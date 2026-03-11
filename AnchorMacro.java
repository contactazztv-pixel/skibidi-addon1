package com.example.addon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * AnchorMacro - Tự động đặt và kích hoạt Respawn Anchor trong Nether PvP.
 * Phân tích từ: glazed/com/nnpg/glazed/modules/pvp/AnchorMacro.class
 *
 * Logic:
 *   1. Đặt Respawn Anchor tại vị trí mục tiêu (dưới chân enemy)
 *   2. Charge anchor bằng Glowstone
 *   3. Click lần 2 để kích nổ (chỉ nổ trong Overworld/End)
 */
public class AnchorMacro extends Module {

    // ==================== SETTINGS ====================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    /** Slot Respawn Anchor trong hotbar (0-8, -1 = tự tìm) */
    private final Setting<Integer> anchorSlot = sgGeneral.add(
        new IntSetting.Builder()
            .name("anchor-slot")
            .description("Hotbar slot of Respawn Anchor (-1 = auto find).")
            .defaultValue(-1)
            .sliderRange(-1, 8)
            .build()
    );

    /** Slot Glowstone trong hotbar (0-8, -1 = tự tìm) */
    private final Setting<Integer> glowstoneSlot = sgGeneral.add(
        new IntSetting.Builder()
            .name("glowstone-slot")
            .description("Hotbar slot of Glowstone (-1 = auto find).")
            .defaultValue(-1)
            .sliderRange(-1, 8)
            .build()
    );

    /** Delay giữa các bước (tick) */
    private final Setting<Integer> stepDelay = sgGeneral.add(
        new IntSetting.Builder()
            .name("step-delay")
            .description("Delay in ticks between each step.")
            .defaultValue(2)
            .sliderRange(1, 10)
            .build()
    );

    /** Tự toggle off sau khi thực hiện */
    private final Setting<Boolean> autoDisable = sgGeneral.add(
        new BoolSetting.Builder()
            .name("auto-disable")
            .description("Automatically disable after activation.")
            .defaultValue(true)
            .build()
    );

    // ==================== INTERNAL STATE ====================
    private enum State { IDLE, PLACE_ANCHOR, CHARGE, DETONATE }
    private State state         = State.IDLE;
    private int   tickCounter   = 0;
    private BlockPos anchorPos  = null;
    private int   prevSlot      = 0;

    // ==================== CONSTRUCTOR ====================
    public AnchorMacro() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "anchor-macro",
            "Places and detonates Respawn Anchor for Nether PvP."
        );
    }

    @Override
    public void onActivate() {
        state = State.PLACE_ANCHOR;
        tickCounter = 0;
        if (mc.player != null) prevSlot = mc.player.getInventory().selectedSlot;
    }

    @Override
    public void onDeactivate() {
        state = State.IDLE;
    }

    // ==================== EVENT HANDLER ====================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;
        if (tickCounter < stepDelay.get()) return;
        tickCounter = 0;

        switch (state) {

            case PLACE_ANCHOR -> {
                int aSlot = anchorSlot.get() >= 0 ? anchorSlot.get() : findInHotbar(Items.RESPAWN_ANCHOR);
                if (aSlot == -1) {
                    error("No Respawn Anchor found in hotbar.");
                    toggle();
                    return;
                }

                // Đặt anchor dưới chân player
                anchorPos = mc.player.getBlockPos().down();
                BlockPos supportPos = anchorPos.down();

                // Cần block dưới để đặt
                if (mc.world.getBlockState(anchorPos).isAir()
                    && !mc.world.getBlockState(supportPos).isAir()) {

                    int prev = mc.player.getInventory().selectedSlot;
                    mc.player.getInventory().selectedSlot = aSlot;
                    mc.interactionManager.interactBlock(
                        mc.player, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(supportPos), Direction.UP, supportPos, false)
                    );
                    mc.player.swingHand(Hand.MAIN_HAND);
                    mc.player.getInventory().selectedSlot = prev;
                    state = State.CHARGE;
                } else {
                    error("Cannot place anchor here.");
                    toggle();
                }
            }

            case CHARGE -> {
                if (anchorPos == null) { toggle(); return; }
                // Kiểm tra anchor đã đặt chưa
                if (mc.world.getBlockState(anchorPos).getBlock() != Blocks.RESPAWN_ANCHOR) {
                    error("Anchor not placed.");
                    toggle();
                    return;
                }

                int gSlot = glowstoneSlot.get() >= 0 ? glowstoneSlot.get() : findInHotbar(Items.GLOWSTONE);
                if (gSlot == -1) {
                    error("No Glowstone found in hotbar.");
                    toggle();
                    return;
                }

                // Charge anchor (right-click với glowstone)
                int prev = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = gSlot;
                mc.interactionManager.interactBlock(
                    mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(anchorPos), Direction.UP, anchorPos, false)
                );
                mc.player.swingHand(Hand.MAIN_HAND);
                mc.player.getInventory().selectedSlot = prev;
                state = State.DETONATE;
            }

            case DETONATE -> {
                if (anchorPos == null) { toggle(); return; }
                // Kích nổ anchor (right-click lần 2 không dùng glowstone)
                int prev = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = 0; // bất kỳ item nào không phải glowstone
                mc.interactionManager.interactBlock(
                    mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(anchorPos), Direction.UP, anchorPos, false)
                );
                mc.player.swingHand(Hand.MAIN_HAND);
                mc.player.getInventory().selectedSlot = prevSlot;

                if (autoDisable.get()) toggle();
                state = State.IDLE;
            }
        }
    }

    // ==================== HELPERS ====================
    private int findInHotbar(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }
}
