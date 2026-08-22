package com.shopassist.chat;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

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

    public static ChatMessage fromAssistant(String content, String model, long latencyMs) {
        return ChatMessage.builder()
                .role(MessageRole.ASSISTANT)
                .content(content)
                .model(model)
                .latencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE))
                .build();
    }
}
