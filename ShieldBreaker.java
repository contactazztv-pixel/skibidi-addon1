package com.example.addon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * ShieldBreaker - Tự động phá shield bằng rìu rồi quay lại vũ khí.
 * Phân tích từ: glazed/com/nnpg/glazed/modules/pvp/ShieldBreaker.class
 *
 * Logic:
 *   1. Phát hiện player đang dùng shield trong tầm
 *   2. Switch sang rìu trong hotbar
 *   3. Đánh để phá shield
 *   4. Switch ngược về vũ khí gốc
 *   5. Tấn công tiếp (nếu killSwitch bật)
 */
public class ShieldBreaker extends Module {

    // ==================== STATES ====================
    private enum ShieldBreakerState {
        IDLE,           // Chờ đợi
        SWITCH_TO_AXE,  // Đang switch sang rìu
        BREAK_SHIELD,   // Đang đánh shield
        SWITCH_BACK,    // Đang switch về vũ khí gốc
        KILL_ATTACK     // Tấn công sau khi phá shield
    }

    // ==================== SETTING GROUPS ====================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ==================== SETTINGS ====================

    /** Tự động phá không cần click */
    private final Setting<Boolean> autoBreak = sgGeneral.add(
        new BoolSetting.Builder()
            .name("auto-break")
            .description("Automatically break shields without requiring clicks.")
            .defaultValue(true)
            .build()
    );

    /** Quay về slot trước đó hay slot cụ thể */
    private final Setting<Boolean> returnToPrevSlot = sgGeneral.add(
        new BoolSetting.Builder()
            .name("return-to-prev-slot")
            .description("Return to the previous slot after breaking shield instead of a specific weapon slot.")
            .defaultValue(true)
            .build()
    );

    /** Slot vũ khí cụ thể (1-9) nếu không dùng return-to-prev */
    private final Setting<Integer> weaponSlot = sgGeneral.add(
        new IntSetting.Builder()
            .name("weapon-slot")
            .description("The hotbar slot to switch back to after breaking shield (1-9).")
            .defaultValue(1)
            .sliderRange(1, 9)
            .visible(() -> !returnToPrevSlot.get())
            .build()
    );

    /** Delay trước khi đánh sau khi switch rìu (tick) */
    private final Setting<Integer> axeSwitchDelay = sgGeneral.add(
        new IntSetting.Builder()
            .name("axe-switch-delay")
            .description("Delay in ticks to ensure axe switch is completed.")
            .defaultValue(2)
            .sliderRange(0, 10)
            .build()
    );

    /** Delay giữa đánh shield và switch ngược (tick) */
    private final Setting<Integer> attackDelay = sgGeneral.add(
        new IntSetting.Builder()
            .name("attack-delay")
            .description("Delay in ticks between shield break and weapon switch.")
            .defaultValue(1)
            .sliderRange(0, 10)
            .build()
    );

    /** Delay giữa switch vũ khí và kill attack (tick) */
    private final Setting<Integer> killDelay = sgGeneral.add(
        new IntSetting.Builder()
            .name("kill-delay")
            .description("Delay in ticks between weapon switch and kill attack.")
            .defaultValue(2)
            .sliderRange(0, 10)
            .build()
    );

    /** Cooldown giữa các chu kỳ phá shield */
    private final Setting<Integer> cycleCooldown = sgGeneral.add(
        new IntSetting.Builder()
            .name("cycle-cooldown")
            .description("Delay in ticks before starting the next shield break cycle.")
            .defaultValue(10)
            .sliderRange(0, 40)
            .build()
    );

    /** Chỉ target player */
    private final Setting<Boolean> onlyPlayers = sgGeneral.add(
        new BoolSetting.Builder()
            .name("only-players")
            .description("Only break shields of players, not other entities.")
            .defaultValue(true)
            .build()
    );

