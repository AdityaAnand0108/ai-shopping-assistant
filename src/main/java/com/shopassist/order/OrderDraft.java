package com.shopassist.order;

import com.shopassist.user.AppUser;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A purchase the assistant has proposed but nobody has agreed to yet.
 *
 * <p>This entity is the reason the model cannot spend a shopper's money. The
 * only tool that creates one produces a priced proposal and stops; a separate
 * tool, which the prompt is told to call only after the shopper says yes, turns
 * it into an order. Splitting the action in two means an over-eager or
 * manipulated model has no single call that completes a purchase.
 */
@Entity
@Table(name = "order_drafts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_ref", nullable = false, length = 36, updatable = false)
    private String publicRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DraftStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_order_id")
    private Order confirmedOrder;

    @Builder.Default
    @OneToMany(mappedBy = "draft", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDraftItem> items = new ArrayList<>();

    @PrePersist
    void assignDefaults() {
        if (publicRef == null) {
            publicRef = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = DraftStatus.PENDING;
        }
    }

    public void addItem(OrderDraftItem item) {
        items.add(item);
        item.setDraft(this);
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    /** Only a pending, unexpired draft may be confirmed. */
    public boolean isConfirmable() {
        return status == DraftStatus.PENDING && !isExpired();
    }
}
