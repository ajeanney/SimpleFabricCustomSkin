package com.gmail.anthony17j.simplefabriccustomskin.skin;

import com.gmail.anthony17j.simplefabriccustomskin.SimpleFabricCustomSkin;
import com.gmail.anthony17j.simplefabriccustomskin.mixin.PlayerAccessor;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.BiomeManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;
import java.util.stream.Stream;

public final class SkinManager {

    private static final String TEXTURE_PROPERTY = "textures";
    private static final String PLAYER_SKIN_FILE = "simplefabriccustomskin-player-skins.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static final Map<UUID, String> PLAYER_SKINS = new HashMap<>();
    private static final Map<UUID, Property> ORIGINAL_SKINS = new HashMap<>();

    public interface SkinPropertyProvider {
        Optional<Property> fromSkinFile(File skinFile, String skinName) throws IOException;
    }

    private static SkinPropertyProvider skinPropertyProvider = SkinManager::uploadSkinToMineskin;

    private SkinManager() {
    }

    public static void initialize() {
        reloadPlayerSkinsFromDisk();
        ensureSkinsDirectory();
    }

    public static Path getSkinsDirectory() {
        return FabricLoader.getInstance().getGameDir().resolve("skins");
    }

    public static Path getPersistencePath() {
        return FabricLoader.getInstance().getConfigDir().resolve(PLAYER_SKIN_FILE);
    }

    public static void ensureSkinsDirectory() {
        try {
            Files.createDirectories(getSkinsDirectory());
        } catch (IOException e) {
            SimpleFabricCustomSkin.LOGGER.warn("Failed to create skins folder: {}", e.getMessage());
        }
    }

    public static synchronized void setSkinPropertyProvider(SkinPropertyProvider provider) {
        skinPropertyProvider = provider;
    }

    public static synchronized void resetSkinPropertyProvider() {
        skinPropertyProvider = SkinManager::uploadSkinToMineskin;
    }

    public static synchronized void cacheOriginalSkin(ServerPlayer player) {
        var textures = player.getGameProfile().properties().get(TEXTURE_PROPERTY);
        if (textures == null || textures.isEmpty()) {
            return;
        }
        Property original = textures.iterator().next();
        if (original != null) {
            ORIGINAL_SKINS.putIfAbsent(player.getUUID(), original);
        }
    }