    /** Tầm phát hiện */
    private final Setting<Double> range = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("range")
            .description("Maximum range to detect shield usage.")
            .defaultValue(4.5)
            .sliderRange(1, 8)
            .build()
    );

    /** Tự động tấn công sau khi phá shield */
    private final Setting<Boolean> killSwitch = sgGeneral.add(
        new BoolSetting.Builder()
            .name("kill-switch")
            .description("Enable auto attack after breaking shield.")
            .defaultValue(true)
            .build()
    );

    /** Thông báo chat */
    private final Setting<Boolean> chatInfo = sgGeneral.add(
        new BoolSetting.Builder()
            .name("chat-info")
            .description("Send info messages to chat.")
            .defaultValue(false)
            .build()
    );

    // ==================== INTERNAL STATE ====================
    private ShieldBreakerState state = ShieldBreakerState.IDLE;
    private PlayerEntity       targetPlayer;
    private int                originalSlot      = -1;
    private int                tickCounter       = 0;
    private boolean            shieldBroken      = false;
    private int                cooldownTicks     = 0;

    // ==================== CONSTRUCTOR ====================
    public ShieldBreaker() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "shield-breaker",
            "Automatically breaks player shields with axe then switches back to weapon for kill."
        );
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    // ==================== EVENT HANDLERS ====================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Đang cooldown giữa chu kỳ
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        switch (state) {

            case IDLE -> {
                // Tìm player đang dùng shield
                targetPlayer = findShieldingPlayer();
                if (targetPlayer == null) return;

                // Lưu slot hiện tại
                originalSlot = mc.player.getInventory().selectedSlot;

                // Tìm rìu trong hotbar
                int axeSlot = findAxeInHotbar();
                if (axeSlot == -1) return; // Không có rìu

                // Switch sang rìu
                mc.player.getInventory().selectedSlot = axeSlot;
                tickCounter = 0;
                state = ShieldBreakerState.SWITCH_TO_AXE;
            }

            case SWITCH_TO_AXE -> {
                tickCounter++;
                if (tickCounter >= axeSwitchDelay.get()) {
                    // Đánh để phá shield
                    if (targetPlayer != null && isAlive(targetPlayer)) {
                        mc.interactionManager.attackEntity(mc.player, targetPlayer);
                        mc.player.swingHand(Hand.MAIN_HAND);
                        shieldBroken = true;
                        tickCounter = 0;
                        state = ShieldBreakerState.BREAK_SHIELD;
                    } else {
                        resetState();
                    }
                }
            }

            case BREAK_SHIELD -> {
                tickCounter++;
                if (tickCounter >= attackDelay.get()) {
                    // Switch ngược về vũ khí
                    int targetSlot = returnToPrevSlot.get() ? originalSlot : (weaponSlot.get() - 1);
                    mc.player.getInventory().selectedSlot = targetSlot;
                    tickCounter = 0;
                    state = killSwitch.get() ? ShieldBreakerState.SWITCH_BACK : ShieldBreakerState.IDLE;
                    if (state == ShieldBreakerState.IDLE) startCooldown();
                }
            }

            case SWITCH_BACK -> {
                tickCounter++;
                if (tickCounter >= killDelay.get()) {
                    // Tấn công kill
                    if (targetPlayer != null && isAlive(targetPlayer)) {
                        mc.interactionManager.attackEntity(mc.player, targetPlayer);
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                    startCooldown();
                    state = ShieldBreakerState.IDLE;
                }
            }
        }
    }

    // ==================== HELPERS ====================

    /** Tìm player đang cầm shield trong tầm */
    private PlayerEntity findShieldingPlayer() {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (!isAlive(player)) continue;
            if (mc.player.distanceTo(player) > range.get()) continue;

            // Kiểm tra có đang dùng shield không
            if (player.isBlocking()) return player;
        }
        return null;
    }

    /** Tìm rìu trong hotbar (slot 0-8) */
    private int findAxeInHotbar() {
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof AxeItem) return i;
        }
        return -1;
    }

    private boolean isAlive(PlayerEntity player) {
        return player != null && player.isAlive() && !player.isDead();
    }

    private void resetState() {
        state        = ShieldBreakerState.IDLE;
        targetPlayer = null;
        originalSlot = -1;
        tickCounter  = 0;
        shieldBroken = false;
    }

    private void startCooldown() {
        cooldownTicks = cycleCooldown.get();
    }
}
