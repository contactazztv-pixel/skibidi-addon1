package com.example.addon.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * KeyPearl - Ném Ender Pearl khi nhấn phím, tự tìm pearl trong hotbar.
 * Phân tích từ: glazed/com/nnpg/glazed/modules/pvp/KeyPearl.class
 *
 * Logic:
 *   - Giữ phím được cấu hình -> tìm pearl trong hotbar -> switch -> throw -> switch back
 *   - Có cooldown để tránh spam
 */
public class KeyPearl extends Module {

    // ==================== SETTINGS ====================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    /** Hotbar slot của Pearl (-1 = tự tìm) */
    private final Setting<Integer> pearlSlot = sgGeneral.add(
        new IntSetting.Builder()
            .name("pearl-slot")
            .description("Hotbar slot to look for Ender Pearl (-1 = auto find).")
            .defaultValue(-1)
            .sliderRange(-1, 8)
            .build()
    );

    /** Giữ phím chuột phải để ném hay nhấn một lần */
    private final Setting<Boolean> holdMode = sgGeneral.add(
        new BoolSetting.Builder()
            .name("hold-mode")
            .description("Hold right-click to throw pearl, or press once.")
            .defaultValue(false)
            .build()
    );

    /** Cooldown giữa các lần ném (tick) */
    private final Setting<Integer> cooldown = sgGeneral.add(
        new IntSetting.Builder()
            .name("cooldown")
            .description("Cooldown in ticks between pearl throws.")
            .defaultValue(20)
            .sliderRange(1, 60)
            .build()
    );

    // ==================== INTERNAL STATE ====================
    private int  cooldownCounter     = 0;
    private boolean keyCurrentlyPressed = false;

    // ==================== CONSTRUCTOR ====================
    public KeyPearl() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "key-pearl",
            "Throws Ender Pearl on keybind press, auto-finds pearl in hotbar."
        );
    }

    // ==================== EVENT HANDLER ====================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        // Cooldown
        if (cooldownCounter > 0) {
            cooldownCounter--;
            return;
        }

        // Kiểm tra phím chuột phải đang được nhấn
        boolean rightClickPressed = mc.options.useKey.isPressed();

        if (holdMode.get()) {
            if (rightClickPressed) {
                throwPearl();
                cooldownCounter = cooldown.get();
            }
        } else {
            // Chỉ ném khi vừa nhấn (edge trigger)
            if (rightClickPressed && !keyCurrentlyPressed) {
                throwPearl();
                cooldownCounter = cooldown.get();
            }
        }
        keyCurrentlyPressed = rightClickPressed;
    }

    // ==================== HELPERS ====================

    /**
     * Tìm pearl trong hotbar và ném.
     */
    private void throwPearl() {
        int slot = pearlSlot.get() >= 0 ? pearlSlot.get() : findPearlInHotbar();

        if (slot == -1) {
            warning("§c[KeyPearl] No Ender Pearl found in hotbar!");
            return;
        }

        int prevSlot = mc.player.getInventory().selectedSlot;

        // Switch sang slot pearl
        mc.player.getInventory().selectedSlot = slot;

        // Verify có pearl
        if (mc.player.getInventory().getStack(slot).getItem() != Items.ENDER_PEARL) {
            mc.player.getInventory().selectedSlot = prevSlot;
            return;
        }

        // Ném pearl
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);

        // Trả về slot gốc
        mc.player.getInventory().selectedSlot = prevSlot;
    }

    /** Tìm Ender Pearl trong hotbar (0-8) */
    private int findPearlInHotbar() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.ENDER_PEARL) {
                return i;
            }
        }
        return -1;
    }
}
