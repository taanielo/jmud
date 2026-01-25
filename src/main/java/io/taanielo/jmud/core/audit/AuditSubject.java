package io.taanielo.jmud.core.audit;

import java.util.Objects;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.Item;

public record AuditSubject(String type, String id) {
    public AuditSubject {
        Objects.requireNonNull(type, "Subject type is required");
        Objects.requireNonNull(id, "Subject id is required");
    }

    public static AuditSubject player(Username username) {
        Objects.requireNonNull(username, "Username is required");
        return new AuditSubject("player", username.getValue());
    }

    public static AuditSubject item(Item item) {
        Objects.requireNonNull(item, "Item is required");
        return new AuditSubject("item", item.getId().getValue());
    }

    public static AuditSubject system(String id) {
        return new AuditSubject("system", id);
    }
}
