package tech.aomona.discordfabric;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.fabricmc.api.ModInitializer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.awt.*;
import java.util.EnumSet;
import java.util.logging.Logger;

public class Discordfabric implements ModInitializer {
    private static final Logger LOGGER = Logger.getLogger("DiscordFabric");
    private JDA discordApi;
    private MinecraftServer server;
    private TextChannel discordChannel;

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

                discordApi.awaitReady();
                discordChannel = discordApi.getTextChannelById(ModConfig.getDiscordChannelId());

                registerPlayerJoinEvent();
                registerPlayerDisconnectEvent();
                registerChatEvents();
                registerServerStoppedEvent();
                if (discordChannel != null) {
                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setTitle(":heart: サーバーが起動しました");
                    embed.setColor(Color.GREEN); // 色を設定
                    discordChannel.sendMessageEmbeds(embed.build()).queue();
                }
            } catch (Exception e) {
                System.out.println("Failed to start Discord bot: " + e.getMessage());
            }
        } else {
            System.out.println("Discord bot not started: No valid token and channelID found in config.");
            System.out.println("Please add your token and channelID to config/discordfabric.json");
        }
    }

    private void registerPlayerJoinEvent() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            String playerName = handler.getPlayer().getName().getString();
            String playerUUID = handler.getPlayer().getUuid().toString();
            if (discordApi != null) {
                try {
                    // 設定からチャンネルIDを取得する想定
                    if (discordChannel != null) {
                        EmbedBuilder embed = new EmbedBuilder();
                        embed.setAuthor(playerName+"さんが参加しました",null,"https://mc-heads.net/avatar/"+playerUUID);
                        embed.setColor(Color.GREEN); // 色を設定
                        discordChannel.sendMessageEmbeds(embed.build()).queue();
                    }
                } catch (Exception e) {
                    System.out.println("Failed to send message to Discord: " + e.getMessage());
                }
            }
        });
    }

    private void registerPlayerDisconnectEvent() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            String playerName = handler.getPlayer().getName().getString();
            String playerUUID = handler.getPlayer().getUuid().toString();
            if (discordApi != null) {
                try {
                    if (discordChannel != null) {
                        EmbedBuilder embed = new EmbedBuilder();
                        embed.setAuthor(playerName+"さんが退出しました",null,"https://mc-heads.net/avatar/"+playerUUID);
                        embed.setColor(Color.GRAY); // 色を設定
                        discordChannel.sendMessageEmbeds(embed.build()).queue();
                    }
                } catch (Exception e) {
                    System.out.println("Failed to send message to Discord: " + e.getMessage());
                }
            }
        });
    }

    private void registerServerStoppedEvent() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (discordApi != null) {
                try {
                    if (discordChannel != null) {
                        EmbedBuilder embed = new EmbedBuilder();
                        embed.setTitle(":broken_heart: サーバーが終了しました");
                        embed.setColor(Color.RED); // 色を設定
                        discordChannel.sendMessageEmbeds(embed.build()).queue();
                    }
                } catch (Exception e) {
                    System.out.println("Failed to send message to Discord: " + e.getMessage());
                }
                discordApi.shutdown();
            }
        });
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
                    if (discordChannel != null) {
                        discordChannel.sendMessage("**" + playerName + "**: " + content).queue();
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
