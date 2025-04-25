package tech.aomona.discordfabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class ModConfig {
    private static final Logger LOGGER = Logger.getLogger("DiscordFabric");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("discordfabric.json");

    private static ConfigData config;

    public static class ConfigData {
        public String discordToken = "";
        public String ChannelId = "";
    }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                config = GSON.fromJson(json, ConfigData.class);
                if (config.discordToken == null || config.discordToken.isEmpty()) {
                    System.out.println("Discord token not found in config file. Bot will not start.");
                }
            } else {
                // 初期設定ファイルを作成（空のトークンで）
                config = new ConfigData();
                save();
                System.out.println("Config file created at " + CONFIG_PATH + ". Please add your Discord token.");
            }
        } catch (IOException e) {
            System.out.println("Failed to load config: " + e.getMessage());
            config = new ConfigData(); // デフォルト設定を使用
        }
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            String json = GSON.toJson(config);
            Files.writeString(CONFIG_PATH, json);
        } catch (IOException e) {
            System.out.println("Failed to save config: " + e.getMessage());
        }
    }

    public static String getDiscordToken() {
        return config != null ? config.discordToken : "";
    }

    public static String getDiscordChannelId() {
        return config != null ? config.ChannelId : "";
    }

    public static boolean hasValidTokenandChannelId() {
        return config != null && config.discordToken != null && !config.discordToken.isEmpty() && config.ChannelId != null && !config.ChannelId.isEmpty();
    }
}
