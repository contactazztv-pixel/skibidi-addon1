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
 * HotbarDropper — Mua item từ shop rồi drop liên tục (farm drop exploit).
 * Kết hợp logic từ:
 *   - glazed: BlazeRodDropper, ShulkerDropper, OrderDropper
 *
 * ═══ FLOW ═══
 *   OPEN_SHOP → NAV_ITEM → BUY → CLOSE → DROP_ALL → REPEAT
 */
public class HotbarDropper extends Module {

    // ═══════════════════════════════════════
    // ITEM TYPES
    // ═══════════════════════════════════════
    public enum DropItem {
        BlazeRod,
        ShulkerBox,
        Spawner,
        Totem,
        GoldenApple,
        Custom
    }

    // ═══════════════════════════════════════
    // STAGES
    // ═══════════════════════════════════════
    private enum Stage {
        SHOP, SHOP_END, SHOP_SHULKER, SHOP_CONFIRM, SHOP_CHECK_FULL,
        SHOP_EXIT, DROP_ITEMS, WAIT
    }

    // ═══════════════════════════════════════
    // SETTINGS
    // ═══════════════════════════════════════
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    /** Item cần mua và drop */
    private final Setting<DropItem> dropItem = sgGeneral.add(
        new EnumSetting.Builder<DropItem>()
            .name("drop-item")
            .description("Item to buy from shop and drop.")
            .defaultValue(DropItem.BlazeRod)
            .build()
    );

    /** Delay giữa các action (tick) */
    private final Setting<Integer> delay = sgGeneral.add(
        new IntSetting.Builder()
            .name("delay")
            .description("Delay between actions in ticks.")
            .defaultValue(4)
            .sliderRange(1, 20)
            .build()
    );

    /** Randomize drop delay để tránh phát hiện */
    private final Setting<Boolean> randomDelay = sgGeneral.add(
        new BoolSetting.Builder()
            .name("random-delay")
            .description("Randomize drop delay to avoid detection.")
            .defaultValue(true)
            .build()
    );

    /** Số lần drop tối đa trước khi dừng (0 = vô hạn) */
    private final Setting<Integer> maxDrops = sgGeneral.add(
        new IntSetting.Builder()
            .name("max-drops")
            .description("Max drop cycles (0 = infinite).")
            .defaultValue(0)
            .sliderRange(0, 1000)
            .build()
    );

    // ═══════════════════════════════════════
    // INTERNAL STATE
    // ═══════════════════════════════════════
    private Stage  stage       = Stage.SHOP;
    private long   stageStart  = 0;
    private int    tickCounter = 0;
    private int    dropCount   = 0;
    private int    bulkBuyCount = 0;
    private static final int MAX_BULK_BUY = 5;
    private long nextDropDelay = 0;
    private static final long MIN_DROP_DELAY = 50;
    private static final long MAX_DROP_DELAY = 150;
    private final java.util.Random dropRandom = new java.util.Random();

