package noppes.npcs.abilities;

import java.util.Map;
import java.util.Set;

public interface CnpcAbility {
    String getId();

    boolean requiresTarget();

    Map<String, Object> defaultParams();

    Set<String> knownParamKeys();

    boolean onStart(ActiveAbility active, AbilityContext ctx);

    TickResult tick(ActiveAbility active, AbilityContext ctx);

    void onEnd(ActiveAbility active, AbilityContext ctx);

    void onCancel(ActiveAbility active, AbilityContext ctx);
}
