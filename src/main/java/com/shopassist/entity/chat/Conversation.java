package com.shopassist.entity.chat;

import com.shopassist.entity.user.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One chat thread, owned by a shopper.
 *
 * <p>Conversations are owner-scoped exactly as orders are: the repository has no
 * lookup that omits the owner, so one shopper cannot read another's chat history
 * even with a valid conversation reference.
 */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    /** How much of a first message is kept as the thread's display title. */
    private static final int TITLE_MAX_LENGTH = 80;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_ref", nullable = false, length = 36, updatable = false)
    private String publicRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(length = 200)
    private String title;

    @Builder.Default
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ChatMessage> messages = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void assignPublicRef() {
        if (publicRef == null) {
            publicRef = UUID.randomUUID().toString();
        }
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        message.setConversation(this);
    }

    /**
     * Names a new thread after its opening question, so the history list is
     * scannable. Titles come from the shopper's own words rather than from a
     * summarising model call: a second inference per conversation would cost
     * latency, and a hallucinated title is a bad trade for a cosmetic gain.
     */
    public void titleFrom(String firstMessage) {
        if (title != null || firstMessage == null || firstMessage.isBlank()) {
            return;
        }
        String flattened = firstMessage.strip().replaceAll("\\s+", " ");
        title = flattened.length() <= TITLE_MAX_LENGTH
                ? flattened
                : flattened.substring(0, TITLE_MAX_LENGTH - 1) + "…";
    }
}
