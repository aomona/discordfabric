package tech.aomona.discordfabric;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.fabricmc.api.ModInitializer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.util.EnumSet;
import java.util.logging.Logger;

public class Discordfabric implements ModInitializer {
    private static final Logger LOGGER = Logger.getLogger("DiscordFabric");
    private JDA discordApi;
    private MinecraftServer server;

    @Override
    public void onInitialize() {
        // 設定を読み込む
        ModConfig.load();

        // サーバーのインスタンスを取得するためのイベント登録
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            this.server = server;
        });

        // トークンとチャンネルIDがあるか確認
        if (ModConfig.hasValidTokenandChannelId()) {
            try {
                // Discord botを初期化して、イベントリスナーを登録
                discordApi = JDABuilder.createDefault(ModConfig.getDiscordToken(), EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT))
                        .setActivity(Activity.playing("Minecraft"))
                        .addEventListeners(new DiscordListener())
                        .build();

                System.out.println("Discord bot started successfully");


                // チャットメッセージイベントを登録
                registerChatEvents();

            } catch (Exception e) {
                System.out.println("Failed to start Discord bot: " + e.getMessage());
            }
        } else {
            System.out.println("Discord bot not started: No valid token and channelID found in config.");
            System.out.println("Please add your token and channelID to config/discordfabric.json");

            // Discord連携なしでもチャットイベントは登録
            registerChatEvents();
        }
    }

    private void registerChatEvents() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            // メッセージの内容を取得
            String content = message.getContent().getString();

            // プレイヤー名を取得
            String playerName = sender.getName().getString();

            // Discordに送信（APIが初期化されている場合）
            if (discordApi != null) {
                try {
                    // 設定からチャンネルIDを取得する想定
                    String channelId = ModConfig.getDiscordChannelId();
                    TextChannel channel = discordApi.getTextChannelById(channelId);

                    if (channel != null) {
                        channel.sendMessage("**" + playerName + "**: " + content).queue();
                    }
                } catch (Exception e) {
                    System.out.println("Failed to send message to Discord: " + e.getMessage());
                }
            }
        });
    }

    // Discordからのメッセージを処理するためのリスナークラス
    private class DiscordListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            // ボット自身のメッセージは無視
            if (event.getAuthor().isBot()) return;

            // 指定されたチャンネルからのメッセージのみ処理
            if (event.getChannel().getId().equals(ModConfig.getDiscordChannelId())) {
                String username = event.getAuthor().getName();
                String message = event.getMessage().getContentDisplay();

                // MinecraftサーバーにDiscordからのメッセージを転送
                if (server != null) {
                    Text discordMessage = Text.of("§9[Discord] §b" + username + "§f: " + message);
                    server.getPlayerManager().broadcast(discordMessage, false);
                }
            }
        }
    }
}
