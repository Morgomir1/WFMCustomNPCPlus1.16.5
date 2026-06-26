package noppes.npcs.abilities;

import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.shared.common.util.LogWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AbilityRunner {
    private static final Map<UUID, ActiveAbility> ACTIVE = new ConcurrentHashMap<>();

    private AbilityRunner() {
    }

    public static boolean start(
            final ICustomNpc npc,
            final String abilityId,
            final IEntityLiving target,
            final Map<String, Object> overrides) {
        if (npc == null || abilityId == null || abilityId.isEmpty()) {
            return false;
        }
        final UUID uuid = parseUuid(npc);
        if (uuid == null) {
            return false;
        }
        if (ACTIVE.containsKey(uuid)) {
            return false;
        }

        final CnpcAbility ability = AbilityRegistry.get(abilityId);
        if (ability == null) {
            LogWriter.info("AbilityRunner: unknown ability id: " + abilityId);
            return false;
        }
        if (ability.requiresTarget() && (target == null || !target.isAlive())) {
            return false;
        }

        final AbilityParams params = AbilityParams.merge(
                ability.defaultParams(),
                overrides,
                ability.knownParamKeys());
        final AbilityContext ctx = new AbilityContext(npc, target, npc.getWorld(), params);
        final ActiveAbility active = new ActiveAbility(uuid, abilityId, ability, params, ctx);
        if (!ability.onStart(active, ctx)) {
            return false;
        }
        ACTIVE.put(uuid, active);
        return true;
    }

    public static boolean isBusy(final ICustomNpc npc) {
        final UUID uuid = parseUuid(npc);
        return uuid != null && ACTIVE.containsKey(uuid);
    }

    public static String getActiveId(final ICustomNpc npc) {
        final UUID uuid = parseUuid(npc);
        if (uuid == null) {
            return "";
        }
        final ActiveAbility active = ACTIVE.get(uuid);
        return active == null ? "" : active.abilityId;
    }

    public static void cancel(final ICustomNpc npc) {
        final UUID uuid = parseUuid(npc);
        if (uuid == null) {
            return;
        }
        final ActiveAbility active = ACTIVE.remove(uuid);
        if (active != null) {
            active.ability.onCancel(active, active.context);
        }
    }

    public static void tickAll() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        final List<UUID> keys = new ArrayList<>(ACTIVE.keySet());
        for (final UUID uuid : keys) {
            final ActiveAbility active = ACTIVE.get(uuid);
            if (active == null) {
                continue;
            }
            tickOne(active);
        }
    }

    private static void tickOne(final ActiveAbility active) {
        final AbilityContext ctx = active.context;
        if (ctx.npc == null || !ctx.npc.isAlive()) {
            removeAndCancel(active);
            return;
        }
        if (active.ability.requiresTarget()) {
            if (ctx.target == null || !ctx.target.isAlive()) {
                removeAndCancel(active);
                return;
            }
        }

        final TickResult result = active.ability.tick(active, ctx);
        if (result == TickResult.FINISHED) {
            ACTIVE.remove(active.npcUuid);
            active.ability.onEnd(active, ctx);
        }
    }

    private static void removeAndCancel(final ActiveAbility active) {
        ACTIVE.remove(active.npcUuid);
        active.ability.onCancel(active, active.context);
    }

    private static UUID parseUuid(final ICustomNpc npc) {
        try {
            return UUID.fromString(String.valueOf(npc.getUUID()));
        } catch (final Exception e) {
            return null;
        }
    }
}
