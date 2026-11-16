ALTER TABLE app_user ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE refresh_session (
    id CHAR(36) PRIMARY KEY,
    family_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    INDEX idx_refresh_family (family_id),
    INDEX idx_refresh_user (user_id, revoked_at)
) ENGINE=InnoDB;

CREATE TABLE login_throttle (
    bucket_hash CHAR(64) PRIMARY KEY,
    attempts INT NOT NULL DEFAULT 0,
    window_start TIMESTAMP(6) NOT NULL,
    CONSTRAINT ck_login_attempts CHECK (attempts >= 0)
) ENGINE=InnoDB;
