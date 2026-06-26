package noppes.npcs.abilities;

import noppes.npcs.api.IWorld;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntityLiving;

public final class AbilityContext {
    public final ICustomNpc npc;
    public final IEntityLiving target;
    public final IWorld world;
    public final AbilityParams params;

    public AbilityContext(
            final ICustomNpc npc,
            final IEntityLiving target,
            final IWorld world,
            final AbilityParams params) {
        this.npc = npc;
        this.target = target;
        this.world = world;
        this.params = params;
    }
}
