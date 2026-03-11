package com.example.addon.modules.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * PillagerESP — ESP cho pillager với tracer, webhook notification, và info display.
 * Phân tích từ: glazed/com/nnpg/glazed/modules/esp/PillagerESP.class
 */
public class PillagerESP extends Module {

    // ═══════════════════════════════════════
    // MODES
    // ═══════════════════════════════════════
    public enum NotificationMode { Chat, Webhook, Both }
    public enum TracersMode      { ToEntity, Fixed }

    // ═══════════════════════════════════════
    // SETTING GROUPS
    // ═══════════════════════════════════════
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgESP     = settings.createGroup("ESP");
    private final SettingGroup sgTracers = settings.createGroup("Tracers");
    private final SettingGroup sgWebhook = settings.createGroup("Webhook");

    // ═══════════════════════════════════════
    // SETTINGS — General
    // ═══════════════════════════════════════
    private final Setting<Integer> maxDistance = sgGeneral.add(
        new IntSetting.Builder()
            .name("max-distance")
            .description("Maximum distance to render pillagers.")
            .defaultValue(100)
            .range(1, 512)
            .sliderRange(10, 256)
            .build()
    );

    private final Setting<Boolean> showCount = sgGeneral.add(
        new BoolSetting.Builder()
            .name("show-count")
            .description("Show pillager count in chat.")
            .defaultValue(true)
            .build()
    );

    private final Setting<NotificationMode> notificationMode = sgGeneral.add(
        new EnumSetting.Builder<NotificationMode>()
            .name("notification-mode")
            .description("How to notify when pillagers are detected.")
            .defaultValue(NotificationMode.Chat)
            .build()
    );

