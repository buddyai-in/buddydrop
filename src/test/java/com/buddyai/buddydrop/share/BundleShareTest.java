package com.buddyai.buddydrop.share;

import com.buddyai.buddydrop.domain.AppUser;
import com.buddyai.buddydrop.domain.FileStatus;
import com.buddyai.buddydrop.domain.StoredFile;
import com.buddyai.buddydrop.exception.InvalidPasswordException;
import com.buddyai.buddydrop.exception.ShareUnavailableException;
import com.buddyai.buddydrop.repository.AppUserRepository;
import com.buddyai.buddydrop.repository.StoredFileRepository;
import com.buddyai.buddydrop.share.dto.ShareSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BundleShareTest {

    @Autowired ShareBundleService bundleService;
    @Autowired AppUserRepository users;
    @Autowired StoredFileRepository files;
    @Autowired MockMvc mvc;

    @Test
    void createAndResolveBundle() {
        Ctx c = seed(3);
        String url = bundleService.create(c.ownerId, c.ids, new ShareSettings(7, null, null, false));
        String token = token(url);

        BundleView view = bundleService.resolvePublic(token);
        assertThat(view.count()).isEqualTo(3);
        assertThat(view.requiresPassword()).isFalse();
        assertThat(view.files()).extracting(BundleView.Item::name).contains("file-0.txt", "file-2.txt");
    }

    @Test
    void passwordProtectedBundle() {
        Ctx c = seed(2);
        String token = token(bundleService.create(c.ownerId, c.ids, new ShareSettings(null, null, "pw", false)));

        assertThat(bundleService.resolvePublic(token).requiresPassword()).isTrue();
        assertThatThrownBy(() -> bundleService.verifyPassword(token, "wrong")).isInstanceOf(InvalidPasswordException.class);
        assertThatCode(() -> bundleService.verifyPassword(token, "pw")).doesNotThrowAnyException();
    }

    @Test
    void downloadCapExhaustsBundle() {
        Ctx c = seed(2);
        String token = token(bundleService.create(c.ownerId, c.ids, new ShareSettings(null, 1, null, false)));

        assertThat(bundleService.prepareDownload(token)).hasSize(2); // 1st ok
        assertThatThrownBy(() -> bundleService.resolvePublic(token)).isInstanceOf(ShareUnavailableException.class);
    }

    @Test
    void publicZipEndpointStreamsArchive() throws Exception {
        Ctx c = seed(2);
        String token = token(bundleService.create(c.ownerId, c.ids, new ShareSettings(7, null, null, false)));

        mvc.perform(get("/d/{t}/zip", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".zip")));
    }

    private String token(String url) {
        return url.substring(url.lastIndexOf("/d/") + 3);
    }

    private Ctx seed(int n) {
        AppUser owner = users.save(AppUser.builder().email("o-" + UUID.randomUUID() + "@ex.com").quotaBytes(1_000_000).build());
        List<UUID> ids = java.util.stream.IntStream.range(0, n).mapToObj(i -> {
            UUID id = UUID.randomUUID();
            files.save(StoredFile.builder().id(id).ownerId(owner.getId())
                    .s3Key("users/" + owner.getId() + "/" + id + "/file-" + i + ".txt")
                    .originalName("file-" + i + ".txt").contentType("text/plain")
                    .sizeBytes(1024).status(FileStatus.READY).build());
            return id;
        }).toList();
        return new Ctx(owner.getId(), ids);
    }

    private record Ctx(UUID ownerId, List<UUID> ids) {
    }
}
