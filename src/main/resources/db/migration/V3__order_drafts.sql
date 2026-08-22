-- Phase 5: proposed purchases, awaiting the shopper's confirmation.
--
-- A purchase is split into two steps. The assistant may build a draft, but only
-- an explicit confirmation turns one into an order. Drafts live in their own
-- table rather than as orders in a DRAFT status, so an abandoned proposal never
-- appears in order history and can never be mistaken for a purchase.
--
-- expires_at exists because a draft freezes prices and availability at the
-- moment it was built. Confirming a stale draft would charge yesterday's price
-- or promise stock that has since sold out, so drafts are short-lived and
-- re-validated on confirmation regardless.

CREATE TABLE order_drafts (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_ref         VARCHAR(36)  NOT NULL,
    user_id            BIGINT       NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    total_amount       DECIMAL(12, 2) NOT NULL,
    currency           VARCHAR(3)   NOT NULL DEFAULT 'INR',
    created_at         TIMESTAMP    NOT NULL,
    expires_at         TIMESTAMP    NOT NULL,
    confirmed_order_id BIGINT       NULL,
    CONSTRAINT uk_order_drafts_public_ref UNIQUE (public_ref),
    CONSTRAINT fk_order_drafts_user FOREIGN KEY (user_id) REFERENCES app_users (id),
    CONSTRAINT fk_order_drafts_order FOREIGN KEY (confirmed_order_id) REFERENCES orders (id)
);

CREATE INDEX idx_order_drafts_user ON order_drafts (user_id);

CREATE TABLE order_draft_items (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    draft_id   BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity   INT    NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    line_total DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_order_draft_items_draft   FOREIGN KEY (draft_id)   REFERENCES order_drafts (id),
    CONSTRAINT fk_order_draft_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX idx_order_draft_items_draft ON order_draft_items (draft_id);
