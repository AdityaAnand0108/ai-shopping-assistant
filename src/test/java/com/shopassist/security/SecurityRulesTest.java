package com.shopassist.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopassist.config.security.JwtProperties;
import com.shopassist.dto.auth.LoginRequest;
import com.shopassist.entity.user.AppUser;
import com.shopassist.repository.user.AppUserRepository;
import com.shopassist.services.DemoDataInstaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the filter chain itself: which routes are open, what a token has to
 * look like to be accepted, and what happens when one is revoked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// MockMvc runs on the test thread, so a test-managed transaction rolls back
// everything these requests write. Without it each class would commit into the
// shared in-memory database and leak fixtures into the others.
@Transactional
class SecurityRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DemoDataInstaller installer;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void seed() {
        if (userRepository.count() == 0) {
            installer.install();
        }
    }

    // --- open routes --------------------------------------------------------

    @Test
    void healthAndInfoStayOpen() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/info")).andExpect(status().isOk());
    }

    @Test
    void apiDocsStayOpen() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    // --- protected routes ---------------------------------------------------

    @Test
    void profileRequiresAToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    @Test
    void aGarbageTokenIsRefused() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenSignedWithADifferentKeyIsRefused() throws Exception {
        // Same claims, different secret: this is the forgery case, and it must
        // fail on the signature rather than on anything in the payload.
        JwtProperties attackerProperties = new JwtProperties(
                "an-entirely-different-signing-key-of-sufficient-length", null, "shop-assistant");
        JwtService attackerService = new JwtService(attackerProperties);
        AppUser aditya = userRepository.findByUsername("aditya").orElseThrow();

        String forged = attackerService.issue(aditya).token();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenFromAnotherIssuerIsRefused() throws Exception {
        JwtProperties otherIssuer = new JwtProperties(
                JwtProperties.DEVELOPMENT_SECRET, null, "some-other-service");
        AppUser aditya = userRepository.findByUsername("aditya").orElseThrow();

        String token = new JwtService(otherIssuer).issue(aditya).token();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aMalformedAuthorizationHeaderIsIgnoredRatherThanCrashing() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredTokenIsRefused() throws Exception {
        JwtProperties instantlyExpiring = new JwtProperties(
                JwtProperties.DEVELOPMENT_SECRET, java.time.Duration.ofSeconds(-10), "shop-assistant");
        AppUser aditya = userRepository.findByUsername("aditya").orElseThrow();

        String expired = new JwtService(instantlyExpiring).issue(aditya).token();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSubjectCarriesThePublicRefNotTheDatabaseKey() {
        AppUser aditya = userRepository.findByUsername("aditya").orElseThrow();

        JwtService.VerifiedToken verified =
                jwtService.verify(jwtService.issue(aditya).token()).orElseThrow();

        assertThat(verified.publicRef()).isEqualTo(aditya.getPublicRef());
        assertThat(verified.publicRef()).isNotEqualTo(String.valueOf(aditya.getId()));
    }

    // --- disabled accounts and revocation -----------------------------------

    @Test
    void disablingAnAccountInvalidatesTokensAlreadyIssuedToIt() throws Exception {
        String token = tokenFor("priya", "Password123");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        AppUser priya = userRepository.findByUsername("priya").orElseThrow();
        priya.setEnabled(false);
        userRepository.save(priya);

        // The token is still cryptographically valid; the account behind it is not.
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        priya.setEnabled(true);
        userRepository.save(priya);
    }

    @Test
    void logoutRevokesTheTokenItWasCalledWith() throws Exception {
        String token = tokenFor("aditya", "Password123");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokingOneTokenLeavesTheSameUsersOtherSessionsWorking() throws Exception {
        String laptop = tokenFor("aditya", "Password123");
        String phone = tokenFor("aditya", "Password123");
        assertThat(laptop).isNotEqualTo(phone);

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + laptop))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + laptop))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + phone))
                .andExpect(status().isOk());
    }

    @Test
    void eachUsersTokenResolvesToTheirOwnIdentityOnly() throws Exception {
        String adityasToken = tokenFor("aditya", "Password123");
        String priyasToken = tokenFor("priya", "Password123");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + adityasToken))
                .andExpect(jsonPath("$.username").value("aditya"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + priyasToken))
                .andExpect(jsonPath("$.username").value("priya"));
    }

    @Test
    void profileNeverLeaksTheHashOrInternalCounters() throws Exception {
        String token = tokenFor("aditya", "Password123");

        String payload = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(payload)
                .doesNotContain("passwordHash")
                .doesNotContain("$2a$")
                .doesNotContain("failedLoginAttempts")
                .doesNotContain("lockedUntil");
    }

    private String tokenFor(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(
                new com.shopassist.dto.auth.LoginRequest(username, password));

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
