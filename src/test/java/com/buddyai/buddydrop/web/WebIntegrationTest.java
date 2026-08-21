package com.buddyai.buddydrop.web;

import com.buddyai.buddydrop.domain.AppUser;
import com.buddyai.buddydrop.repository.AppUserRepository;
import com.buddyai.buddydrop.repository.MagicTokenRepository;
import com.buddyai.buddydrop.security.AppUserPrincipal;
import com.buddyai.buddydrop.support.FakeStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WebIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired MagicTokenRepository tokens;
    @Autowired FakeStorageService storage;
    @Autowired ObjectMapper json;

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mvc.perform(get("/files"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void requestingMagicLinkShowsSentPageAndIssuesToken() throws Exception {
        mvc.perform(post("/auth/request").param("email", "user@example.com").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/sent"));

        assertThat(users.findByEmail("user@example.com")).isPresent();
        assertThat(tokens.findAll()).hasSize(1);
    }

    @Test
    void fullUploadShareDownloadFlow() throws Exception {
        AppUser user = users.save(AppUser.builder()
                .email("flow@example.com").quotaBytes(10_000_000).build());
        Authentication auth = authOf(user);
        storage.setSimulatedSize(2_048);

        // 1. presign an upload
        MvcResult presign = mvc.perform(post("/api/files/presign-upload")
                        .with(authentication(auth)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"filename":"report.pdf","contentType":"application/pdf","size":2048}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").exists())
                .andExpect(jsonPath("$.upload.url").exists())
                .andReturn();
        String fileId = node(presign).get("fileId").asText();

        // 2. confirm (browser has "PUT" to fake storage; objectSize returns simulated size)
        mvc.perform(post("/api/files/{id}/confirm", fileId).with(authentication(auth)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2048))
                .andExpect(jsonPath("$.name").value("report.pdf"));

        // 3. the file now appears on the dashboard
        mvc.perform(get("/files").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("files"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("report.pdf")));

        // 4. download redirects to a presigned URL
        mvc.perform(get("/api/files/{id}/download", fileId).with(authentication(auth)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("https://fake-storage.test/**"));

        // 5. create a public share link
        MvcResult share = mvc.perform(post("/api/files/{id}/share", fileId)
                        .with(authentication(auth)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"expiresInDays":7,"maxDownloads":null,"password":"","regenerate":false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists())
                .andReturn();
        String url = node(share).get("url").asText();
        String token = url.substring(url.lastIndexOf("/s/") + 3);

        // 6. anonymous landing page renders (no auth)
        mvc.perform(get("/s/{token}", token))
                .andExpect(status().isOk())
                .andExpect(view().name("share/download"));

        // 7. anonymous download redirects to storage
        mvc.perform(post("/s/{token}", token).with(csrf()))
                .andExpect(status().is3xxRedirection());

        // 8. delete
        mvc.perform(delete("/api/files/{id}", fileId).with(authentication(auth)).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void unknownShareTokenRendersUnavailable() throws Exception {
        mvc.perform(get("/s/{token}", "does-not-exist"))
                .andExpect(status().isOk())
                .andExpect(view().name("share/unavailable"));
    }

    private Authentication authOf(AppUser user) {
        return new UsernamePasswordAuthenticationToken(
                new AppUserPrincipal(user.getId(), user.getEmail()),
                null, AuthorityUtils.createAuthorityList("ROLE_USER"));
    }

    private JsonNode node(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
