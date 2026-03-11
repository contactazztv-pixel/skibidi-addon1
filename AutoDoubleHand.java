package com.example.addon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * AutoDoubleHand - Tự động cầm totem vào offhand sau khi totem bị pop.
 * Phân tích từ: glazed/com/nnpg/glazed/modules/pvp/AutoDoubleHand.class
 *
 * Logic:
 *   - Mỗi tick kiểm tra offhand có totem không
 *   - Nếu không có, tìm totem trong hotbar
 *   - Swap totem từ hotbar vào offhand
 *   - Ưu tiên hotbar trước, sau đó inventory
 */
public class AutoDoubleHand extends Module {

    // ==================== CONSTRUCTOR ====================
    public AutoDoubleHand() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "auto-double-hand",
            "After totem pop, automatically switches to totem in offhand."
        );
    }

    @Override
    public void onActivate() {
        equipTotemIfNeeded();
    }

    // ==================== EVENT HANDLER ====================

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        equipTotemIfNeeded();
    }

    // ==================== HELPERS ====================

    /**
     * Kiểm tra offhand có totem không; nếu không, tìm và equip.
     */
    private void equipTotemIfNeeded() {
        var offhand = mc.player.getOffHandStack();

        // Offhand đã có totem -> không cần làm gì
        if (offhand.getItem() == Items.TOTEM_OF_UNDYING) return;

        // Tìm totem trong hotbar trước
        int hotbarSlot = findTotemInHotbar();
        if (hotbarSlot != -1) {
            swapHotbarToOffhand(hotbarSlot);
            return;
        }

        // Tìm trong inventory (slot 9-35)
        int invSlot = findTotemInInventory();
        if (invSlot != -1) {
            swapInventoryToOffhand(invSlot);
        }
    }

    /** Tìm totem trong hotbar (slot 0-8), trả về -1 nếu không có */
    private int findTotemInHotbar() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                return i;
            }
        }
        return -1;
    }

    /** Tìm totem trong inventory (slot 9-35) */
    private int findTotemInInventory() {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Swap item trong hotbar slot về offhand.
     * Cơ chế: dùng packet SetHeldItem + PickItem hoặc swap slot packet.
     */
    private void swapHotbarToOffhand(int hotbarSlot) {
        // Dùng CreativeInventoryAction hoặc PickFromInventory packet tùy version
        // Cách đơn giản nhất với Fabric: dùng PlayerInventory.pickSlot
        mc.player.getInventory().selectedSlot = hotbarSlot;

        // Gửi swap hands packet (F key)
        mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);

        // Thực ra cần dùng packet để swap main-hand <-> off-hand
        // Phương pháp chuẩn Meteor:
        var network = mc.getNetworkHandler();
        if (network != null) {
            // Slot 40 là offhand trong inventory protocol
            // Dùng PickFromInventory (slot hotbar) sau đó swap
            mc.player.getInventory().swapSlotWithHotbar(hotbarSlot);
        }
    }

    /** Swap item trong inventory về offhand thông qua click thông thường */
    private void swapInventoryToOffhand(int invSlot) {
        if (mc.currentScreen != null) return; // Không làm gì nếu đang mở màn hình

        // Mở inventory, click totem, nhấn F để offhand
        // Cách đơn giản: dùng network handler để gửi click packet
        var network = mc.getNetworkHandler();
        if (network == null) return;

        // Click inventory slot để pick up, rồi click offhand slot
        int syncId = mc.player.playerScreenHandler.syncId;
        // Slot trong PlayerInventory: invSlot + 9 (offset màn hình)
        mc.interactionManager.clickSlot(syncId, invSlot, 40, net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);
    }
}
