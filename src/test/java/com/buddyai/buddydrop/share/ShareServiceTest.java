package com.buddyai.buddydrop.share;

import com.buddyai.buddydrop.domain.AppUser;
import com.buddyai.buddydrop.domain.FileStatus;
import com.buddyai.buddydrop.domain.ShareLink;
import com.buddyai.buddydrop.domain.StoredFile;
import com.buddyai.buddydrop.exception.InvalidPasswordException;
import com.buddyai.buddydrop.exception.ShareUnavailableException;
import com.buddyai.buddydrop.repository.AppUserRepository;
import com.buddyai.buddydrop.repository.ShareLinkRepository;
import com.buddyai.buddydrop.repository.StoredFileRepository;
import com.buddyai.buddydrop.share.dto.ShareResult;
import com.buddyai.buddydrop.share.dto.ShareSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShareServiceTest {

    @Autowired ShareService shareService;
    @Autowired AppUserRepository users;
    @Autowired StoredFileRepository files;
    @Autowired ShareLinkRepository shares;

    @Test
    void shareThenResolvePublicly() {
        Ctx ctx = seedFile();
        ShareResult result = shareService.share(ctx.ownerId, ctx.fileId,
                new ShareSettings(7, null, null, false));

        assertThat(result.url()).contains("/s/");
        String token = tokenFrom(result.url());

        PublicShareView view = shareService.resolvePublic(token);
        assertThat(view.filename()).isEqualTo("report.pdf");
        assertThat(view.requiresPassword()).isFalse();

        assertThat(shareService.resolveDownload(token, null)).contains("download");
    }

    @Test
    void passwordProtectedShareRequiresCorrectPassword() {
        Ctx ctx = seedFile();
        String token = tokenFrom(shareService.share(ctx.ownerId, ctx.fileId,
                new ShareSettings(null, null, "s3cret", false)).url());

        assertThat(shareService.resolvePublic(token).requiresPassword()).isTrue();
        assertThatThrownBy(() -> shareService.resolveDownload(token, "wrong"))
                .isInstanceOf(InvalidPasswordException.class);
        assertThatThrownBy(() -> shareService.resolveDownload(token, null))
                .isInstanceOf(InvalidPasswordException.class);
        assertThat(shareService.resolveDownload(token, "s3cret")).isNotBlank();
    }

    @Test
    void downloadCapIsEnforced() {
        Ctx ctx = seedFile();
        String token = tokenFrom(shareService.share(ctx.ownerId, ctx.fileId,
                new ShareSettings(null, 1, null, false)).url());

        shareService.resolveDownload(token, null); // 1st ok
        assertThatThrownBy(() -> shareService.resolveDownload(token, null))
                .isInstanceOf(ShareUnavailableException.class);
    }

    @Test
    void settingsUpdateKeepsTokenAndReturnsNoUrl() {
        Ctx ctx = seedFile();
        shareService.share(ctx.ownerId, ctx.fileId, new ShareSettings(7, null, null, false));
        String firstHash = shares.findByFileId(ctx.fileId).orElseThrow().getTokenHash();

        ShareResult update = shareService.share(ctx.ownerId, ctx.fileId,
                new ShareSettings(30, 5, null, false));

        assertThat(update.url()).isNull(); // token not re-derivable, so no URL on a settings-only change
        assertThat(shares.findByFileId(ctx.fileId).orElseThrow().getTokenHash()).isEqualTo(firstHash);
        assertThat(update.info().maxDownloads()).isEqualTo(5);
    }

    @Test
    void regenerateRotatesTokenAndResetsCount() {
        Ctx ctx = seedFile();
        String token1 = tokenFrom(shareService.share(ctx.ownerId, ctx.fileId,
                new ShareSettings(null, null, null, false)).url());
        shareService.resolveDownload(token1, null);

        String token2 = tokenFrom(shareService.share(ctx.ownerId, ctx.fileId,
                new ShareSettings(null, null, null, true)).url());

        assertThat(token2).isNotEqualTo(token1);
        assertThat(shares.findByFileId(ctx.fileId).orElseThrow().getDownloadCount()).isZero();
        // Old token no longer resolves.
        assertThatThrownBy(() -> shareService.resolvePublic(token1))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void deletingFileSharesIsCleanedUp() {
        Ctx ctx = seedFile();
        shareService.share(ctx.ownerId, ctx.fileId, new ShareSettings(7, null, null, false));
        assertThat(shares.findByFileId(ctx.fileId)).isPresent();

        shareService.removeSharesForFile(ctx.fileId);
        assertThat(shares.findByFileId(ctx.fileId)).isEmpty();
    }

    private String tokenFrom(String url) {
        return url.substring(url.lastIndexOf("/s/") + 3);
    }

    private Ctx seedFile() {
        AppUser owner = users.save(AppUser.builder().email("owner-" + UUID.randomUUID() + "@ex.com").quotaBytes(1_000_000).build());
        UUID fileId = UUID.randomUUID();
        files.save(StoredFile.builder()
                .id(fileId)
                .ownerId(owner.getId())
                .s3Key("users/" + owner.getId() + "/" + fileId + "/report.pdf")
                .originalName("report.pdf")
                .contentType("application/pdf")
                .sizeBytes(2048)
                .status(FileStatus.READY)
                .build());
        return new Ctx(owner.getId(), fileId);
    }

    private record Ctx(UUID ownerId, UUID fileId) {
    }
}
