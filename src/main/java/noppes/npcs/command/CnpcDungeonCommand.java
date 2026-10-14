package noppes.npcs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.dungeon.AsyncDungeonBuilder;
import noppes.npcs.dungeon.DungeonDefinition;
import noppes.npcs.dungeon.DungeonRegistry;

@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CnpcDungeonCommand {
    private static final int PERM = 2;
    private static final SuggestionProvider<CommandSource> DUNGEON_NAMES = (ctx, builder) -> {
        final Set<String> names = new LinkedHashSet<>();
        for (final String id : DungeonRegistry.names()) {
            names.add(id);
        }
        return ISuggestionProvider.suggest(names, builder);
    };

    private CnpcDungeonCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(final CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(tree("cncpdungeon"));
        dispatcher.register(tree("cnpcdungeon"));
    }

    private static LiteralArgumentBuilder<CommandSource> tree(final String name) {
        return Commands.literal(name)
                .requires(src -> src.hasPermission(PERM))
                .executes(CnpcDungeonCommand::usage)
                .then(Commands.literal("cancel").executes(CnpcDungeonCommand::cancel))
                .then(Commands.literal("status").executes(CnpcDungeonCommand::status))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(DUNGEON_NAMES)
                        .executes(ctx -> build(ctx, ""))
                        .then(Commands.argument("extra", StringArgumentType.greedyString())
                                .executes(ctx -> build(ctx, StringArgumentType.getString(ctx, "extra")))));
    }

    private static int usage(final CommandContext<CommandSource> ctx) {
        final StringBuilder ids = new StringBuilder();
        final Set<String> seen = new LinkedHashSet<>();
        for (final String id : DungeonRegistry.names()) {
            final DungeonDefinition def = DungeonRegistry.get(id);
            if (def != null && seen.add(def.id)) {
                if (ids.length() > 0) {
                    ids.append(", ");
                }
                ids.append(def.id);
            }
        }
        ctx.getSource().sendSuccess(new StringTextComponent(
                "§7/cncpdungeon <название> [dry] [force] [1-4]\n"
                        + "§7/cncpdungeon cancel | status\n"
                        + "§fДанжи: §e" + ids), false);
        return 1;
    }

    private static int cancel(final CommandContext<CommandSource> ctx) {
        ctx.getSource().sendSuccess(new StringTextComponent(AsyncDungeonBuilder.cancel()), false);
        return 1;
    }

    private static int status(final CommandContext<CommandSource> ctx) {
        ctx.getSource().sendSuccess(new StringTextComponent(AsyncDungeonBuilder.status()), false);
        return 1;
    }

    private static int build(final CommandContext<CommandSource> ctx, final String extra) {
        final CommandSource src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayerEntity)) {
            src.sendFailure(new StringTextComponent("Команду может выполнить только игрок."));
            return 0;
        }
        final ServerPlayerEntity player = (ServerPlayerEntity) src.getEntity();
        final String name = StringArgumentType.getString(ctx, "name");
        if ("cancel".equalsIgnoreCase(name)) {
            src.sendSuccess(new StringTextComponent(AsyncDungeonBuilder.cancel()), false);
            return 1;
        }
        if ("status".equalsIgnoreCase(name)) {
            src.sendSuccess(new StringTextComponent(AsyncDungeonBuilder.status()), false);
            return 1;
        }
        final DungeonDefinition def = DungeonRegistry.get(name);
        if (def == null) {
            src.sendFailure(new StringTextComponent("Неизвестный данж: " + name + ". /cncpdungeon — список."));
            return 0;
        }
        final boolean dry = AsyncDungeonBuilder.hasFlag(extra, "dry");
        final boolean force = AsyncDungeonBuilder.hasFlag(extra, "force");
        final String msg = AsyncDungeonBuilder.start(player, def,
                AsyncDungeonBuilder.parseLevels(extra, def), dry, force);
        src.sendSuccess(new StringTextComponent(msg), false);
        return 1;
    }
}
