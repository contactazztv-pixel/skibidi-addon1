package com.example.addon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * SpawnerProtect - Khi phát hiện player lạ, tự động đào spawner và cất vào inventory.
 * Phân tích từ: glazed/com/nnpg/glazed/modules/main/SpawnerProtect.class
 *
 * Logic (state machine):
 *   IDLE       -> Chờ player lạ xuất hiện trong tầm
 *   DETECTING  -> Xác nhận player (anti-flicker)
 *   MINING     -> Đào spawner gần nhất
 *   COLLECTING -> Thu thập item drop
 *   DONE       -> Hoàn thành, reset
 */
public class SpawnerProtect extends Module {

    // ==================== STATES ====================
    private enum State { IDLE, DETECTING, MINING, COLLECTING, DONE }

    // ==================== SETTING GROUPS ====================
    private final SettingGroup sgGeneral   = settings.getDefaultGroup();
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgWebhook   = settings.createGroup("Webhook");

    // ==================== SETTINGS ====================

    private final Setting<Integer> spawnerRange = sgGeneral.add(
        new IntSetting.Builder()
            .name("spawner-range")
            .description("Range (blocks) to search for spawners.")
            .defaultValue(16)
            .sliderRange(4, 32)
            .build()
    );

    private final Setting<Integer> minDetectionRange = sgGeneral.add(
        new IntSetting.Builder()
            .name("min-detection-range")
            .description("Minimum range to detect players (blocks).")
            .defaultValue(10)
            .sliderRange(1, 64)
            .build()
    );

    private final Setting<Integer> maxDetectionRange = sgGeneral.add(
        new IntSetting.Builder()
            .name("max-detection-range")
            .description("Maximum range to detect players (blocks).")
            .defaultValue(30)
            .sliderRange(5, 128)
            .build()
    );

    private final Setting<Integer> spawnerCheckDelay = sgGeneral.add(
        new IntSetting.Builder()
            .name("spawner-check-delay")
            .description("Ticks between spawner checks.")
            .defaultValue(5)
            .sliderRange(1, 20)
            .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(
        new BoolSetting.Builder()
            .name("notifications")
            .description("Send chat notifications.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> enableWhitelist = sgWhitelist.add(
        new BoolSetting.Builder()
            .name("enable-whitelist")
            .description("Enable player whitelist.")
            .defaultValue(false)
            .build()
    );

    private final Setting<List<String>> whitelistPlayers = sgWhitelist.add(
        new StringListSetting.Builder()
            .name("whitelist-players")
            .description("Players to ignore.")
            .visible(enableWhitelist::get)
            .build()
    );

    private final Setting<Boolean> webhook = sgWebhook.add(
        new BoolSetting.Builder()
            .name("webhook")
            .description("Enable webhook notifications.")
            .defaultValue(false)
            .build()
    );

    private final Setting<String> webhookUrl = sgWebhook.add(
        new StringSetting.Builder()
            .name("webhook-url")
            .description("Discord webhook URL for notifications.")
            .defaultValue("https://discord.com/api/webhooks/...")
            .visible(webhook::get)
            .build()
    );

    // ==================== INTERNAL STATE ====================
    private State   currentState     = State.IDLE;
    private String  detectedPlayer   = null;
    private long    detectionTime    = 0;
    private BlockPos currentTarget   = null;
    private int     tickCounter      = 0;
    private int     miningTicks      = 0;

    // ==================== CONSTRUCTOR ====================
    public SpawnerProtect() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "spawner-protect",
            "Breaks spawners and stores them when a player is detected nearby."
        );
    }

    @Override
    public void onActivate() {
        currentState  = State.IDLE;
        detectedPlayer = null;
        currentTarget = null;
        tickCounter   = 0;
    }

    // ==================== EVENT HANDLER ====================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        tickCounter++;

        switch (currentState) {

            case IDLE -> {
                if (tickCounter % spawnerCheckDelay.get() != 0) return;

                // Scan player trong tầm
                String threat = scanForThreat();
                if (threat != null) {
                    detectedPlayer = threat;
                    detectionTime  = System.currentTimeMillis();
                    currentState   = State.DETECTING;
                    if (notifications.get()) {
                        warning("§c[SpawnerProtect] §fDetected player: §c" + threat + " §f- Protecting spawners!");
                    }
                }
            }

            case DETECTING -> {
                // Chờ 1 giây để tránh false positive
                if (System.currentTimeMillis() - detectionTime < 1000) return;

                // Tìm spawner gần nhất
                currentTarget = findNearestSpawner();
                if (currentTarget == null) {
                    if (notifications.get()) info("§e[SpawnerProtect] No spawners found nearby.");
                    currentState = State.IDLE;
                    return;
                }
                currentState = State.MINING;
                miningTicks  = 0;
                if (notifications.get()) info("§a[SpawnerProtect] Mining spawner at " + currentTarget);
            }

            case MINING -> {
                if (currentTarget == null) { currentState = State.IDLE; return; }

                // Đào spawner (packet-based)
                mineBlock(currentTarget);
                miningTicks++;

                // Kiểm tra spawner đã biến mất
                if (mc.world.getBlockState(currentTarget).getBlock() != Blocks.SPAWNER) {
                    if (notifications.get()) info("§a[SpawnerProtect] Spawner collected!");
                    currentState = State.COLLECTING;
                    tickCounter  = 0;
                }
            }

            case COLLECTING -> {
                // Chờ 40 tick để item nổi lên và tự pickup
                if (tickCounter >= 40) {
                    // Tìm spawner tiếp theo
                    currentTarget = findNearestSpawner();
                    if (currentTarget != null) {
                        currentState = State.MINING;
                        miningTicks  = 0;
                    } else {
                        currentState = State.DONE;
                    }
                }
            }

            case DONE -> {
                if (notifications.get()) info("§a[SpawnerProtect] All spawners protected.");
                currentState = State.IDLE;
            }
        }
    }

    // ==================== HELPERS ====================

    /** Scan player trong khoảng [minRange, maxRange] */
    private String scanForThreat() {
        if (mc.player == null) return null;

        for (net.minecraft.entity.player.PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            String name = player.getName().getString();

            // Kiểm tra whitelist
            if (enableWhitelist.get() && isWhitelisted(name)) continue;

            double dist = mc.player.distanceTo(player);
            if (dist >= minDetectionRange.get() && dist <= maxDetectionRange.get()) {
                return name;
            }
        }
        return null;
    }

    /** Tìm spawner gần nhất trong range */
    private BlockPos findNearestSpawner() {
        if (mc.player == null) return null;

        BlockPos closest = null;
        double   minDist = Double.MAX_VALUE;

        BlockPos center = mc.player.getBlockPos();
        int      range  = spawnerRange.get();

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() == Blocks.SPAWNER) {
                        double d = mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (d < minDist) {
                            minDist = d;
                            closest = pos;
                        }
                    }
                }
            }
        }
        return closest;
    }

    /** Gửi packet đào block */
    private void mineBlock(BlockPos pos) {
        if (mc.interactionManager == null) return;

        // Start và stop mine mỗi tick để simulate instant mine với Silk Touch (nếu có)
        mc.interactionManager.attackBlock(pos, net.minecraft.util.math.Direction.DOWN);
    }

    private boolean isWhitelisted(String name) {
        for (String w : whitelistPlayers.get()) {
            if (w.equalsIgnoreCase(name)) return true;
        }
        return false;
    }
}
