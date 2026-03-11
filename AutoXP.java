package com.example.addon.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * AutoXP - Tự động chuyển XP bottle từ inventory vào hotbar slot.
 * Phân tích từ: zinc/com/vrzt/zinc/modules/Zinc/AutoXP.class
 *
 * Logic:
 *   - Theo dõi hotbar slot được chọn
 *   - Khi slot đó hết XP bottle, tìm trong inventory và swap vào
 *   - Thông báo khi XP bottle được refill
 */
public class AutoXP extends Module {

    // ==================== SETTINGS ====================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    /** Hotbar slot để giữ XP bottle (0-8) */
    private final Setting<Integer> targetSlot = sgGeneral.add(
        new IntSetting.Builder()
            .name("target-slot")
            .description("Hotbar slot to keep XP bottles in (0 = slot 1).")
            .defaultValue(7)
            .sliderRange(0, 8)
            .build()
    );

    /** Thông báo khi refill */
    private final Setting<Boolean> alertOnMove = sgGeneral.add(
        new BoolSetting.Builder()
            .name("alert-on-move")
            .description("Send a chat message when XP bottles are moved/refilled into hotbar.")
            .defaultValue(false)
            .build()
    );

    // ==================== CONSTRUCTOR ====================
    public AutoXP() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "auto-xp",
            "Automatically moves XP bottles to a hotbar slot when it runs out."
        );
    }

    // ==================== EVENT HANDLER ====================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        int slot = targetSlot.get();

        // Kiểm tra hotbar slot có XP bottle không
        var stack = mc.player.getInventory().getStack(slot);
        if (stack.getItem() == Items.EXPERIENCE_BOTTLE) return; // Đã có, không cần làm gì

        // Tìm XP bottle trong inventory (slot 9-35)
        int xpSlot = findXPBottleInInventory();
        if (xpSlot == -1) return; // Không có XP bottle trong inventory

        // Move XP bottle vào hotbar slot
        moveToHotbar(xpSlot, slot);

        if (alertOnMove.get()) {
            info("§7[§aAutoXP§7] §fRefilled XP bottles into slot " + (slot + 1));
        }
    }

    // ==================== HELPERS ====================

    /** Tìm XP bottle trong inventory (9-35), trả về slot index hoặc -1 */
    private int findXPBottleInInventory() {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.EXPERIENCE_BOTTLE) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Move item từ inventory slot sang hotbar slot.
     * Dùng SlotActionType.SWAP.
     *
     * @param invSlot    Source slot trong inventory (9-35)
     * @param hotbarSlot Target hotbar slot (0-8)
     */
    private void moveToHotbar(int invSlot, int hotbarSlot) {
        if (mc.currentScreen != null) return;

        int syncId = mc.player.playerScreenHandler.syncId;

        // SWAP action: button = hotbarSlot (0-8) -> swap inventory slot với hotbar slot
        mc.interactionManager.clickSlot(
            syncId,
            invSlot,
            hotbarSlot,
            SlotActionType.SWAP,
            mc.player
        );
    }
}
