package com.gmail.anthony17j.simplefabriccustomskin.command;

import com.gmail.anthony17j.simplefabriccustomskin.skin.SkinManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SkinCommand {

    private SkinCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("skin")
                .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
            .then(Commands.literal("set")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("skin", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(SkinManager.listAvailableSkinNames(), builder))
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            String skinName = StringArgumentType.getString(ctx, "skin");
                            boolean success = SkinManager.applySkinByName(target, skinName, true);
                            if (!success) {
                            ctx.getSource().sendFailure(Component.literal("Could not apply skin '" + skinName + "'. Ensure skins/" + skinName + ".png exists and is valid 64x64 or 64x32."));
                            return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("Applied skin '" + skinName + "' to " + target.getGameProfile().name()), false);
                            return 1;
                        })
                    )
                )
            )
                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            ServerPlayer self = ctx.getSource().getPlayer();
                            if (self == null) {
                                ctx.getSource().sendFailure(Component.literal("Specify a player when running from console: /skin clear <player>."));
                                return 0;
                            }
                            SkinManager.clearSkin(self, true);
                            ctx.getSource().sendSuccess(() -> Component.literal("Cleared skin for " + self.getGameProfile().name()), false);
                            return 1;
                        })
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    SkinManager.clearSkin(target, true);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Cleared skin for " + target.getGameProfile().name()), false);
                                    return 1;
                                })
                        )
                )
        );
    }
}
