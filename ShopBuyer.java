package com.example.addon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * ShopBuyer — Tự động mua item từ PVP shop category.
 * Phân tích từ: glazed/com/nnpg/glazed/modules/main/ShopBuyer.class
 *
 * ═══ FLOW ═══
 *   1. Gửi /shop để mở shop GUI
 *   2. Navigate đến PVP category (slot cố định)
 *   3. Tìm và click item cần mua
 *   4. Confirm mua
 *   5. (Tùy chọn) Drop ngay sau khi mua
 *   6. Lặp lại
 */
public class ShopBuyer extends Module {

    // ═══════════════════════════════════════
    // ITEM TYPES (điều chỉnh theo server)
    // ═══════════════════════════════════════
    public enum ItemType {
        BlazeRod,       // Blaze Rod
        Shulker,        // Shulker Box
        Spawner,        // Mob Spawner
        Totem,          // Totem of Undying
        GoldenApple,    // Golden Apple
        Obsidian,       // Obsidian
        Crystal,        // End Crystal
        Custom          // Custom item (dùng item-setting)
    }

    // ═══════════════════════════════════════
    // STAGES
    // ═══════════════════════════════════════
    private enum Stage {
        IDLE,
        OPEN_SHOP,
        WAIT_SHOP,
        NAV_CATEGORY,
        FIND_ITEM,
        BUY_ITEM,
        CONFIRM_BUY,
        CLOSE_SHOP,
        DROP_ITEMS,
        WAIT_CYCLE
    }

    // ═══════════════════════════════════════
    // SETTINGS
    // ═══════════════════════════════════════
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget  = settings.createGroup("Target");

    /** Item cần mua */
    private final Setting<ItemType> itemToBuy = sgGeneral.add(
        new EnumSetting.Builder<ItemType>()
            .name("item")
            .description("Select which item to buy from PVP category.")
            .defaultValue(ItemType.BlazeRod)
            .build()
    );

    /** Auto drop sau khi mua */
    private final Setting<Boolean> autoDrop = sgGeneral.add(
        new BoolSetting.Builder()
            .name("auto-drop")
            .description("Automatically drop purchased items.")
            .defaultValue(false)
            .build()
    );

    /** Delay giữa các action (tick) */
    private final Setting<Integer> delay = sgGeneral.add(
        new IntSetting.Builder()
            .name("delay")
            .description("Delay between actions in ticks.")
            .defaultValue(5)
            .sliderRange(1, 20)
            .build()
    );

    /** Số lần mua trước khi dừng (0 = vô hạn) */
    private final Setting<Integer> buyCount = sgTarget.add(
        new IntSetting.Builder()
            .name("buy-count")
            .description("How many times to buy (0 = infinite).")
            .defaultValue(0)
            .sliderRange(0, 100)
            .build()
    );

    /** Dừng khi inventory đầy */
    private final Setting<Boolean> stopWhenFull = sgTarget.add(
        new BoolSetting.Builder()
            .name("stop-when-full")
            .description("Stop when inventory is full.")
            .defaultValue(true)
            .build()
    );

    // ═══════════════════════════════════════
    // INTERNAL STATE
    // ═══════════════════════════════════════
    private Stage stage        = Stage.IDLE;
    private long  stageStart   = 0;
    private int   tickCounter  = 0;
    private int   buysCompleted = 0;
    private boolean inBuyingScreen = false;

