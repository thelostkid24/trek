package com.sahyatri.common.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Append-only audit trail (§3.1). Joins the caller's transaction. */
@Component
public class AuditLog {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditLog(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** @param actorId the signed-in user, or null for a system job */
    public void record(UUID actorId, String action, String entityType, UUID entityId, Map<String, ?> data) {
        jdbc.update("""
                        INSERT INTO audit_events (id, actor_id, action, entity_type, entity_id, data, created_at)
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)""",
                UUID.randomUUID(), actorId, action, entityType, entityId, json.writeValueAsString(data),
                Timestamp.from(Instant.now()));
    }
}
