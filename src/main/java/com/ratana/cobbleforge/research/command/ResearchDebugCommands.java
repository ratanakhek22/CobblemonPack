package com.ratana.cobbleforge.research.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.ratana.cobbleforge.research.network.ResearchNetworking;
import com.ratana.cobbleforge.research.player.ModAttachments;
import com.ratana.cobbleforge.research.player.ResearchPlayerData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Debug-only command for testing the research system without grinding dex/captures. */
@EventBusSubscriber(modid = "cobbleforge")
public final class ResearchDebugCommands {
    private ResearchDebugCommands() {}

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("research")
                .requires(src -> src.hasPermission(2)) // op-only -- this is a testing shortcut, not player-facing
                .then(Commands.literal("points")
                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                .executes(ResearchDebugCommands::giveSelf)
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ResearchDebugCommands::giveTarget)))));
    }

    private static int giveSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return givePoints(ctx, ctx.getSource().getPlayerOrException());
    }

    private static int giveTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return givePoints(ctx, EntityArgument.getPlayer(ctx, "target"));
    }

    private static int givePoints(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        ResearchPlayerData data = player.getData(ModAttachments.RESEARCH_DATA.get());
        data.addPoints(amount);
        ResearchNetworking.sendSync(player, data);

        ctx.getSource().sendSuccess(() ->
                        Component.literal("Gave " + amount + " research points to " + player.getGameProfile().getName()),
                true);
        return 1;
    }
}