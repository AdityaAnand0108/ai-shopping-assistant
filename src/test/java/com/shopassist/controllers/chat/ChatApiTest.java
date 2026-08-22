package com.shopassist.controllers.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopassist.dto.ai.AssistantExchange;
import com.shopassist.dto.auth.LoginRequest;
import com.shopassist.dto.chat.ChatRequest;
import com.shopassist.services.DemoDataInstaller;
import com.shopassist.services.ai.AssistantModel;
import com.shopassist.services.ai.StubAssistantModel;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The chat API, exercised against a stubbed model so the suite stays fast and
 * needs no Ollama running.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatApiTest {

    @TestConfiguration
    static class StubModelConfig {
        @Bean
        @Primary
        AssistantModel stubAssistantModel() {
            return new StubAssistantModel();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DemoDataInstaller installer;

    @Autowired
    private AssistantModel assistantModel;

    private StubAssistantModel stub;

    @BeforeEach
    void setUp() {
        installer.install();
        stub = (StubAssistantModel) assistantModel;
        stub.reset();
    }

    // --- access -------------------------------------------------------------

    @Test
    void chatRequiresAToken() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hello"}"""))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/chat/conversations"))
                .andExpect(status().isUnauthorized());
    }

    // --- a turn -------------------------------------------------------------

    @Test
    void answersAndOpensAConversation() throws Exception {
        stub.willReply("I can help you find products once I am connected to the catalog.");

        mockMvc.perform(chat("aditya", """
                        {"message":"What can you do?"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").isNotEmpty())
                .andExpect(jsonPath("$.reply.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.reply.content")
                        .value("I can help you find products once I am connected to the catalog."))
                .andExpect(jsonPath("$.reply.id").isNotEmpty());
    }

    @Test
    void sendsTheSystemPromptWithEveryTurn() throws Exception {
        mockMvc.perform(chat("aditya", """
                        {"message":"Hello"}""")).andExpect(status().isOk());

        assertThat(stub.lastExchange().systemPrompt())
                .contains("shopping assistant")
                .contains("Every factual claim you make must come from a tool result")
                .contains("Never call confirmOrder in the same reply");
    }

    @Test
    void bothTurnsArePersisted() throws Exception {
        String conversationId = startConversation("aditya", "Hello there");

        mockMvc.perform(get("/api/chat/conversations/" + conversationId)
                        .header("Authorization", bearer("aditya", "Password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[0].content").value("Hello there"))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"));
    }

    @Test
    void titlesAThreadFromItsOpeningQuestion() throws Exception {
        startConversation("aditya", "Do you sell running shoes?");

        mockMvc.perform(get("/api/chat/conversations")
                        .header("Authorization", bearer("aditya", "Password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Do you sell running shoes?"))
                .andExpect(jsonPath("$[0].messageCount").value(2));
    }

    @Test
    void trimsWhitespaceFromTheStoredQuestion() throws Exception {
        String conversationId = startConversation("aditya", "   spaced out   ");

        mockMvc.perform(get("/api/chat/conversations/" + conversationId)
                        .header("Authorization", bearer("aditya", "Password123")))
                .andExpect(jsonPath("$.messages[0].content").value("spaced out"));
    }

    // --- conversation continuity --------------------------------------------

    @Test
    void continuesAThreadAndReplaysHistoryInOrder() throws Exception {
        String token = bearer("aditya", "Password123");
        String conversationId = startConversation("aditya", "First question");

        mockMvc.perform(post("/api/chat").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChatRequest("Second question", conversationId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(conversationId));

        List<AssistantExchange.HistoryTurn> history = stub.lastExchange().history();

        // The second call must see the first exchange, oldest first, and must not
        // include the question being asked right now.
        assertThat(history).hasSize(2);
        assertThat(history.get(0).fromShopper()).isTrue();
        assertThat(history.get(0).content()).isEqualTo("First question");
        assertThat(history.get(1).fromShopper()).isFalse();
        assertThat(stub.lastExchange().userMessage()).isEqualTo("Second question");
        assertThat(history).noneMatch(turn -> turn.content().equals("Second question"));
    }

    @Test
    void theFirstTurnOfAThreadHasNoHistory() throws Exception {
        startConversation("aditya", "Opening line");
        assertThat(stub.lastExchange().history()).isEmpty();
    }

    @Test
    void windowsHistorySoAnOldThreadCannotOverflowTheContext() throws Exception {
        String token = bearer("aditya", "Password123");
        String conversationId = startConversation("aditya", "Turn 1");

        for (int turn = 2; turn <= 10; turn++) {
            mockMvc.perform(post("/api/chat").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ChatRequest("Turn " + turn, conversationId))))
                    .andExpect(status().isOk());
        }

        // 18 turns exist by the final call; the window keeps 12.
        assertThat(stub.lastExchange().history()).hasSize(12);
    }

    // --- ownership ----------------------------------------------------------

    @Test
    void aShopperCannotReadAnotherShoppersConversation() throws Exception {
        String adityasConversation = startConversation("aditya", "Private question");

        mockMvc.perform(get("/api/chat/conversations/" + adityasConversation)
                        .header("Authorization", bearer("rahul", "Password123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aShopperCannotPostIntoAnotherShoppersConversation() throws Exception {
        String adityasConversation = startConversation("aditya", "Private question");

        mockMvc.perform(post("/api/chat")
                        .header("Authorization", bearer("rahul", "Password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChatRequest("Injecting myself", adityasConversation))))
                .andExpect(status().isNotFound());
    }

    @Test
    void conversationListsAreScopedToTheirOwner() throws Exception {
        startConversation("aditya", "Aditya's thread");
        startConversation("priya", "Priya's thread");

        mockMvc.perform(get("/api/chat/conversations")
                        .header("Authorization", bearer("priya", "Password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Priya's thread"));
    }

    // --- input bounds -------------------------------------------------------

    @Test
    void rejectsAnEmptyMessageWithoutCallingTheModel() throws Exception {
        mockMvc.perform(chat("aditya", """
                        {"message":"   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").exists());

        assertThat(stub.callCount()).isZero();
    }

    @Test
    void rejectsAnOverlongMessageWithoutCallingTheModel() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ChatRequest("x".repeat(1001), null));

        mockMvc.perform(post("/api/chat")
                        .header("Authorization", bearer("aditya", "Password123"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").exists());

        assertThat(stub.callCount()).isZero();
    }

    // --- failure handling ---------------------------------------------------

    @Test
    void reportsAModelOutageAsUnavailableRatherThanAServerError() throws Exception {
        stub.willFailAsUnavailable();

        mockMvc.perform(chat("aditya", """
                        {"message":"Are you there?"}"""))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Assistant unavailable"));
    }

    @Test
    void aModelOutageLeaksNothingAboutTheInfrastructure() throws Exception {
        stub.willFailAsUnavailable();

        String body = mockMvc.perform(chat("aditya", """
                        {"message":"Are you there?"}"""))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContainIgnoringCase("ollama")
                .doesNotContainIgnoringCase("localhost")
                .doesNotContain("11434")
                .doesNotContainIgnoringCase("connection refused");
    }

    // --- helpers ------------------------------------------------------------

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder chat(
            String username, String json) throws Exception {
        return post("/api/chat")
                .header("Authorization", bearer(username, "aditya".equals(username)
                        || "priya".equals(username) || "rahul".equals(username)
                        ? "Password123" : "Demo1234"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private String startConversation(String username, String message) throws Exception {
        String password = "demo".equals(username) ? "Demo1234" : "Password123";
        String response = mockMvc.perform(post("/api/chat")
                        .header("Authorization", bearer(username, password))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest(message, null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("conversationId").asText();
    }

    private String bearer(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(
                new com.shopassist.dto.auth.LoginRequest(username, password));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
    }
}
