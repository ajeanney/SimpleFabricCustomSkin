package com.gmail.anthony17j.simplefabriccustomskin.test;

import com.gmail.anthony17j.simplefabriccustomskin.mixin.PlayerAccessor;
import com.gmail.anthony17j.simplefabriccustomskin.skin.SkinManager;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import static javax.imageio.ImageIO.write;

public class SkinCommandGameTest {

    private static ServerPlayer createMockServerPlayer(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();

        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    @GameTest(maxTicks = 600)
    public void operatorCommandAppliesSkin(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper, "skin_target_a");
        MinecraftServer server = helper.getLevel().getServer();
        String originalTextureValue = getFirstTextureValue(player);

        setupMockProviderAndSkinFile(helper, "testskin");

        String cmd = "skin set " + player.getGameProfile().name() + " testskin";
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);

        helper.runAfterDelay(10, () -> {
            String assigned = SkinManager.getAssignedSkinName(player.getUUID());
            if (!"testskin".equals(assigned)) {
                helper.fail("Expected persisted skin assignment 'testskin', got " + assigned);
                return;
            }
            if (player.getGameProfile().properties().get("textures").isEmpty()) {
                helper.fail("Expected textures property to be present on player profile");
                return;
            }
            String appliedTextureValue = getFirstTextureValue(player);
            if (appliedTextureValue.equals(originalTextureValue)) {
                helper.fail("Expected texture value to change after /skin set");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 600)
    public void nonOperatorCannotRunSkinCommand(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper, "skin_target_b");
        ServerPlayer lowPermSourcePlayer = createMockServerPlayer(helper, "skin_source_lowperm");
        MinecraftServer server = helper.getLevel().getServer();

        setupMockProviderAndSkinFile(helper, "testskin_lowperm");

        String cmd = "skin set " + player.getGameProfile().name() + " testskin_lowperm";
        server.getCommands().performPrefixedCommand(lowPermSourcePlayer.createCommandSourceStack(), cmd);
        if (SkinManager.getAssignedSkinName(player.getUUID()) != null) {
            helper.fail("Non-op should not be able to assign skin");
            return;
        }

        helper.succeed();
    }

    @GameTest(maxTicks = 600)
    public void clearCommandRemovesSkin(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper, "skin_target_c");
        MinecraftServer server = helper.getLevel().getServer();

        setPlayerTexture(player, "original_texture_value");
        SkinManager.cacheOriginalSkin(player);
        String originalTextureValue = getFirstTextureValue(player);

        setupMockProviderAndSkinFile(helper, "testskin_clear");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "skin set " + player.getGameProfile().name() + " testskin_clear");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "skin clear " + player.getGameProfile().name());

        helper.runAfterDelay(10, () -> {
            if (SkinManager.getAssignedSkinName(player.getUUID()) != null) {
                helper.fail("Expected skin assignment to be removed after /skin clear");
                return;
            }
            if (player.getGameProfile().properties().get("textures").isEmpty()) {
                helper.fail("Expected original personal textures to be restored after /skin clear");
                return;
            }
            String restoredTextureValue = getFirstTextureValue(player);
            if (!restoredTextureValue.equals(originalTextureValue)) {
                helper.fail("Expected /skin clear to restore original personal skin texture value");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 800)
    public void skinAssignmentPersistsThroughReloadAndJoinApply(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper, "skin_target_d");
        MinecraftServer server = helper.getLevel().getServer();

        setupMockProviderAndSkinFile(helper, "testskin_persist");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "skin set " + player.getGameProfile().name() + " testskin_persist");

        // Simulate a reconnect by replacing the live profile, then reapplying from saved assignment.
        GameProfile profile = player.getGameProfile();
        ((PlayerAccessor) player).setGameProfile(new GameProfile(profile.id(), profile.name()));
        SkinManager.applySavedSkin(player, false);

        helper.runAfterDelay(10, () -> {
            String assigned = SkinManager.getAssignedSkinName(player.getUUID());
            if (!"testskin_persist".equals(assigned)) {
                helper.fail("Expected persisted skin name to be testskin_persist, got " + assigned);
                return;
            }
            try {
                String persisted = Files.readString(SkinManager.getPersistencePath());
                String expectedEntry = "\"" + player.getUUID() + "\": \"testskin_persist\"";
                if (!persisted.contains(expectedEntry)) {
                    helper.fail("Expected persistence file to contain player skin assignment entry");
                    return;
                }
            } catch (Exception e) {
                helper.fail("Failed to read persistence file: " + e.getMessage());
                return;
            }
            if (player.getGameProfile().properties().get("textures").isEmpty()) {
                helper.fail("Expected saved skin to be reapplied on simulated join");
                return;
            }
            helper.succeed();
        });
    }

    private static void setupMockProviderAndSkinFile(GameTestHelper helper, String skinName) {
        try {
            Path skinsDir = SkinManager.getSkinsDirectory();
            Files.createDirectories(skinsDir);

            BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Path skinPath = skinsDir.resolve(skinName + ".png");
            write(image, "png", skinPath.toFile());

            SkinManager.setSkinPropertyProvider((file, name) -> {
                String payload = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + name + "\"}}}";
                String value = Base64.getEncoder().encodeToString(payload.getBytes());
                return java.util.Optional.of(new Property("textures", value, ""));
            });
        } catch (Exception e) {
            helper.fail("Failed to set up mock skin environment: " + e.getMessage());
        }
    }

    private static String getFirstTextureValue(ServerPlayer player) {
        var textures = player.getGameProfile().properties().get("textures");
        if (textures == null || textures.isEmpty()) {
            return "";
        }
        return textures.iterator().next().value();
    }

    private static void setPlayerTexture(ServerPlayer player, String textureValue) {
        GameProfile profile = player.getGameProfile();
        var props = com.google.common.collect.ArrayListMultimap.<String, Property>create();
        props.put("textures", new Property("textures", textureValue, ""));
        var map = new com.mojang.authlib.properties.PropertyMap(props);
        ((PlayerAccessor) player).setGameProfile(new GameProfile(profile.id(), profile.name(), map));
    }
}
