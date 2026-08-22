-- Phase 1: core commerce schema.
-- Written in MySQL-flavoured DDL that H2 also accepts in MODE=MySQL, so one
-- migration set serves both the default dev database and a real MySQL server.
-- Avoids ON UPDATE CURRENT_TIMESTAMP (unsupported by H2); timestamps are
-- maintained by Hibernate instead.

CREATE TABLE app_users (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_ref            VARCHAR(36)  NOT NULL,
    username              VARCHAR(50)  NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    full_name             VARCHAR(120),
    role                  VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP    NULL,
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP    NOT NULL,
    CONSTRAINT uk_app_users_public_ref UNIQUE (public_ref),
    CONSTRAINT uk_app_users_username   UNIQUE (username),
    CONSTRAINT uk_app_users_email      UNIQUE (email)
);

CREATE TABLE products (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku            VARCHAR(40)  NOT NULL,
    name           VARCHAR(200) NOT NULL,
    brand          VARCHAR(80)  NOT NULL,
    category       VARCHAR(60)  NOT NULL,
    subcategory    VARCHAR(60),
    color          VARCHAR(40),
    size_label     VARCHAR(30),
    material       VARCHAR(60),
    description    TEXT,
    price          DECIMAL(12, 2) NOT NULL,
    currency       VARCHAR(3)   NOT NULL DEFAULT 'INR',
    stock_quantity INT          NOT NULL DEFAULT 0,
    rating         DECIMAL(2, 1),
    image_url      VARCHAR(500),
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    CONSTRAINT uk_products_sku UNIQUE (sku)
);

CREATE INDEX idx_products_brand    ON products (brand);
CREATE INDEX idx_products_category ON products (category);
CREATE INDEX idx_products_price    ON products (price);

CREATE TABLE orders (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number           VARCHAR(30)  NOT NULL,
    user_id                BIGINT       NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    placed_at              TIMESTAMP    NOT NULL,
    expected_delivery_date DATE,
    delivered_at           TIMESTAMP    NULL,
    cancelled_at           TIMESTAMP    NULL,
    total_amount           DECIMAL(12, 2) NOT NULL,
    currency               VARCHAR(3)   NOT NULL DEFAULT 'INR',
    shipping_address       VARCHAR(400),
    created_at             TIMESTAMP    NOT NULL,
    updated_at             TIMESTAMP    NOT NULL,
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES app_users (id)
);

CREATE INDEX idx_orders_user   ON orders (user_id);
CREATE INDEX idx_orders_status ON orders (status);

CREATE TABLE order_items (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity   INT    NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    line_total DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_order_items_order   FOREIGN KEY (order_id)   REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX idx_order_items_order ON order_items (order_id);

CREATE TABLE order_events (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMP   NOT NULL,
    note        VARCHAR(300),
    CONSTRAINT fk_order_events_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE INDEX idx_order_events_order ON order_events (order_id);
