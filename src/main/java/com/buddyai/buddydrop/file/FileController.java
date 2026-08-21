package com.buddyai.buddydrop.file;

import com.buddyai.buddydrop.file.dto.FileResponse;
import com.buddyai.buddydrop.file.dto.PresignUploadRequest;
import com.buddyai.buddydrop.file.dto.PresignUploadResponse;
import com.buddyai.buddydrop.security.AppUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * JSON API behind the dashboard. Drives the direct-to-S3 upload handshake (presign &rarr; confirm),
 * download redirects, and deletes. Every method is scoped to the authenticated principal, so
 * ownership is enforced server-side regardless of the id in the path.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/presign-upload")
    public PresignUploadResponse presignUpload(@AuthenticationPrincipal AppUserPrincipal user,
                                               @Valid @RequestBody PresignUploadRequest request) {
        return fileService.initiateUpload(user.id(), request);
    }

    @PostMapping("/{id}/confirm")
    public FileResponse confirm(@AuthenticationPrincipal AppUserPrincipal user,
                                @PathVariable UUID id) {
        return FileResponse.from(fileService.confirmUpload(user.id(), id));
    }

    /** Redirects to a short-lived presigned GET so the browser streams the file straight from S3. */
    @GetMapping("/{id}/download")
    public ResponseEntity<Void> download(@AuthenticationPrincipal AppUserPrincipal user,
                                         @PathVariable UUID id) {
        String url = fileService.presignDownload(user.id(), id);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserPrincipal user,
                       @PathVariable UUID id) {
        fileService.delete(user.id(), id);
    }
}
