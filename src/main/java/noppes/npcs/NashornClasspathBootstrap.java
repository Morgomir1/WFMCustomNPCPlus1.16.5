package noppes.npcs;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import noppes.npcs.shared.common.util.LogWriter;

/**
 * Standalone OpenJDK Nashorn (nashorn-core) registers via {@code META-INF/services} on the mod
 * classloader. CustomNPCs uses {@link ScriptEngineManager}'s no-arg constructor, which follows
 * the thread context classloader — callers should set TCCL to the mod classloader (see
 * {@link CustomNpcs} setup / server about-to-start).
 */
public final class NashornClasspathBootstrap {
    private static boolean logged;

    private NashornClasspathBootstrap() {
    }

    /** Probes Nashorn with an explicit classloader (does not change TCCL). */
    public static void ensure() {
        final ClassLoader modCl = NashornClasspathBootstrap.class.getClassLoader();
        final ScriptEngineManager mgr = new ScriptEngineManager(modCl);
        ScriptEngine engine = mgr.getEngineByName("nashorn");
        if (engine == null) {
            engine = mgr.getEngineByName("javascript");
        }
        if (!logged) {
            logged = true;
            if (engine != null) {
                LogWriter.info("CustomNPCs: Nashorn (standalone) ScriptEngine available: " + engine.getFactory().getEngineName());
            }
            else {
                LogWriter.error("CustomNPCs: Nashorn ScriptEngine not found — embed org.openjdk.nashorn:nashorn-core or use Java 8/11 with built-in Nashorn.");
            }
        }
    }
}