    // ═══════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════
    public HotbarDropper() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "hotbar-dropper",
            "Automatically buys items from shop and drops them repeatedly."
        );
    }

    @Override
    public void onActivate() {
        stage       = Stage.SHOP;
        stageStart  = System.currentTimeMillis();
        dropCount   = 0;
        bulkBuyCount = 0;
        info("§a[HotbarDropper] §fActivated! Buying and dropping §e" + dropItem.get());
        ChatUtils.sendPlayerMsg("/shop");
    }

    @Override
    public void onDeactivate() {
        stage = Stage.SHOP;
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

        if (maxDrops.get() > 0 && dropCount >= maxDrops.get()) {
            info("§a[HotbarDropper] §fCompleted " + dropCount + " drop cycles.");
            toggle();
            return;
        }

        switch (stage) {

            case SHOP -> {
                // Chờ shop GUI mở
                if (mc.currentScreen != null) {
                    advanceTo(Stage.SHOP_END);
                } else if (elapsed() > 3000) {
                    ChatUtils.sendPlayerMsg("/shop");
                    stageStart = System.currentTimeMillis();
                }
            }

            case SHOP_END -> {
                // Navigate đến sub-category có item
                if (mc.currentScreen == null) { advanceTo(Stage.SHOP); return; }
                // Click category slot (thường slot 13 = trung tâm GUI)
                clickGuiSlot(13);
                advanceTo(Stage.SHOP_SHULKER);
            }

            case SHOP_SHULKER -> {
                // Tìm và click item cần mua
                if (mc.currentScreen == null) { advanceTo(Stage.SHOP); return; }
                int slot = findItemInGui();
                if (slot != -1) {
                    clickGuiSlot(slot);
                    advanceTo(Stage.SHOP_CONFIRM);
                } else {
                    closeGui();
                    advanceTo(Stage.SHOP);
                }
            }

            case SHOP_CONFIRM -> {
                // Confirm mua (slot 11 = OK button thường)
                if (mc.currentScreen != null) clickGuiSlot(11);
                advanceTo(Stage.SHOP_CHECK_FULL);
            }

            case SHOP_CHECK_FULL -> {
                bulkBuyCount++;
                if (isInventoryFull() || bulkBuyCount >= MAX_BULK_BUY) {
                    // Inventory đầy hoặc đã mua đủ - đóng shop và drop
                    closeGui();
                    advanceTo(Stage.SHOP_EXIT);
                } else {
                    // Mua thêm
                    advanceTo(Stage.SHOP_SHULKER);
                }
            }

            case SHOP_EXIT -> {
                closeGui();
                bulkBuyCount = 0;
                nextDropDelay = randomDelay.get()
                    ? MIN_DROP_DELAY + (long)(dropRandom.nextDouble() * (MAX_DROP_DELAY - MIN_DROP_DELAY))
                    : MIN_DROP_DELAY;
                advanceTo(Stage.DROP_ITEMS);
            }

            case DROP_ITEMS -> {
                if (elapsed() < nextDropDelay) return;

                // Drop tất cả item đã mua từ inventory
                boolean dropped = dropAllTargetItems();
                if (dropped) {
                    dropCount++;
                    if (dropCount % 10 == 0) {
                        info("§a[HotbarDropper] §fDropped §e" + dropCount + " §fbatches.");
                    }
                }
                advanceTo(Stage.SHOP);
                ChatUtils.sendPlayerMsg("/shop"); // Mở shop luôn cho cycle kế
            }

            case WAIT -> {
                if (elapsed() > 500) advanceTo(Stage.SHOP);
            }
        }
    }

    // ═══════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════

    /** Tìm item cần mua trong GUI shop */
    private int findItemInGui() {
        if (mc.player == null) return -1;
        var handler = mc.player.currentScreenHandler;
        if (handler == null) return -1;

        Item target = getTargetItem();
        if (target == null) return -1;

        for (int i = 0; i < Math.min(handler.slots.size(), 54); i++) {
            if (handler.slots.get(i).getStack().getItem() == target) return i;
        }
        return -1;
    }

    /** Drop tất cả item target trong inventory */
    private boolean dropAllTargetItems() {
        if (mc.player == null) return false;
        Item target = getTargetItem();
        if (target == null) return false;

        boolean found = false;
        for (int i = 0; i < 36; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || stack.getItem() != target) continue;

            // Select slot và drop
            int prev = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = (i < 9 ? i : 0);
            if (i < 9) {
                mc.player.dropSelectedItem(true); // Drop full stack
            } else {
                // Inventory slot - dùng click packet Q
                var handler = mc.player.playerScreenHandler;
                mc.interactionManager.clickSlot(handler.syncId, i + 9, 1, SlotActionType.THROW, mc.player);
            }
            mc.player.getInventory().selectedSlot = prev;
            found = true;
        }
        return found;
    }

    private Item getTargetItem() {
        return switch (dropItem.get()) {
            case BlazeRod   -> Items.BLAZE_ROD;
            case ShulkerBox -> Items.SHULKER_BOX;
            case Spawner    -> Items.SPAWNER;
            case Totem      -> Items.TOTEM_OF_UNDYING;
            case GoldenApple -> Items.GOLDEN_APPLE;
            default         -> null;
        };
    }

    private boolean isInventoryFull() {
        if (mc.player == null) return false;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return false;
        }
        return true;
    }

    private void clickGuiSlot(int slot) {
        if (mc.currentScreen == null || mc.player == null) return;
        var handler = mc.player.currentScreenHandler;
        if (handler == null || slot < 0 || slot >= handler.slots.size()) return;
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    private void closeGui() { if (mc.currentScreen != null) mc.setScreen(null); }

    private void advanceTo(Stage next) {
        stage = next; stageStart = System.currentTimeMillis(); tickCounter = 0;
    }

    private long elapsed() { return System.currentTimeMillis() - stageStart; }
}
