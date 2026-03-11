package com.example.addon.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * ElytraSwapper - Tự động swap Elytra vào/ra khỏi chestplate slot.
 * Phân tích từ: zinc/com/vrzt/zinc/modules/pvp/ElytraSwap.class
 *
 * Logic:
 *   - Khi bật: nếu đang mặc Elytra, không làm gì
 *   - Khi đang rơi / bay: swap Elytra vào chestplate
 *   - Khi tiếp đất: swap Chestplate trở lại
 *   - Hỗ trợ swap từ inventory hoặc hotbar
 */
public class ElytraSwapper extends Module {

    // ==================== SETTINGS ====================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    /** Mode hoạt động */
    public enum SwapMode {
        OnFall,     // Swap khi bắt đầu rơi
        OnJump,     // Swap khi nhảy
        Manual      // Chỉ swap khi toggle module
    }

    private final Setting<SwapMode> mode = sgGeneral.add(
        new EnumSetting.Builder<SwapMode>()
            .name("mode")
            .description("When to swap the Elytra.")
            .defaultValue(SwapMode.OnFall)
            .build()
    );

    /** Swap lại Chestplate khi tiếp đất */
    private final Setting<Boolean> swapBack = sgGeneral.add(
        new BoolSetting.Builder()
            .name("swap-back")
            .description("Swap back to chestplate when landing.")
            .defaultValue(true)
            .build()
    );

    /** Tự disable sau khi swap (cho manual mode) */
    private final Setting<Boolean> autoDisable = sgGeneral.add(
        new BoolSetting.Builder()
            .name("auto-disable")
            .description("Automatically disable after performing the swap (Manual mode).")
            .defaultValue(false)
            .visible(() -> mode.get() == SwapMode.Manual)
            .build()
    );

    // ==================== INTERNAL STATE ====================
    private boolean wasFlying      = false;
    private boolean wasOnGround    = true;
    private int     savedChestSlot = -1; // Inventory slot của chestplate đã cất

    // Slot armor: 5=helmet, 6=chestplate, 7=leggings, 8=boots (trong PlayerScreenHandler)
    private static final int CHESTPLATE_SCREEN_SLOT = 6;

    // ==================== CONSTRUCTOR ====================
    public ElytraSwapper() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "elytra-swapper",
            "Automatically swaps Elytra in and out of chestplate slot."
        );
    }

    @Override
    public void onActivate() {
        if (mode.get() == SwapMode.Manual) {
            performSwap();
            if (autoDisable.get()) toggle();
        }
        wasOnGround = mc.player != null && mc.player.isOnGround();
    }

    // ==================== EVENT HANDLER ====================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (mode.get() == SwapMode.Manual) return;

        boolean onGround = mc.player.isOnGround();
        boolean flying   = mc.player.isFallFlying();

        switch (mode.get()) {
            case OnFall -> {
                // Swap Elytra khi bắt đầu rơi (và tidak sedang nerbang)
                boolean falling = !onGround && mc.player.getVelocity().y < -0.1 && !flying;
                if (falling && !wasFlying && !hasElytraEquipped()) {
                    swapToElytra();
                }

                // Swap lại Chestplate khi tiếp đất
                if (swapBack.get() && onGround && !wasOnGround && hasElytraEquipped()) {
                    swapToChestplate();
                }
            }
            case OnJump -> {
                // Swap Elytra khi nhảy
                boolean jumping = !wasOnGround && onGround == false;
                if (jumping && !hasElytraEquipped()) {
                    swapToElytra();
                }
                if (swapBack.get() && onGround && hasElytraEquipped()) {
                    swapToChestplate();
                }
            }
        }

        wasFlying   = flying;
        wasOnGround = onGround;
    }

    // ==================== HELPERS ====================

    /** Kiểm tra đang mặc Elytra không */
    private boolean hasElytraEquipped() {
        return mc.player.getInventory().getArmorStack(2).getItem() == Items.ELYTRA;
        // armor index 2 = chestplate slot
    }

    /** Swap Elytra vào chestplate slot */
    private void swapToElytra() {
        int elytraSlot = findInInventory(Items.ELYTRA);
        if (elytraSlot == -1) return;

        // Lưu vị trí chestplate hiện tại trước khi swap
        savedChestSlot = elytraSlot; // Sau khi swap, elytra sẽ vào slot này

        performArmorSwap(elytraSlot, CHESTPLATE_SCREEN_SLOT);
    }

    /** Swap Chestplate trở lại */
    private void swapToChestplate() {
        if (savedChestSlot != -1) {
            // Chestplate đã được swap vào slot của elytra trước đó
            performArmorSwap(savedChestSlot, CHESTPLATE_SCREEN_SLOT);
        } else {
            // Tìm chestplate trong inventory
            int chestSlot = findChestplateInInventory();
            if (chestSlot == -1) return;
            performArmorSwap(chestSlot, CHESTPLATE_SCREEN_SLOT);
        }
    }

    /** Thực hiện swap giữa inventory slot và armor slot */
    private void performArmorSwap(int invSlot, int armorScreenSlot) {
        if (mc.player == null || mc.currentScreen != null) return;

        int syncId = mc.player.playerScreenHandler.syncId;

        // Click inventory slot để pickup
        mc.interactionManager.clickSlot(syncId, invSlot, 0, SlotActionType.PICKUP, mc.player);
        // Click armor slot để equip
        mc.interactionManager.clickSlot(syncId, armorScreenSlot, 0, SlotActionType.PICKUP, mc.player);
        // Click lại inventory để trả item cũ (nếu có)
        mc.interactionManager.clickSlot(syncId, invSlot, 0, SlotActionType.PICKUP, mc.player);
    }

    /** Tìm item trong inventory (slot 9-44), trả về -1 nếu không có */
    private int findInInventory(net.minecraft.item.Item item) {
        for (int i = 9; i < 45; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    /** Tìm chestplate (bất kỳ loại nào, không phải Elytra) */
    private int findChestplateInInventory() {
        for (int i = 9; i < 45; i++) {
            var item = mc.player.getInventory().getStack(i).getItem();
            if (item instanceof net.minecraft.item.ArmorItem armor) {
                if (armor.getSlotType() == net.minecraft.entity.EquipmentSlot.CHEST) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Cho manual mode: swap Elytra nếu đang có Chestplate, và ngược lại */
    private void performSwap() {
        if (mc.player == null) return;
        if (hasElytraEquipped()) {
            swapToChestplate();
        } else {
            swapToElytra();
        }
    }
}
