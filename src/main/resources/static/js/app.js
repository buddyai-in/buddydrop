/*
 * BuddyDrop dashboard behaviour. Vanilla JS, no framework.
 *
 * Upload path is direct-to-S3: presign (our API) -> PUT bytes to S3 with progress -> confirm (our API).
 * All calls to our own API carry the CSRF token from the page meta; the S3 PUT is cross-origin and
 * carries only the headers the presign told us to send.
 */
(function () {
  "use strict";

  const csrfToken = document.querySelector('meta[name="_csrf"]').content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

  const dropzone = document.getElementById("dropzone");
  const fileInput = document.getElementById("file-input");
  const pickBtn = document.getElementById("pick-btn");
  const uploadsEl = document.getElementById("uploads");
  const fileList = document.getElementById("file-list");
  const emptyState = document.getElementById("empty-state");
  const toastEl = document.getElementById("toast");

  // ---- helpers ----------------------------------------------------------

  function api(method, url, body) {
    const headers = { [csrfHeader]: csrfToken };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    return fetch(url, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      credentials: "same-origin",
    }).then(async (res) => {
      if (!res.ok) {
        let msg = "Request failed";
        try { msg = (await res.json()).message || msg; } catch (e) { /* non-JSON */ }
        throw new Error(msg);
      }
      return res.status === 204 ? null : res.json();
    });
  }

  function toast(message) {
    toastEl.textContent = message;
    toastEl.classList.add("show");
    clearTimeout(toast._t);
    toast._t = setTimeout(() => toastEl.classList.remove("show"), 2600);
  }

  function extOf(name) {
    const i = name.lastIndexOf(".");
    if (i < 0 || i === name.length - 1) return "FILE";
    return name.slice(i + 1).toUpperCase().slice(0, 4);
  }

  function svgIcon(paths) {
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">' + paths + "</svg>";
  }

  // ---- upload -----------------------------------------------------------

  function uploadFile(file) {
    const item = document.createElement("li");
    item.className = "upload-item";
    item.innerHTML =
      '<div class="u-top"><span class="u-name"></span><span class="u-pct">0%</span></div>' +
      '<div class="u-bar"><span></span></div>';
    item.querySelector(".u-name").textContent = file.name;
    uploadsEl.prepend(item);
    const pctEl = item.querySelector(".u-pct");
    const barEl = item.querySelector(".u-bar > span");

    const fail = (msg) => {
      item.classList.add("error");
      pctEl.textContent = "Failed";
      toast(file.name + ": " + msg);
      setTimeout(() => item.remove(), 4000);
    };

    api("POST", "/api/files/presign-upload", {
      filename: file.name,
      contentType: file.type || "application/octet-stream",
      size: file.size,
    })
      .then(({ fileId, upload }) => putToStorage(upload, file, pctEl, barEl)
        .then(() => api("POST", "/api/files/" + fileId + "/confirm"))
        .then((confirmed) => {
          item.remove();
          addFileRow(confirmed);
          toast(file.name + " uploaded");
        }))
      .catch((e) => fail(e.message));
  }

  function putToStorage(upload, file, pctEl, barEl) {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open(upload.method || "PUT", upload.url);
      Object.entries(upload.headers || {}).forEach(([k, v]) => xhr.setRequestHeader(k, v));
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
          const pct = Math.round((e.loaded / e.total) * 100);
          pctEl.textContent = pct + "%";
          barEl.style.width = pct + "%";
        }
      };
      xhr.onload = () => (xhr.status >= 200 && xhr.status < 300)
        ? resolve()
        : reject(new Error("Storage rejected the upload (" + xhr.status + ")"));
      xhr.onerror = () => reject(new Error("Network error during upload"));
      xhr.send(file);
    });
  }

  function addFileRow(file) {
    if (emptyState) emptyState.classList.add("hidden");
    const kind = extOf(file.name).toLowerCase();
    const row = document.createElement("li");
    row.className = "file-row";
    row.id = "row-" + file.id;
    row.dataset.fileId = file.id;
    row.dataset.fileName = file.name;
    row.innerHTML =
      '<span class="file-icon" data-kind="' + kind + '">' + extOf(file.name) + "</span>" +
      '<div class="file-meta"><div class="file-name"></div>' +
      '<div class="file-sub"><span class="fsize"></span> · just now</div></div>' +
      '<span class="badge badge-private">private</span>' +
      '<div class="row-actions">' +
      '<button class="btn-icon js-download" type="button" title="Download" aria-label="Download">' +
      svgIcon('<path d="M12 3v12m0 0 4-4m-4 4-4-4M4 17v2a2 2 0 002 2h12a2 2 0 002-2v-2"/>') + "</button>" +
      '<button class="btn-icon js-share" type="button" title="Share" aria-label="Share">' +
      svgIcon('<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m8.6 13.5 6.8 4M15.4 6.5l-6.8 4"/>') + "</button>" +
      '<button class="btn-icon js-delete" type="button" title="Delete" aria-label="Delete">' +
      svgIcon('<path d="M3 6h18M8 6V4a1 1 0 011-1h6a1 1 0 011 1v2m2 0v14a1 1 0 01-1 1H7a1 1 0 01-1-1V6"/>') + "</button>" +
      "</div>";
    row.querySelector(".file-name").textContent = file.name;
    row.querySelector(".fsize").textContent = file.sizeHuman;
    fileList.prepend(row);
  }

  function handleFiles(files) {
    Array.from(files).forEach(uploadFile);
  }

  // ---- dropzone wiring --------------------------------------------------

  pickBtn.addEventListener("click", () => fileInput.click());
  dropzone.addEventListener("click", () => fileInput.click());
  dropzone.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") { e.preventDefault(); fileInput.click(); }
  });
  fileInput.addEventListener("change", () => { handleFiles(fileInput.files); fileInput.value = ""; });

  ["dragenter", "dragover"].forEach((ev) =>
    dropzone.addEventListener(ev, (e) => { e.preventDefault(); dropzone.classList.add("dragover"); }));
  ["dragleave", "drop"].forEach((ev) =>
    dropzone.addEventListener(ev, (e) => { e.preventDefault(); dropzone.classList.remove("dragover"); }));
  dropzone.addEventListener("drop", (e) => {
    if (e.dataTransfer && e.dataTransfer.files.length) handleFiles(e.dataTransfer.files);
  });

  // ---- row actions (event delegation) -----------------------------------

  document.addEventListener("click", (e) => {
    const row = e.target.closest(".file-row");
    if (!row) return;
    const id = row.dataset.fileId;
    if (e.target.closest(".js-download")) {
      window.location.href = "/api/files/" + id + "/download";
    } else if (e.target.closest(".js-delete")) {
      deleteFile(row, id);
    } else if (e.target.closest(".js-share")) {
      openShare(row, id);
    }
  });

  function deleteFile(row, id) {
    if (!confirm('Delete "' + row.dataset.fileName + '"? This cannot be undone.')) return;
    api("DELETE", "/api/files/" + id)
      .then(() => {
        row.remove();
        if (!fileList.children.length && emptyState) emptyState.classList.remove("hidden");
        toast("File deleted");
      })
      .catch((e) => toast(e.message));
  }

  // ---- share modal ------------------------------------------------------

  const modal = document.getElementById("share-modal");
  const shareTitle = document.getElementById("share-title");
  const shareUrl = document.getElementById("share-url");
  const optExpiry = document.getElementById("opt-expiry");
  const optCap = document.getElementById("opt-cap");
  const optPass = document.getElementById("opt-pass");
  const saveBtn = document.getElementById("share-save");
  const revokeBtn = document.getElementById("revoke-btn");
  const copyBtn = document.getElementById("copy-btn");
  let activeShare = null; // { row, id, exists }

  function openShare(row, id) {
    activeShare = { row, id, exists: false };
    shareTitle.textContent = 'Share "' + row.dataset.fileName + '"';
    shareUrl.value = "";
    shareUrl.placeholder = "Generate a link below";
    optCap.value = "";
    optPass.value = "";
    revokeBtn.classList.add("hidden");
    saveBtn.textContent = "Create link";
    api("GET", "/api/files/" + id + "/share")
      .then((info) => {
        if (info.exists) {
          activeShare.exists = true;
          revokeBtn.classList.remove("hidden");
          saveBtn.textContent = "Update link";
          shareUrl.placeholder = "Link active — regenerate to reveal a new URL";
          if (info.maxDownloads != null) optCap.value = info.maxDownloads;
        }
      })
      .catch(() => { /* first-time share; defaults are fine */ });
    modal.classList.add("open");
  }

  function closeShare() {
    modal.classList.remove("open");
    activeShare = null;
  }

  document.getElementById("share-close").addEventListener("click", closeShare);
  modal.addEventListener("click", (e) => { if (e.target === modal) closeShare(); });
  document.addEventListener("keydown", (e) => { if (e.key === "Escape" && modal.classList.contains("open")) closeShare(); });

  saveBtn.addEventListener("click", () => {
    if (!activeShare) return;
    const settings = {
      expiresInDays: optExpiry.value ? parseInt(optExpiry.value, 10) : null,
      maxDownloads: optCap.value ? parseInt(optCap.value, 10) : null,
      password: optPass.value ? optPass.value : "",
      // Existing link + no new URL yet -> regenerate so the owner gets a copyable URL.
      regenerate: activeShare.exists,
    };
    saveBtn.disabled = true;
    api("POST", "/api/files/" + activeShare.id + "/share", settings)
      .then((result) => {
        if (result.url) { shareUrl.value = result.url; shareUrl.select(); }
        markShared(activeShare.row, result.info);
        activeShare.exists = true;
        revokeBtn.classList.remove("hidden");
        saveBtn.textContent = "Update link";
        toast("Share link ready");
      })
      .catch((e) => toast(e.message))
      .finally(() => { saveBtn.disabled = false; });
  });

  revokeBtn.addEventListener("click", () => {
    if (!activeShare) return;
    api("DELETE", "/api/files/" + activeShare.id + "/share")
      .then(() => {
        markPrivate(activeShare.row);
        toast("Share link revoked");
        closeShare();
      })
      .catch((e) => toast(e.message));
  });

  copyBtn.addEventListener("click", () => {
    if (!shareUrl.value) return;
    navigator.clipboard.writeText(shareUrl.value)
      .then(() => toast("Link copied"))
      .catch(() => { shareUrl.select(); document.execCommand("copy"); toast("Link copied"); });
  });

  function markShared(row, info) {
    const badge = row.querySelector(".badge");
    badge.classList.remove("badge-private");
    badge.classList.add("badge-shared");
    badge.textContent = info && info.expiresAt ? "shared" : "shared · no expiry";
  }

  function markPrivate(row) {
    const badge = row.querySelector(".badge");
    badge.classList.remove("badge-shared");
    badge.classList.add("badge-private");
    badge.textContent = "private";
  }
})();
