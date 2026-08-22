package noppes.npcs.controllers;

import java.lang.reflect.*;
import net.minecraft.nbt.*;
import java.util.*;
import noppes.npcs.constants.*;
import net.minecraftforge.eventbus.api.*;
import java.io.*;
import javax.script.*;
import noppes.npcs.*;
import noppes.npcs.api.constants.*;
import net.minecraft.util.math.*;
import noppes.npcs.api.wrapper.*;
import java.util.function.*;
import noppes.npcs.shared.client.util.*;
import noppes.npcs.shared.common.util.*;

public class ScriptContainer
{
    private static final String lock = "lock";
    public static ScriptContainer Current;
    private static String CurrentType;
    public static final HashMap<String, Object> Data;
    public String fullscript;
    public String script;
    public TreeMap<Long, String> console;
    public boolean errored;
    public List<String> scripts;
    private HashSet<String> unknownFunctions;
    public long lastCreated;
    private String currentScriptLanguage;
    private ScriptEngine engine;
    private IScriptHandler handler;
    private boolean init;
    private static Method luaCoerce;
    private static Method luaCall;
    
    private static void FillMap(final Class c) {
        try {
            ScriptContainer.Data.put(c.getSimpleName(), c.newInstance());
        }
        catch (Exception ex) {}
        final Field[] declaredFields2;
        final Field[] declaredFields = declaredFields2 = c.getDeclaredFields();
        for (final Field field : declaredFields2) {
            try {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == Integer.TYPE) {
                    ScriptContainer.Data.put(c.getSimpleName() + "_" + field.getName(), field.getInt(null));
                }
            }
            catch (Exception ex2) {}
        }
    }
    
    public ScriptContainer(final IScriptHandler handler) {
        this.fullscript = "";
        this.script = "";
        this.console = new TreeMap<Long, String>();
        this.errored = false;
        this.scripts = new ArrayList<String>();
        this.unknownFunctions = new HashSet<String>();
        this.lastCreated = 0L;
        this.currentScriptLanguage = null;
        this.engine = null;
        this.handler = null;
        this.init = false;
        this.handler = handler;
    }
    
    public void load(final CompoundNBT compound) {
        this.script = compound.getString("Script");
        this.console = NBTTags.GetLongStringMap(compound.getList("Console", 10));
        this.scripts = NBTTags.getStringList(compound.getList("ScriptList", 10));
        this.lastCreated = 0L;
    }
    
    public CompoundNBT save(final CompoundNBT compound) {
        compound.putString("Script", this.script);
        compound.put("Console", (INBT)NBTTags.NBTLongStringMap(this.console));
        compound.put("ScriptList", (INBT)NBTTags.nbtStringList(this.scripts));
        return compound;
    }
    
    private String getFullCode() {
        if (!this.init) {
            this.fullscript = this.script;
            if (!this.fullscript.isEmpty()) {
                this.fullscript += "\n";
            }
            for (final String loc : this.scripts) {
                final String code = ScriptController.Instance.scripts.get(loc);
                if (code != null && !code.isEmpty()) {
                    this.fullscript = this.fullscript + code + "\n";
                }
            }
            this.unknownFunctions = new HashSet<String>();
        }
        return this.fullscript;
    }
    
    public void run(final EnumScriptType type, final Event event) {
        this.run(type.function, event);
    }
    
    public void run(final String type, final Object event) {
        if (this.errored || !this.hasCode() || this.unknownFunctions.contains(type) || !CustomNpcs.EnableScripting) {
            return;
        }
        this.setEngine(this.handler.getLanguage());
        if (this.engine == null) {
            return;
        }
        if (ScriptController.Instance.lastLoaded > this.lastCreated) {
            this.lastCreated = ScriptController.Instance.lastLoaded;
            this.init = false;
        }
        synchronized ("lock") {
            ScriptContainer.Current = this;
            ScriptContainer.CurrentType = type;
            final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw);
            this.engine.getContext().setWriter(pw);
            this.engine.getContext().setErrorWriter(pw);
            try {
                if (!this.init) {
                    this.engine.eval(this.getFullCode());
                    this.init = true;
                }
                if (this.engine.getFactory().getLanguageName().equals("lua")) {
                    final Object ob = this.engine.get(type);
                    if (ob != null) {
                        if (ScriptContainer.luaCoerce == null) {
                            ScriptContainer.luaCoerce = Class.forName("org.luaj.vm2.lib.jse.CoerceJavaToLua").getMethod("coerce", Object.class);
                            ScriptContainer.luaCall = ob.getClass().getMethod("call", Class.forName("org.luaj.vm2.LuaValue"));
                        }
                        ScriptContainer.luaCall.invoke(ob, ScriptContainer.luaCoerce.invoke(null, event));
                    }
                    else {
                        this.unknownFunctions.add(type);
                    }
                }
                else {
                    ((Invocable)this.engine).invokeFunction(type, event);
                }
            }
            catch (NoSuchMethodException e2) {
                this.unknownFunctions.add(type);
            }
            catch (Throwable e) {
                this.errored = true;
                e.printStackTrace(pw);
                try {
                    NoppesUtilServer.NotifyOPs(this.handler.noticeString() + " script errored", new Object[0]);
                }
                catch (Throwable notifyError) {
                    // noticeString itself must never escalate a script error into a server crash
                    // (seen: Guava MoreObjects LinkageError under Java 17)
                    NoppesUtilServer.NotifyOPs("script errored", new Object[0]);
                    notifyError.printStackTrace(pw);
                }
            }
            finally {
                this.appandConsole(sw.getBuffer().toString().trim());
                pw.close();
                ScriptContainer.Current = null;
            }
        }
    }
    
    public void appandConsole(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        final long time = System.currentTimeMillis();
        if (this.console.containsKey(time)) {
            message = this.console.get(time) + "\n" + message;
        }
        this.console.put(time, message);
        while (this.console.size() > 40) {
            this.console.remove(this.console.firstKey());
        }
    }
    
    public boolean hasCode() {
        return !this.getFullCode().isEmpty();
    }
    
    public void setEngine(final String scriptLanguage) {
        if (this.currentScriptLanguage != null && this.currentScriptLanguage.equals(scriptLanguage)) {
            return;
        }
        this.engine = ScriptController.Instance.getEngineByName(scriptLanguage);
        if (this.engine == null) {
            this.errored = true;
            return;
        }
        for (final Map.Entry<String, Object> entry : ScriptContainer.Data.entrySet()) {
            this.engine.put(entry.getKey(), entry.getValue());
        }
        this.engine.put("dump", new Dump());
        this.engine.put("log", new Log());
        this.currentScriptLanguage = scriptLanguage;
        this.init = false;
    }
    
    public boolean isValid() {
        return this.init && !this.errored;
    }
    
    static {
        Data = new HashMap<String, Object>();
        FillMap(AnimationType.class);
        FillMap(EntitiesType.class);
        FillMap(RoleType.class);
        FillMap(JobType.class);
        FillMap(SideType.class);
        FillMap(PotionEffectType.class);
        FillMap(ParticleType.class);
        ScriptContainer.Data.put("PosZero", new noppes.npcs.api.wrapper.BlockPosWrapper(BlockPos.ZERO));
    }
    
    private class Dump implements Function<Object, String>
    {
        @Override
        public String apply(final Object o) {
            if (o == null) {
                return "null";
            }
            final StringBuilder builder = new StringBuilder();
            builder.append(o + ":" + NoppesStringUtils.newLine());
            for (final Field field : o.getClass().getFields()) {
                try {
                    builder.append(field.getName() + " - " + field.getType().getSimpleName() + ", ");
                }
                catch (IllegalArgumentException ex) {}
            }
            for (final Method method : o.getClass().getMethods()) {
                try {
                    String s = method.getName() + "(";
                    for (final Class c : method.getParameterTypes()) {
                        s = s + c.getSimpleName() + ", ";
                    }
                    if (s.endsWith(", ")) {
                        s = s.substring(0, s.length() - 2);
                    }
                    builder.append(s + "), ");
                }
                catch (IllegalArgumentException ex2) {}
            }
            return builder.toString();
        }
    }
    
    private class Log implements Function<Object, Void>
    {
        @Override
        public Void apply(final Object o) {
            ScriptContainer.this.appandConsole(o + "");
            LogWriter.info(o + "");
            return null;
        }
    }
}
