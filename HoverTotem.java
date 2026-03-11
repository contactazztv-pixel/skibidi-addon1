package com.example.addon.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * HoverTotem - Giữ totem trong offhand và auto-refill từ inventory.
 * Phân tích từ: glazed/com/nnpg/glazed/modules/pvp/HoverTotem.class
 *
 * Chức năng:
 *   - Liên tục kiểm tra offhand có totem không
 *   - Nếu không có (do pop hoặc throw), tìm trong inventory và equip
 *   - Có thể ưu tiên hotbar hoặc inventory
 *   - Cảnh báo khi hết totem
 */
public class HoverTotem extends Module {

    // ==================== SETTINGS ====================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    /** Cảnh báo khi số lượng totem thấp */
    private final Setting<Boolean> lowAlert = sgGeneral.add(
        new BoolSetting.Builder()
            .name("low-alert")
            .description("Send chat alert when totem count is low.")
            .defaultValue(true)
            .build()
    );

    /** Ngưỡng cảnh báo totem thấp */
    private final Setting<Integer> alertThreshold = sgGeneral.add(
        new IntSetting.Builder()
            .name("alert-threshold")
            .description("Alert when totem count drops to this value.")
            .defaultValue(3)
            .sliderRange(1, 10)
            .visible(lowAlert::get)
            .build()
    );

    /** Tìm kiếm trong Shulker Box hay chỉ inventory */
    private final Setting<Boolean> searchHotbar = sgGeneral.add(
        new BoolSetting.Builder()
            .name("search-hotbar")
            .description("Search hotbar for totems before inventory.")
            .defaultValue(true)
            .build()
    );

    // ==================== INTERNAL STATE ====================
    private boolean alerted         = false;
    private int     checkDelay      = 0;

    // ==================== CONSTRUCTOR ====================
    public HoverTotem() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "hover-totem",
            "Keeps a Totem of Undying in your offhand and auto-refills from inventory."
        );
    }

    // ==================== EVENT HANDLER ====================

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // Giảm delay giữa các lần check
        if (checkDelay > 0) {
            checkDelay--;
            return;
        }
        checkDelay = 2; // Check mỗi 2 tick để không spam packet

        var offhand = mc.player.getOffHandStack();
        if (offhand.getItem() == Items.TOTEM_OF_UNDYING) {
            alerted = false; // Reset cờ cảnh báo khi đã có totem
            return;
        }

        // Tìm totem để equip
        int slot = -1;

        if (searchHotbar.get()) {
            slot = findTotemInHotbar();
        }
        if (slot == -1) {
            slot = findTotemInInventory();
        }

        if (slot == -1) {
            // Không có totem
            if (!alerted && lowAlert.get()) {
                warning("§c[HoverTotem] §fNo totems left!");
                alerted = true;
            }
            return;
        }

        // Kiểm tra cảnh báo thấp
        if (lowAlert.get()) {
            int total = countTotems();
            if (total <= alertThreshold.get() && !alerted) {
                warning("§e[HoverTotem] §fLow totems: " + total);
                alerted = true;
            } else if (total > alertThreshold.get()) {
                alerted = false;
            }
        }

        // Swap totem vào offhand (slot 40 = offhand trong PlayerInventory)
        swapToOffhand(slot);
    }

    // ==================== HELPERS ====================

    /** Tìm totem trong hotbar (0-8) */
    private int findTotemInHotbar() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                return i;
            }
        }
        return -1;
    }

    /** Tìm totem trong inventory (9-35) */
    private int findTotemInInventory() {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                return i;
            }
        }
        return -1;
    }

    /** Đếm tổng số totem trong inventory */
    private int countTotems() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                count++;
            }
        }
        // Đếm offhand
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) count++;
        return count;
    }

    /**
     * Swap item từ inventory slot vào offhand.
     * Dùng SlotActionType.SWAP với hotbarId = 40 (offhand slot).
     */
    private void swapToOffhand(int invSlot) {
        if (mc.currentScreen != null) return; // Không làm khi đang mở GUI

        int syncId = mc.player.playerScreenHandler.syncId;

        // SWAP action với button = 40 sẽ swap slot đó với offhand
        mc.interactionManager.clickSlot(
            syncId,
            invSlot,
            40, // Hotbar ID 40 = offhand trong protocol
            SlotActionType.SWAP,
            mc.player
        );
    }
}
