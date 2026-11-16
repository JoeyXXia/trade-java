ALTER TABLE app_user ADD COLUMN updated_at TIMESTAMP(6) NOT NULL
    DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);

CREATE TABLE identity_audit (
    id CHAR(36) NOT NULL PRIMARY KEY,
    trace_id CHAR(36) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_identity_audit_actor FOREIGN KEY (actor_user_id) REFERENCES app_user(id),
    CONSTRAINT fk_identity_audit_target FOREIGN KEY (target_user_id) REFERENCES app_user(id),
    INDEX ix_identity_audit_target (target_user_id, created_at),
    INDEX ix_identity_audit_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
