package com.shopassist.entity.chat;

import com.shopassist.enums.chat.MessageRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One turn in a conversation.
 *
 * <p>{@link #publicRef} is the handle the frontend uses to attach feedback to a
 * specific answer in Phase 8, and the key the tool-call audit trail will point
 * at. {@link #model} and {@link #latencyMs} are recorded on assistant turns so
 * that "which model said this, and how long did it take" is answerable after the
 * fact rather than only while the logs are still around.
 */
@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_ref", nullable = false, length = 36, updatable = false)
    private String publicRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Which model produced this turn. Null on shopper messages. */
    @Column(length = 80)
    private String model;

    /** Time spent generating this turn, in milliseconds. Null on shopper messages. */
    @Column(name = "latency_ms")
    private Integer latencyMs;

    /**
     * Identifiers this turn's tools returned, comma separated.
     *
     * <p>Replayed alongside the message so the next turn can act on a SKU or an
     * order number the model was shown but did not repeat in its prose. Without
     * this the model has to recall an identifier from memory, and it gets that
     * wrong often enough to break the purchase flow entirely.
     */
    @Column(name = "tool_facts", length = 500)
    private String toolFacts;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void assignPublicRef() {
        if (publicRef == null) {
            publicRef = UUID.randomUUID().toString();
        }
    }

    public static ChatMessage fromShopper(String content) {
        return ChatMessage.builder()
                .role(MessageRole.USER)
                .content(content)
                .build();
    }

    public static ChatMessage fromAssistant(String content, String model, long latencyMs,
                                            String toolFacts) {
        return ChatMessage.builder()
                .role(MessageRole.ASSISTANT)
                .content(content)
                .model(model)
                .latencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE))
                .toolFacts(toolFacts)
                .build();
    }
}
