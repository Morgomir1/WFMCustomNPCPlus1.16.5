package noppes.npcs.entity.data;

import noppes.npcs.entity.*;
import net.minecraft.nbt.*;
import noppes.npcs.constants.*;
import net.minecraftforge.eventbus.api.*;
import noppes.npcs.controllers.*;
import noppes.npcs.*;
import net.minecraft.util.math.*;
import java.util.*;

public class DataScript implements IScriptHandler
{
    private List<ScriptContainer> scripts;
    private String scriptLanguage;
    private EntityNPCInterface npc;
    private boolean enabled;
    public long lastInited;
    
    public DataScript(final EntityNPCInterface npc) {
        this.scripts = new ArrayList<ScriptContainer>();
        this.scriptLanguage = "ECMAScript";
        this.enabled = false;
        this.lastInited = -1L;
        this.npc = npc;
    }
    
    public void load(final CompoundNBT compound) {
        this.scripts = NBTTags.GetScript(compound.getList("Scripts", 10), this);
        this.scriptLanguage = compound.getString("ScriptLanguage");
        this.enabled = compound.getBoolean("ScriptEnabled");
    }
    
    public CompoundNBT save(final CompoundNBT compound) {
        compound.put("Scripts", (INBT)NBTTags.NBTScript(this.scripts));
        compound.putString("ScriptLanguage", this.scriptLanguage);
        compound.putBoolean("ScriptEnabled", this.enabled);
        return compound;
    }
    
    @Override
    public void runScript(final EnumScriptType type, final Event event) {
        if (!this.isEnabled()) {
            return;
        }
        if (ScriptController.Instance.lastLoaded > this.lastInited) {
            this.lastInited = ScriptController.Instance.lastLoaded;
            if (type != EnumScriptType.INIT) {
                EventHooks.onNPCInit(this.npc);
            }
        }
        for (final ScriptContainer script : this.scripts) {
            script.run(type, event);
        }
    }
    
    public boolean isEnabled() {
        return this.enabled && ScriptController.HasStart && !this.npc.level.isClientSide;
    }
    
    @Override
    public boolean isClient() {
        return this.npc.isClientSide();
    }
    
    @Override
    public boolean getEnabled() {
        return this.enabled;
    }
    
    @Override
    public void setEnabled(final boolean bo) {
        this.enabled = bo;
    }
    
    @Override
    public String getLanguage() {
        return this.scriptLanguage;
    }
    
    @Override
    public void setLanguage(final String lang) {
        this.scriptLanguage = lang;
    }
    
    @Override
    public List<ScriptContainer> getScripts() {
        return this.scripts;
    }
    
    @Override
    public String noticeString() {
        // Avoid Guava MoreObjects: on Java 17+ Nashorn/script paths it can throw
        // LinkageError (loader constraint on MoreObjects$ToStringHelper) and crash the server.
        final BlockPos pos = this.npc.blockPosition();
        return this.npc.getClass().getSimpleName()
                + "{x=" + pos.getX() + ", y=" + pos.getY() + ", z=" + pos.getZ() + "}";
    }
    
    @Override
    public Map<Long, String> getConsoleText() {
        final Map<Long, String> map = new TreeMap<Long, String>();
        int tab = 0;
        for (final ScriptContainer script : this.getScripts()) {
            ++tab;
            for (final Map.Entry<Long, String> entry : script.console.entrySet()) {
                map.put(entry.getKey(), " tab " + tab + ":\n" + entry.getValue());
            }
        }
        return map;
    }
    
    @Override
    public void clearConsole() {
        for (final ScriptContainer script : this.getScripts()) {
            script.console.clear();
        }
    }
}
