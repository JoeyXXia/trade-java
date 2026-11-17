CREATE TABLE catalog_product (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_catalog_version CHECK (version >= 0),
    CONSTRAINT fk_catalog_creator FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT fk_catalog_updater FOREIGN KEY (updated_by) REFERENCES app_user(id),
    INDEX ix_catalog_published_id (published, id),
    INDEX ix_catalog_published_title (published, title, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE catalog_sku (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    price_cents BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_catalog_sku_code UNIQUE (sku_code),
    CONSTRAINT fk_catalog_sku_product FOREIGN KEY (product_id) REFERENCES catalog_product(id),
    CONSTRAINT fk_catalog_sku_creator FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT fk_catalog_sku_updater FOREIGN KEY (updated_by) REFERENCES app_user(id),
    CONSTRAINT ck_catalog_price CHECK (price_cents > 0),
    INDEX ix_catalog_sku_product_enabled (product_id, enabled, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
