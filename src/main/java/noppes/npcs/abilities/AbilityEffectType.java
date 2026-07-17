package noppes.npcs.abilities;

import net.minecraft.potion.Effect;

public enum AbilityEffectType {
    SLOWNESS("slowness"),
    WEAKNESS("weakness"),
    BLINDNESS("blindness"),
    POISON("poison"),
    WITHER("wither");

    private final String id;

    AbilityEffectType(final String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public static AbilityEffectType fromString(final String value) {
        if (value == null || value.isEmpty()) {
            return SLOWNESS;
        }
        final String normalized = value.trim().toLowerCase();
        for (final AbilityEffectType type : values()) {
            if (type.id.equals(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return SLOWNESS;
    }

    public Effect toMcEffect() {
        switch (this) {
            case WEAKNESS:
                return net.minecraft.potion.Effects.WEAKNESS;
            case BLINDNESS:
                return net.minecraft.potion.Effects.BLINDNESS;
            case POISON:
                return net.minecraft.potion.Effects.POISON;
            case WITHER:
                return net.minecraft.potion.Effects.WITHER;
            case SLOWNESS:
            default:
                return net.minecraft.potion.Effects.MOVEMENT_SLOWDOWN;
        }
    }
}
