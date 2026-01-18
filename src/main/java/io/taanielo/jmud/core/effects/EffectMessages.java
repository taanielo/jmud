package io.taanielo.jmud.core.effects;

public class EffectMessages {
    private final String applySelf;
    private final String applyRoom;
    private final String expireSelf;
    private final String expireRoom;
    private final String examine;

    public EffectMessages(
        String applySelf,
        String applyRoom,
        String expireSelf,
        String expireRoom,
        String examine
    ) {
        this.applySelf = normalize(applySelf);
        this.applyRoom = normalize(applyRoom);
        this.expireSelf = normalize(expireSelf);
        this.expireRoom = normalize(expireRoom);
        this.examine = normalize(examine);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String applySelf() {
        return applySelf;
    }

    public String applyRoom() {
        return applyRoom;
    }

    public String expireSelf() {
        return expireSelf;
    }

    public String expireRoom() {
        return expireRoom;
    }

    public String examine() {
        return examine;
    }
}