    private final Setting<Boolean> toggleOnFind = sgGeneral.add(
        new BoolSetting.Builder()
            .name("toggle-when-found")
            .description("Automatically toggles the module when pillagers are detected.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> enableDisconnect = sgGeneral.add(
        new BoolSetting.Builder()
            .name("disconnect")
            .description("Automatically disconnect when pillagers are detected.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(
        new BoolSetting.Builder()
            .name("notifications")
            .description("Show chat feedback.")
            .defaultValue(true)
            .build()
    );

    // ═══════════════════════════════════════
    // SETTINGS — ESP
    // ═══════════════════════════════════════
    private final Setting<SettingColor> espColor = sgESP.add(
        new ColorSetting.Builder()
            .name("esp-color")
            .description("Color of pillager ESP.")
            .defaultValue(new SettingColor(255, 50, 50, 150))
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgESP.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the ESP shapes are rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    // ═══════════════════════════════════════
    // SETTINGS — Tracers
    // ═══════════════════════════════════════
    private final Setting<Boolean> tracersEnabled = sgTracers.add(
        new BoolSetting.Builder()
            .name("tracers")
            .description("Draw tracers to pillagers.")
            .defaultValue(true)
            .build()
    );

    private final Setting<SettingColor> tracerColor = sgTracers.add(
        new ColorSetting.Builder()
            .name("tracer-color")
            .description("Pillager tracer color.")
            .defaultValue(new SettingColor(255, 100, 100, 200))
            .visible(tracersEnabled::get)
            .build()
    );

    // ═══════════════════════════════════════
    // SETTINGS — Webhook
    // ═══════════════════════════════════════
    private final Setting<Boolean> enableWebhook = sgWebhook.add(
        new BoolSetting.Builder()
            .name("enable-webhook")
            .description("Send Discord webhook when pillagers are detected.")
            .defaultValue(false)
            .build()
    );

    private final Setting<String> webhookUrl = sgWebhook.add(
        new StringSetting.Builder()
            .name("webhook-url")
            .description("Discord webhook URL.")
            .defaultValue("https://discord.com/api/webhooks/...")
            .visible(enableWebhook::get)
            .build()
    );

    private final Setting<Boolean> selfPing = sgWebhook.add(
        new BoolSetting.Builder()
            .name("self-ping")
            .description("Ping yourself in the webhook message.")
            .defaultValue(false)
            .visible(enableWebhook::get)
            .build()
    );

    private final Setting<String> discordId = sgWebhook.add(
        new StringSetting.Builder()
            .name("discord-id")
            .description("Your Discord user ID for pinging.")
            .defaultValue("000000000000000000")
            .visible(() -> enableWebhook.get() && selfPing.get())
            .build()
    );

    // ═══════════════════════════════════════
    // INTERNAL STATE
    // ═══════════════════════════════════════
    private final List<RaiderEntity> pillagers       = new ArrayList<>();
    private final Set<Integer>       detectedPillagers = new HashSet<>();
    private int                      lastPillagerCount = 0;
    private final HttpClient         httpClient        = HttpClient.newHttpClient();
    private int                      tickCounter       = 0;

    // ═══════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════
    public PillagerESP() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "pillager-esp",
            "ESP for pillagers with tracers, webhook notifications and info display."
        );
    }

    @Override
    public void onActivate() {
        pillagers.clear();
        detectedPillagers.clear();
        lastPillagerCount = 0;
    }

    // ═══════════════════════════════════════
    // EVENT HANDLERS
    // ═══════════════════════════════════════

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;
        if (tickCounter < 10) return; // Check mỗi 10 tick
        tickCounter = 0;

        pillagers.clear();

        // Tìm tất cả pillager trong tầm
        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof RaiderEntity raider)) continue;
            if (!raider.isAlive()) continue;
            if (mc.player.distanceTo(raider) > maxDistance.get()) continue;
            pillagers.add(raider);
        }

        int count = pillagers.size();

        // Phát hiện pillager mới
        for (var pillager : pillagers) {
            int id = pillager.getId();
            if (!detectedPillagers.contains(id)) {
                detectedPillagers.add(id);
                onNewPillagerDetected(pillager, count);
            }
        }

        // Xóa pillager đã biến mất
        detectedPillagers.removeIf(id ->
            pillagers.stream().noneMatch(p -> p.getId() == id)
        );

        // Thông báo count thay đổi
        if (count != lastPillagerCount && showCount.get() && count > 0) {
            info("§c[PillagerESP] §f" + count + " pillager(s) within range.");
            lastPillagerCount = count;
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        for (var pillager : pillagers) {
            if (!pillager.isAlive()) continue;

            // Vẽ ESP box
            Box box = pillager.getBoundingBox();
            event.renderer.box(
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                espColor.get(), espColor.get(),
                shapeMode.get(), 0
            );

            // Vẽ tracer
            if (tracersEnabled.get()) {
                Vec3d center = pillager.getPos().add(0, pillager.getHeight() / 2, 0);
                event.renderer.line(
                    mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(),
                    center.x, center.y, center.z,
                    tracerColor.get()
                );
            }
        }
    }

    // ═══════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════

    private void onNewPillagerDetected(RaiderEntity pillager, int totalCount) {
        String pos = String.format("%.0f, %.0f, %.0f",
            pillager.getX(), pillager.getY(), pillager.getZ());

        if (notifications.get() &&
            (notificationMode.get() == NotificationMode.Chat || notificationMode.get() == NotificationMode.Both)) {
            warning("§c[PillagerESP] §fNew pillager detected at §c" + pos + " §f(Total: " + totalCount + ")");
        }

        if (enableWebhook.get() &&
            (notificationMode.get() == NotificationMode.Webhook || notificationMode.get() == NotificationMode.Both)) {
            sendWebhook(pos, totalCount);
        }

        if (enableDisconnect.get()) {
            mc.getNetworkHandler().getConnection().disconnect(
                net.minecraft.text.Text.of("PillagerESP: Pillager detected!")
            );
        }

        if (toggleOnFind.get()) toggle();
    }

    private void sendWebhook(String pos, int count) {
        String url = webhookUrl.get();
        if (url.isBlank() || url.startsWith("https://discord.com/api/webhooks/...")) return;

        String ping    = selfPing.get() ? "<@" + discordId.get() + "> " : "";
        String content = ping + "⚔️ **Pillager Detected!**\\nPosition: `" + pos
                       + "`\\nTotal nearby: `" + count + "`";
        String json    = "{\"content\":\"" + content.replace("\"", "\\\"") + "\"}";

        new Thread(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
                httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ignored) {}
        }, "PillagerESP-Webhook").start();
    }
}
