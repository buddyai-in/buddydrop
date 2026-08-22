package com.buddyai.buddydrop.web;

import com.buddyai.buddydrop.domain.AppUser;
import com.buddyai.buddydrop.domain.FileStatus;
import com.buddyai.buddydrop.domain.StoredFile;
import com.buddyai.buddydrop.repository.AppUserRepository;
import com.buddyai.buddydrop.repository.ShareLinkRepository;
import com.buddyai.buddydrop.repository.StoredFileRepository;
import com.buddyai.buddydrop.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardFeaturesTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired StoredFileRepository files;
    @Autowired ShareLinkRepository shares;

    @Test
    void dashboardIsPaginated() throws Exception {
        AppUser user = newUser();
        seedFiles(user, 15); // PAGE_SIZE is 12

        mvc.perform(get("/files").with(authentication(authOf(user))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("files", hasSize(12)))
                .andExpect(model().attribute("totalPages", 2))
                .andExpect(model().attribute("hasNext", true))
                .andExpect(model().attribute("hasPrev", false));

        mvc.perform(get("/files").param("page", "1").with(authentication(authOf(user))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("files", hasSize(3)))
                .andExpect(model().attribute("hasPrev", true))
                .andExpect(model().attribute("hasNext", false));
    }

    @Test
    void bulkDeleteRemovesOnlyOwnedSelection() throws Exception {
        AppUser user = newUser();
        List<StoredFile> seeded = seedFiles(user, 3);
        List<UUID> toDelete = List.of(seeded.get(0).getId(), seeded.get(1).getId());

        mvc.perform(post("/api/files/bulk-delete")
                        .with(authentication(authOf(user))).with(csrf())
                        .contentType("application/json")
                        .content("{\"ids\":[\"" + toDelete.get(0) + "\",\"" + toDelete.get(1) + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(2));

        assertThat(files.findByOwnerIdAndStatusOrderByCreatedAtDesc(user.getId(), FileStatus.READY)).hasSize(1);
    }

    @Test
    void bulkShareReturnsALinkPerFile() throws Exception {
        AppUser user = newUser();
        List<StoredFile> seeded = seedFiles(user, 2);

        mvc.perform(post("/api/files/bulk-share")
                        .with(authentication(authOf(user))).with(csrf())
                        .contentType("application/json")
                        .content("{\"ids\":[\"" + seeded.get(0).getId() + "\",\"" + seeded.get(1).getId()
                                + "\"],\"expiresInDays\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].url").exists())
                .andExpect(jsonPath("$[1].url").exists());

        assertThat(shares.findByFileId(seeded.get(0).getId())).isPresent();
        assertThat(shares.findByFileId(seeded.get(1).getId())).isPresent();
    }

    @Test
    void profilePageRenders() throws Exception {
        AppUser user = newUser();
        mvc.perform(get("/profile").with(authentication(authOf(user))))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"))
                .andExpect(model().attributeExists("usage"))
                .andExpect(model().attribute("plan", "Free"));
    }

    // ---- helpers ----

    private AppUser newUser() {
        return users.save(AppUser.builder()
                .email("u-" + UUID.randomUUID() + "@ex.com").quotaBytes(10_000_000).build());
    }

    private List<StoredFile> seedFiles(AppUser owner, int n) {
        return java.util.stream.IntStream.range(0, n).mapToObj(i -> {
            UUID id = UUID.randomUUID();
            return files.save(StoredFile.builder()
                    .id(id)
                    .ownerId(owner.getId())
                    .s3Key("users/" + owner.getId() + "/" + id + "/file-" + i + ".txt")
                    .originalName("file-" + i + ".txt")
                    .contentType("text/plain")
                    .sizeBytes(1024)
                    .status(FileStatus.READY)
                    .build());
        }).toList();
    }

    private Authentication authOf(AppUser user) {
        return new UsernamePasswordAuthenticationToken(
                new AppUserPrincipal(user.getId(), user.getEmail()),
                null, AuthorityUtils.createAuthorityList("ROLE_USER"));
    }
}
