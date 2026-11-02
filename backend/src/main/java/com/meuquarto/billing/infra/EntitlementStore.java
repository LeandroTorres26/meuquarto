package com.meuquarto.billing.infra;

import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads the entitlements table with a fixed query; nothing user-provided is placed in the SQL text. */
@Component
public class EntitlementStore {

    private final JdbcClient jdbc;

    public EntitlementStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Set<String> themeIdsOwnedBy(long userId) {
        return Set.copyOf(jdbc.sql("""
                SELECT t.id FROM entitlements e
                JOIN themes t ON t.product_id = e.product_id
                WHERE e.user_id = :userId
                """).param("userId", userId).query(String.class).list());
    }
}
