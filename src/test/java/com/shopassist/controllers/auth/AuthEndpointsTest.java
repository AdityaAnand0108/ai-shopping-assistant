package com.shopassist.controllers.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopassist.dto.auth.SignupRequest;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the auth endpoints over real HTTP, through the real
 * filter chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// MockMvc runs on the test thread, so a test-managed transaction rolls back
// everything these requests write. Without it each class would commit into the
// shared in-memory database and leak fixtures into the others.
@Transactional
class AuthEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DemoDataInstaller installer;

    @Autowired
    private AppUserRepository userRepository;

    @BeforeEach
    void seed() {
        if (userRepository.count() == 0) {
            installer.install();
        }
    }

    // --- signup -------------------------------------------------------------

    @Test
    void signupCreatesAnAccountAndReturnsAUsableToken() throws Exception {
        String body = """
                {"username":"newshopper","email":"new@example.com",
                 "password":"Sup3rSecret","fullName":"New Shopper"}""";

        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("newshopper"))
                .andReturn();

        String token = json(result).get("accessToken").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newshopper"));
    }

    @Test
    void signupNeverEchoesThePasswordOrItsHash() throws Exception {
        String body = """
                {"username":"quiet","email":"quiet@example.com","password":"Sup3rSecret"}""";

        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String payload = result.getResponse().getContentAsString();
        assertThat(payload).doesNotContain("Sup3rSecret").doesNotContain("$2a$").doesNotContain("$2b$");
        assertThat(json(result).get("user").has("passwordHash")).isFalse();
    }

    @Test
    void signupExposesAPublicIdRatherThanTheDatabaseKey() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"opaque","email":"opaque@example.com","password":"Sup3rSecret"}"""))
                .andExpect(status().isCreated())
                .andReturn();

        AppUser stored = userRepository.findByUsername("opaque").orElseThrow();
        String exposedId = json(result).get("user").get("id").asText();

        assertThat(exposedId).isEqualTo(stored.getPublicRef()).hasSize(36);
        assertThat(exposedId).isNotEqualTo(String.valueOf(stored.getId()));
    }

    @Test
    void signupStoresTheEmailLowercasedSoCasingCannotCreateADuplicate() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"casing","email":"Mixed.Case@Example.COM","password":"Sup3rSecret"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("mixed.case@example.com"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"casing2","email":"mixed.case@example.com","password":"Sup3rSecret"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void signupRejectsADuplicateUsername() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"satvik","email":"different@example.com","password":"Sup3rSecret"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Account already exists"));
    }

    @Test
    void signupReportsEveryInvalidFieldAtOnce() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"a b","email":"not-an-email","password":"short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void signupRejectsAPasswordWithNoDigit() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"letters","email":"letters@example.com","password":"onlyletters"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void signupRejectsAPasswordBeyondTheBcryptLimit() throws Exception {
        String tooLong = "A1" + "x".repeat(80);
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("longpass", "long@example.com", tooLong, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // --- login --------------------------------------------------------------

    @Test
    void loginSucceedsForASeededAccount() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"satvik","password":"Password123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("satvik"));
    }

    @Test
    void loginGivesAnIdenticalResponseForAnUnknownUserAndAWrongPassword() throws Exception {
        String unknownUser = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nobody-here","password":"Password123"}"""))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"sarah","password":"NotThePassword1"}"""))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Strip the timestamp, which legitimately differs between the two calls.
        assertThat(withoutTimestamp(unknownUser)).isEqualTo(withoutTimestamp(wrongPassword));
    }

    @Test
    void loginErrorDoesNotNameTheUserOrRevealWhichHalfWasWrong() throws Exception {
        String payload = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"sarah","password":"NotThePassword1"}"""))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(payload)
                .doesNotContain("sarah")
                .doesNotContainIgnoringCase("no such user")
                .doesNotContainIgnoringCase("incorrect password");
        assertThat(payload).contains("Invalid username or password");
    }

    // --- lockout ------------------------------------------------------------

    @Test
    void fiveFailedAttemptsLockTheAccountAndACorrectPasswordThenStillFails() throws Exception {
        String wrong = """
                {"username":"rahul","password":"WrongPassword1"}""";

        for (int attempt = 1; attempt <= 4; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }

        // The fifth failure is what trips the lock.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.findByUsername("rahul").orElseThrow().isCurrentlyLocked()).isTrue();

        // Even the right password is refused while the lock stands.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"rahul","password":"Password123"}"""))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.lockedUntil").isNotEmpty());
    }

    @Test
    void aSuccessfulLoginClearsAPartialRunOfFailures() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"demo","password":"WrongPassword1"}"""))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(userRepository.findByUsername("demo").orElseThrow().getFailedLoginAttempts())
                .isEqualTo(3);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"demo","password":"Demo1234"}"""))
                .andExpect(status().isOk());

        assertThat(userRepository.findByUsername("demo").orElseThrow().getFailedLoginAttempts())
                .isZero();
    }

    // --- helpers ------------------------------------------------------------

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String withoutTimestamp(String payload) throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(payload);
        node.remove("timestamp");
        return node.toString();
    }
}
