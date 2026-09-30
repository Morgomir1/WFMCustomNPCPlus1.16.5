package noppes.npcs.entity;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;

/**
 * Толпа NPC на шагах/воде забивает клиентский пул OpenAL (247).
 * Режем серверные пакеты у источника, а не на клиенте WFM.
 */
public final class NpcMovementSounds {
	private static final int MAX_STEP_PER_TICK = 6;
	private static final int MAX_SWIM_PER_TICK = 4;
	private static final int MAX_SPLASH_PER_TICK = 3;
	private static final int MIN_STEP_GAP = 8;
	private static final int MIN_SWIM_GAP = 8;
	private static final int MIN_SPLASH_GAP = 16;
	private static final int LAST_MAP_LIMIT = 768;

	private static int cachedGameTime = Integer.MIN_VALUE;
	private static int stepsThisTick;
	private static int swimsThisTick;
	private static int splashesThisTick;
	private static final Map<Integer, Integer> LAST_STEP = new HashMap<Integer, Integer>();
	private static final Map<Integer, Integer> LAST_SWIM = new HashMap<Integer, Integer>();
	private static final Map<Integer, Integer> LAST_SPLASH = new HashMap<Integer, Integer>();

	private NpcMovementSounds() {
	}

	public static boolean allow(Entity npc, SoundEvent sound) {
		if (npc == null || sound == null) {
			return true;
		}
		Kind kind = classify(sound.getLocation());
		if (kind == null) {
			return true;
		}
		World world = npc.level;
		if (world == null) {
			return false;
		}
		rollTick(world);
		int id = npc.getId();
		int now = npc.tickCount;
		if (kind == Kind.STEP) {
			if (stepsThisTick >= MAX_STEP_PER_TICK || tooSoon(LAST_STEP, id, now, MIN_STEP_GAP)) {
				return false;
			}
			LAST_STEP.put(id, now);
			stepsThisTick++;
			return true;
		}
		if (kind == Kind.SWIM) {
			if (swimsThisTick >= MAX_SWIM_PER_TICK || tooSoon(LAST_SWIM, id, now, MIN_SWIM_GAP)) {
				return false;
			}
			LAST_SWIM.put(id, now);
			swimsThisTick++;
			return true;
		}
		if (splashesThisTick >= MAX_SPLASH_PER_TICK || tooSoon(LAST_SPLASH, id, now, MIN_SPLASH_GAP)) {
			return false;
		}
		LAST_SPLASH.put(id, now);
		splashesThisTick++;
		return true;
	}

	private static boolean tooSoon(Map<Integer, Integer> lastById, int id, int now, int minGap) {
		Integer last = lastById.get(id);
		return last != null && now - last < minGap;
	}

	private static void rollTick(World world) {
		int t = (int) world.getGameTime();
		if (t == cachedGameTime) {
			return;
		}
		cachedGameTime = t;
		stepsThisTick = 0;
		swimsThisTick = 0;
		splashesThisTick = 0;
		if (LAST_STEP.size() + LAST_SWIM.size() + LAST_SPLASH.size() > LAST_MAP_LIMIT) {
			LAST_STEP.clear();
			LAST_SWIM.clear();
			LAST_SPLASH.clear();
		}
	}

	private static Kind classify(ResourceLocation location) {
		if (location == null) {
			return null;
		}
		String path = location.getPath();
		if (path.endsWith(".splash")) {
			return Kind.SPLASH;
		}
		if (path.endsWith(".swim")) {
			return Kind.SWIM;
		}
		if (path.endsWith(".step") || path.endsWith(".slide")) {
			return Kind.STEP;
		}
		return null;
	}

	private enum Kind {
		STEP,
		SWIM,
		SPLASH
	}
}
