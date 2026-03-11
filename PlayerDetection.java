package com.example.addon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * PlayerDetection - Phát hiện player vào world và gửi Discord webhook.
 * Phân tích từ: glazed/com/nnpg/glazed/modules/main/PlayerDetection.class
 *
 * Chức năng:
 *   - Theo dõi player list trên server
 *   - Cảnh báo khi player không trong whitelist xuất hiện
 *   - Gửi Discord webhook với thông tin player
 *   - Toggle module khác khi phát hiện
 *   - Tự disconnect (tùy chọn)
 */
public class PlayerDetection extends Module {

    // ==================== MODES ====================
    public enum Mode {
        Chat,       // Chỉ chat
        Sound,      // Chat + sound
        Both        // Chat + Sound + Webhook
    }

    // ==================== SETTING GROUPS ====================
    private final SettingGroup sgGeneral  = settings.getDefaultGroup();
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgWebhook  = settings.createGroup("Webhook");

    // ==================== SETTINGS - GENERAL ====================

    /** Thông báo chat khi phát hiện */
    private final Setting<Boolean> notifications = sgGeneral.add(
        new BoolSetting.Builder()
            .name("notifications")
            .description("Show chat feedback.")
            .defaultValue(true)
            .build()
    );

    /** Chế độ thông báo */
    private final Setting<Mode> notificationMode = sgGeneral.add(
        new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("How to notify when a player is detected.")
            .defaultValue(Mode.Chat)
            .build()
    );

    /** Tự disconnect khi phát hiện */
    private final Setting<Boolean> enableDisconnect = sgGeneral.add(
        new BoolSetting.Builder()
            .name("enable-disconnect")
            .description("Disconnect when a non-whitelisted player is detected.")
            .defaultValue(false)
            .build()
    );

    /** Toggle module khác khi phát hiện */
    private final Setting<List<Module>> modulesToToggle = sgGeneral.add(
        new ModuleListSetting.Builder()
            .name("modules-to-toggle")
            .description("Select modules to toggle when a non-whitelisted player is detected.")
            .build()
    );

    // ==================== SETTINGS - WHITELIST ====================

    /** Danh sách tên player bỏ qua */
    private final Setting<List<String>> userWhitelist = sgWhitelist.add(
        new StringListSetting.Builder()
            .name("user-whitelist")
            .description("List of player names to ignore.")
            .build()
    );

    // ==================== SETTINGS - WEBHOOK ====================

    /** Bật webhook Discord */
    private final Setting<Boolean> enableWebhook = sgWebhook.add(
        new BoolSetting.Builder()
            .name("enable-webhook")
            .description("Send webhook notifications when players are detected.")
            .defaultValue(false)
            .build()
    );

    /** URL Webhook Discord */
    private final Setting<String> webhookUrl = sgWebhook.add(
        new StringSetting.Builder()
            .name("webhook-url")
            .description("Discord webhook URL.")
            .defaultValue("https://discord.com/api/webhooks/...")
            .visible(enableWebhook::get)
            .build()
    );

    /** Ping bản thân trong webhook */
    private final Setting<Boolean> selfPing = sgWebhook.add(
        new BoolSetting.Builder()
            .name("self-ping")
            .description("Ping yourself in the webhook message.")
            .defaultValue(false)
            .visible(enableWebhook::get)
            .build()
    );

    /** Discord User ID để ping */
    private final Setting<String> discordId = sgWebhook.add(
        new StringSetting.Builder()
            .name("discord-id")
            .description("Your Discord user ID for pinging.")
            .defaultValue("000000000000000000")
            .visible(() -> enableWebhook.get() && selfPing.get())
            .build()
    );

    // ==================== INTERNAL STATE ====================
    // Whitelist cố định (không thể xóa qua GUI – ví dụ tên của bạn)
    private final Set<String> PERMANENT_WHITELIST = new HashSet<>();
    private final Set<String> detectedPlayers     = new HashSet<>();
    private final HttpClient  httpClient           = HttpClient.newHttpClient();
    private int               checkTick            = 0;

    // ==================== CONSTRUCTOR ====================
    public PlayerDetection() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "player-detection",
            "Detects when players are in the world."
        );
    }

    @Override
    public void onActivate() {
        detectedPlayers.clear();
        // Thêm tên của mình vào whitelist cố định
        if (mc.player != null) {
            PERMANENT_WHITELIST.add(mc.player.getName().getString());
        }
    }

    @Override
    public void onDeactivate() {
        detectedPlayers.clear();
    }

    // ==================== EVENT HANDLER ====================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Check mỗi 20 tick (1 giây) để tránh spam
        checkTick++;
        if (checkTick < 20) return;
        checkTick = 0;

        // Lấy danh sách player hiện tại
        Set<String> currentPlayers = new HashSet<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            currentPlayers.add(player.getName().getString());
        }

        // Tìm player mới (chưa phát hiện trước đó)
        for (String playerName : currentPlayers) {
            if (detectedPlayers.contains(playerName)) continue;
            if (isWhitelisted(playerName)) {
                detectedPlayers.add(playerName);
                continue;
            }

            // Phát hiện player không trong whitelist!
            detectedPlayers.add(playerName);
            onPlayerDetected(playerName);
        }

        // Xóa player đã rời đi
        detectedPlayers.removeIf(name -> !currentPlayers.contains(name));
    }

    // ==================== LOGIC ====================

    /** Xử lý khi phát hiện player */
    private void onPlayerDetected(String playerName) {
        if (notifications.get()) {
            warning("§c[PlayerDetection] §fDetected: §c" + playerName);
        }

        // Toggle modules
        for (Module module : modulesToToggle.get()) {
            module.toggle();
        }

        // Discord Webhook
        if (enableWebhook.get()) {
            sendWebhook(playerName);
        }

        // Disconnect
        if (enableDisconnect.get()) {
            mc.getNetworkHandler().getConnection().disconnect(
                net.minecraft.text.Text.of("PlayerDetection: " + playerName + " detected!")
            );
        }
    }

    /** Kiểm tra player có trong whitelist không */
    private boolean isWhitelisted(String name) {
        if (PERMANENT_WHITELIST.contains(name)) return true;
        for (String w : userWhitelist.get()) {
            if (w.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /**
     * Gửi Discord webhook notification.
     * Chạy trong thread riêng để không block game.
     */
    private void sendWebhook(String playerName) {
        String url = webhookUrl.get();
        if (url.isBlank() || url.equals("https://discord.com/api/webhooks/...")) return;

        String ping    = selfPing.get() ? "<@" + discordId.get() + "> " : "";
        String content = ping + "⚠️ **Player Detected!**\\nPlayer: `" + playerName
                       + "`\\nServer: `" + (mc.getCurrentServerEntry() != null
                            ? mc.getCurrentServerEntry().address : "unknown")
                       + "`";

        String json = "{\"content\":\"" + content.replace("\"", "\\\"") + "\"}";

        // Gửi async để không block game thread
        new Thread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                // Bỏ qua lỗi network
            }
        }, "PlayerDetection-Webhook").start();
    }
}
