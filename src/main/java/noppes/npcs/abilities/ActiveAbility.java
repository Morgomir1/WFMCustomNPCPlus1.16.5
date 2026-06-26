package noppes.npcs.abilities;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ActiveAbility {
    public static final int PHASE_CHARGE = 1;
    public static final int PHASE_ACTIVE = 2;

    public final UUID npcUuid;
    public final String abilityId;
    public final CnpcAbility ability;
    public final AbilityParams params;
    public final AbilityContext context;

    public int phase;
    public int ticksLeft;
    public int elapsedTicks;
    public final Set<String> hitUuids = new HashSet<>();

    public double sx;
    public double sy;
    public double sz;
    public double ex;
    public double ey;
    public double ez;
    public float yaw;
    public boolean jumpStyle;

    public ActiveAbility(
            final UUID npcUuid,
            final String abilityId,
            final CnpcAbility ability,
            final AbilityParams params,
            final AbilityContext context) {
        this.npcUuid = npcUuid;
        this.abilityId = abilityId;
        this.ability = ability;
        this.params = params;
        this.context = context;
    }
}