    // ═══════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════
    public ShopBuyer() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "shop-buyer",
            "Automatically buys selected items from PVP shop category."
        );
    }

    @Override
    public void onActivate() {
        stage         = Stage.OPEN_SHOP;
        stageStart    = System.currentTimeMillis();
        buysCompleted = 0;
        tickCounter   = 0;
        info("§a[ShopBuyer] §fStarted. Buying: §e" + itemToBuy.get());
    }

    @Override
    public void onDeactivate() {
        stage = Stage.IDLE;
        if (mc.currentScreen != null) mc.setScreen(null);
    }

    // ═══════════════════════════════════════
    // TICK HANDLER
    // ═══════════════════════════════════════
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        tickCounter++;
        if (tickCounter < delay.get()) return;
        tickCounter = 0;

        // Kiểm tra inventory đầy
        if (stopWhenFull.get() && isInventoryFull()) {
            info("§e[ShopBuyer] Inventory full. Stopping.");
            toggle();
            return;
        }

        // Kiểm tra đã đủ số lần mua
        if (buyCount.get() > 0 && buysCompleted >= buyCount.get()) {
            info("§a[ShopBuyer] §fCompleted " + buysCompleted + " purchases. Stopping.");
            toggle();
            return;
        }

        switch (stage) {

            case OPEN_SHOP -> {
                ChatUtils.sendPlayerMsg("/shop");
                advanceTo(Stage.WAIT_SHOP);
            }

            case WAIT_SHOP -> {
                if (mc.currentScreen != null) {
                    advanceTo(Stage.NAV_CATEGORY);
                } else if (elapsed() > 4000) {
                    advanceTo(Stage.OPEN_SHOP);
                }
            }

            case NAV_CATEGORY -> {
                // Click PVP category (slot thay đổi theo server - thường slot 13/14)
                int catSlot = getPVPCategorySlot();
                clickGuiSlot(catSlot);
                advanceTo(Stage.FIND_ITEM);
            }

            case FIND_ITEM -> {
                if (mc.currentScreen == null) { advanceTo(Stage.OPEN_SHOP); return; }
                int itemSlot = getItemSlot(itemToBuy.get());
                if (itemSlot == -1) {
                    warning("§c[ShopBuyer] Item §e" + itemToBuy.get() + " §cnot found in shop!");
                    closeGui();
                    advanceTo(Stage.WAIT_CYCLE);
                } else {
                    clickGuiSlot(itemSlot);
                    advanceTo(Stage.CONFIRM_BUY);
                }
            }

            case CONFIRM_BUY -> {
                // Nhiều shop có confirm screen - click OK (slot 11)
                if (mc.currentScreen != null) {
                    clickGuiSlot(11);
                }
                buysCompleted++;
                info("§a[ShopBuyer] §fPurchased §e" + itemToBuy.get() + " §f(#" + buysCompleted + ")");
                closeGui();
                advanceTo(autoDrop.get() ? Stage.DROP_ITEMS : Stage.WAIT_CYCLE);
            }

            case DROP_ITEMS -> {
                dropPurchasedItems();
                advanceTo(Stage.WAIT_CYCLE);
            }

            case WAIT_CYCLE -> {
                // Chờ 1 giây rồi mua lại
                if (elapsed() > 1000) {
                    advanceTo(Stage.OPEN_SHOP);
                }
            }
        }
    }

    // ═══════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════

    /**
     * Lấy slot của PVP category trong shop GUI chính.
     * ⚠️ Điều chỉnh theo server của bạn!
     */
    private int getPVPCategorySlot() {
        return 13; // PVP category thường ở giữa GUI 6-row
    }

    /**
     * Lấy slot của item trong shop sub-menu.
     * ⚠️ Điều chỉnh theo server của bạn!
     */
    private int getItemSlot(ItemType type) {
        if (mc.player == null) return -1;
        var handler = mc.player.currentScreenHandler;
        if (handler == null) return -1;

        Item targetItem = switch (type) {
            case BlazeRod   -> Items.BLAZE_ROD;
            case Shulker    -> Items.SHULKER_BOX;
            case Spawner    -> Items.SPAWNER;
            case Totem      -> Items.TOTEM_OF_UNDYING;
            case GoldenApple -> Items.GOLDEN_APPLE;
            case Obsidian   -> Items.OBSIDIAN;
            case Crystal    -> Items.END_CRYSTAL;
            default         -> null;
        };
        if (targetItem == null) return -1;

        // Tìm item trong GUI
        for (int i = 0; i < Math.min(handler.slots.size(), 54); i++) {
            if (handler.slots.get(i).getStack().getItem() == targetItem) return i;
        }
        return -1;
    }

    /** Click slot trong GUI hiện tại */
    private void clickGuiSlot(int slot) {
        if (mc.currentScreen == null || mc.player == null) return;
        var handler = mc.player.currentScreenHandler;
        if (handler == null || slot < 0 || slot >= handler.slots.size()) return;
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    /** Drop tất cả item vừa mua */
    private void dropPurchasedItems() {
        if (mc.player == null) return;
        Item targetItem = switch (itemToBuy.get()) {
            case BlazeRod   -> Items.BLAZE_ROD;
            case Shulker    -> Items.SHULKER_BOX;
            default         -> null;
        };
        if (targetItem == null) return;

        for (int i = 0; i < 36; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == targetItem) {
                mc.player.dropSelectedItem(true); // Drop full stack
                break;
            }
        }
    }

    /** Kiểm tra inventory đầy */
    private boolean isInventoryFull() {
        if (mc.player == null) return false;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return false;
        }
        return true;
    }

    private void closeGui() {
        if (mc.currentScreen != null) mc.setScreen(null);
    }

    private void advanceTo(Stage next) {
        stage = next; stageStart = System.currentTimeMillis(); tickCounter = 0;
    }

    private long elapsed() { return System.currentTimeMillis() - stageStart; }
}
