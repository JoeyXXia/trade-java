-- Schema only. Test fixtures and demo accounts never belong in versioned migrations.
CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    login_name VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_app_user_login_name UNIQUE (login_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