    public static List<String> listAvailableSkinNames() {
        ensureSkinsDirectory();
        try (Stream<Path> paths = Files.list(getSkinsDirectory())) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".png"))
                    .map(name -> name.substring(0, name.length() - 4))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public static synchronized boolean applySkinByName(ServerPlayer player, String skinName, boolean reload) {
        cacheOriginalSkin(player);

        File skinFile = getSkinsDirectory().resolve(skinName + ".png").toFile();
        if (!skinFile.exists() || !skinFile.isFile()) {
            return false;
        }

        if (!isValidSkinPng(skinFile)) {
            return false;
        }

        Optional<Property> property;
        try {
            property = skinPropertyProvider.fromSkinFile(skinFile, skinName);
        } catch (Exception e) {
            SimpleFabricCustomSkin.LOGGER.warn("Failed to read/upload skin '{}': {}", skinName, e.getMessage());
            return false;
        }

        if (property.isEmpty()) {
            return false;
        }

        setSkinProperty(player, property.get(), reload);
        PLAYER_SKINS.put(player.getUUID(), skinName);
        savePlayerSkinsToDisk();
        return true;
    }

    public static synchronized boolean clearSkin(ServerPlayer player, boolean reload) {
        clearSkinProperty(player, reload);
        boolean removed = PLAYER_SKINS.remove(player.getUUID()) != null;
        savePlayerSkinsToDisk();
        return removed;
    }

    public static synchronized void applySavedSkin(ServerPlayer player, boolean reload) {
        String skinName = PLAYER_SKINS.get(player.getUUID());
        if (skinName == null) {
            return;
        }
        File skinFile = getSkinsDirectory().resolve(skinName + ".png").toFile();
        if (!skinFile.exists() || !isValidSkinPng(skinFile)) {
            return;
        }

        try {
            Optional<Property> property = skinPropertyProvider.fromSkinFile(skinFile, skinName);
            property.ifPresent(value -> setSkinProperty(player, value, reload));
        } catch (Exception e) {
            SimpleFabricCustomSkin.LOGGER.warn("Failed to reapply saved skin '{}' for {}: {}", skinName, player.getGameProfile().name(), e.getMessage());
        }
    }

    public static synchronized String getAssignedSkinName(UUID playerUuid) {
        return PLAYER_SKINS.get(playerUuid);
    }

    public static synchronized void reloadPlayerSkinsFromDisk() {
        PLAYER_SKINS.clear();
        Path path = getPersistencePath();
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                savePlayerSkinsToDisk();
                return;
            }

            String json = Files.readString(path);
            Map<String, String> data = GSON.fromJson(json, MAP_TYPE);
            if (data == null) {
                return;
            }
            for (Map.Entry<String, String> entry : data.entrySet()) {
                try {
                    PLAYER_SKINS.put(UUID.fromString(entry.getKey()), entry.getValue());
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception e) {
            SimpleFabricCustomSkin.LOGGER.warn("Failed to read skin persistence file: {}", e.getMessage());
        }
    }

    private static synchronized void savePlayerSkinsToDisk() {
        Path path = getPersistencePath();
        try {
            Files.createDirectories(path.getParent());
            Map<String, String> data = new HashMap<>();
            for (Map.Entry<UUID, String> entry : PLAYER_SKINS.entrySet()) {
                data.put(entry.getKey().toString(), entry.getValue());
            }
            Files.writeString(path, GSON.toJson(data));
        } catch (IOException e) {
            SimpleFabricCustomSkin.LOGGER.warn("Failed to write skin persistence file: {}", e.getMessage());
        }
    }

    private static boolean isValidSkinPng(File skinFile) {
        try (InputStream in = new FileInputStream(skinFile)) {
            if (in.read() != 137) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }

        try {
            BufferedImage image = ImageIO.read(skinFile);
            if (image == null) {
                return false;
            }
            return image.getWidth() == 64 && (image.getHeight() == 64 || image.getHeight() == 32);
        } catch (IOException e) {
            return false;
        }
    }

    private static Optional<Property> uploadSkinToMineskin(File skinFile, String skinName) {
        try {
            String reply = mineSkinUploadRequest(new URL("https://api.mineskin.org/v2/generate"), skinFile);
            if (reply == null || !reply.contains("\"value\":\"") || !reply.contains("\"signature\":\"")) {
                return Optional.empty();
            }
            String value = reply.split("\\\"value\\\":\\\"")[1].split("\\\"")[0];
            String signature = reply.split("\\\"signature\\\":\\\"")[1].split("\\\"")[0];
            return Optional.of(new Property(TEXTURE_PROPERTY, value, signature));
        } catch (Exception e) {
            SimpleFabricCustomSkin.LOGGER.warn("Mineskin upload failed for '{}': {}", skinName, e.getMessage());
            return Optional.empty();
        }
    }

    private static String mineSkinUploadRequest(URL url, File image) throws IOException {
        URLConnection connection = url.openConnection();
        if (!(connection instanceof HttpURLConnection http)) {
            return null;
        }

        http.setUseCaches(false);
        http.setDoOutput(true);
        http.setDoInput(true);
        http.setRequestMethod("POST");

        String boundary = UUID.randomUUID().toString();
        http.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        http.setRequestProperty("User-Agent", "SimpleFabricCustomSkin");

        var outputStream = http.getOutputStream();
        var writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);
        String line = "\r\n";

        writer.append("--").append(boundary).append(line);
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(image.getName()).append("\";");
        writer.append(" variant=\"classic\"").append(line);
        writer.append("Content-Type: image/png").append(line);
        writer.append("Content-Transfer-Encoding: binary").append(line);
        writer.append(line);
        writer.flush();

        byte[] fileBytes = Files.readAllBytes(image.toPath());
        outputStream.write(fileBytes);
        outputStream.flush();

        writer.append(line);
        writer.append("--").append(boundary).append("--").append(line);
        writer.flush();
        writer.close();

        if (http.getResponseCode() != HttpURLConnection.HTTP_OK) {
            return null;
        }

        try (InputStream is = http.getInputStream();
             InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
             Scanner scanner = new Scanner(isr)) {
            StringBuilder reply = new StringBuilder();
            while (scanner.hasNextLine()) {
                String row = scanner.nextLine();
                if (!row.trim().isEmpty()) {
                    reply.append(row);
                }
            }
            return reply.toString();
        } finally {
            http.disconnect();
        }
    }

    private static void setSkinProperty(ServerPlayer player, Property skinData, boolean reload) {
        Multimap<String, Property> properties = ArrayListMultimap.create(player.getGameProfile().properties());
        properties.removeAll(TEXTURE_PROPERTY);
        properties.put(TEXTURE_PROPERTY, skinData);

        ((PlayerAccessor) player).setGameProfile(new GameProfile(
                player.getGameProfile().id(),
                player.getGameProfile().name(),
                new PropertyMap(properties)
        ));

        if (reload) {
            reloadPlayerSkin(player);
        }
    }

    private static void clearSkinProperty(ServerPlayer player, boolean reload) {
        Multimap<String, Property> properties = ArrayListMultimap.create(player.getGameProfile().properties());
        properties.removeAll(TEXTURE_PROPERTY);
        Property original = ORIGINAL_SKINS.get(player.getUUID());
        if (original != null) {
            properties.put(TEXTURE_PROPERTY, original);
        }

        ((PlayerAccessor) player).setGameProfile(new GameProfile(
                player.getGameProfile().id(),
                player.getGameProfile().name(),
                new PropertyMap(properties)
        ));

        if (reload) {
            reloadPlayerSkin(player);
        }
    }

    private static void reloadPlayerSkin(ServerPlayer player) {
        if (player.level().getServer() == null) {
            return;
        }

        var server = player.level().getServer();
        var playerList = server.getPlayerList();

        playerList.broadcastAll(new ClientboundPlayerInfoRemovePacket(new ArrayList<>(Collections.singleton(player.getUUID()))));
        playerList.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(Collections.singleton(player)));

        var level = player.level();
        player.connection.send(new ClientboundRespawnPacket(
                new CommonPlayerSpawnInfo(
                        level.dimensionTypeRegistration(),
                        level.dimension(),
                        BiomeManager.obfuscateSeed(level.getSeed()),
                        player.gameMode.getGameModeForPlayer(),
                        player.gameMode.getPreviousGameModeForPlayer(),
                        level.isDebug(),
                        level.isFlat(),
                        player.getLastDeathLocation(),
                        player.getPortalCooldown(),
                        level.getSeaLevel()
                ),
                ClientboundRespawnPacket.KEEP_ALL_DATA
        ));
        player.connection.send(new ClientboundPlayerPositionPacket(0, net.minecraft.world.entity.PositionMoveRotation.of(player), Collections.emptySet()));
        player.connection.send(new ClientboundChangeDifficultyPacket(level.getDifficulty(), level.getLevelData().isDifficultyLocked()));
        player.connection.send(new ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));

        playerList.sendLevelInfo(player, level);
        playerList.sendPlayerPermissionLevel(player);
        playerList.sendAllPlayerInfo(player);
    }
}
