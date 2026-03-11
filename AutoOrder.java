package com.example.addon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * AutoOrder — Tự động đặt và xử lý order từ server shop.
 * Phân tích từ: glazed/com/nnpg/glazed/modules/main/AutoOrder.class
 *
 * ═══ STAGES ═══
 *   ORDERS → ORDERS_SELECT → ORDERS_CONFIRM → ORDERS_FINAL_EXIT
 *
 * Lệnh `/orders <item>` mở GUI danh sách orders.
 * Module click vào order của item cần, confirm, rồi reset để tìm order tiếp.
 */
public class AutoOrder extends Module {

    // ═══════════════════════════════════════
    // STAGES
    // ═══════════════════════════════════════
    private enum Stage {
        NONE,
        ORDERS,             // Gửi /orders <item>
        ORDERS_SELECT,      // Tìm và click order phù hợp
        ORDERS_CONFIRM,     // Confirm order
        ORDERS_FINAL_EXIT,  // Đóng GUI
        WAIT_CYCLE          // Chờ trước chu kỳ kế
    }

    // ═══════════════════════════════════════
    // SETTINGS
    // ═══════════════════════════════════════
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget  = settings.createGroup("Target");

    /** Tên item cần order (keyword tìm trong GUI) */
    private final Setting<String> targetItemName = sgGeneral.add(
        new StringSetting.Builder()
            .name("item-name")
            .description("Name of the item to order (keyword search in order GUI).")
            .defaultValue("blaze rod")
            .build()
    );

    /** Item để snipe trong orders (dùng ItemSetting để chọn chính xác) */
    private final Setting<Item> snipingItem = sgTarget.add(
        new ItemSetting.Builder()
            .name("snipping-item")
            .description("The item to snipe from orders.")
            .build()
    );

    /** Delay giữa các lần click/move (ms) */
    private final Setting<Integer> moveDelay = sgGeneral.add(
        new IntSetting.Builder()
            .name("move-delay")
            .description("Delay between item movements in milliseconds.")
            .defaultValue(200)
            .sliderRange(50, 1000)
            .build()
    );

    /** Chat feedback */
    private final Setting<Boolean> chatFeedback = sgGeneral.add(
        new BoolSetting.Builder()
            .name("chat-feedback")
            .description("Show chat feedback messages.")
            .defaultValue(true)
            .build()
    );

    /** Blacklist players */
    private final Setting<List<String>> blacklistedPlayers = sgGeneral.add(
        new StringListSetting.Builder()
            .name("blacklisted-players")
            .description("Ignore orders from these players.")
            .build()
    );

    // ═══════════════════════════════════════
    // INTERNAL STATE
    // ═══════════════════════════════════════
    private Stage stage        = Stage.NONE;
    private long  stageStart   = 0;
    private int   itemMoveIndex = 0;
    private long  lastItemMoveTime = 0;
    private static final long WAIT_TIME  = 500;  // ms
    private static final long CYCLE_DELAY = 2000; // ms between cycles

    // ═══════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════
    public AutoOrder() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "auto-order",
            "Automatically orders items from the server shop."
        );
    }

    @Override
    public void onActivate() {
        if (chatFeedback.get())
            info("§a[AutoOrder] §fStarted - Target: §e" + targetItemName.get());
        stage      = Stage.ORDERS;
        stageStart = System.currentTimeMillis();
    }

    @Override
    public void onDeactivate() {
        if (chatFeedback.get()) info("§e[AutoOrder] Stopped.");
        stage = Stage.NONE;
        if (mc.currentScreen != null) mc.setScreen(null);
    }

    // ═══════════════════════════════════════
    // TICK HANDLER
    // ═══════════════════════════════════════
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();

        switch (stage) {

            // ── Gửi lệnh /orders ──────────────────────────────────
            case ORDERS -> {
                if (now - stageStart < WAIT_TIME) return;
                ChatUtils.sendPlayerMsg("/orders " + targetItemName.get());
                advanceTo(Stage.ORDERS_SELECT);
            }

            // ── Tìm order phù hợp trong GUI ───────────────────────
            case ORDERS_SELECT -> {
                if (now - stageStart < WAIT_TIME) return;
                if (mc.currentScreen == null) {
                    // GUI chưa mở - chờ thêm hoặc retry
                    if (now - stageStart > 3000) advanceTo(Stage.ORDERS);
                    return;
                }

                int targetSlot = findTargetOrderSlot();
                if (targetSlot == -1) {
                    if (chatFeedback.get())
                        info("§e[AutoOrder] No matching orders for §f" + targetItemName.get() + "§e. Retrying...");
                    closeGui();
                    advanceTo(Stage.WAIT_CYCLE);
                    return;
                }

                // Click slot để select order
                clickGuiSlot(targetSlot);
                if (chatFeedback.get())
                    info("§a[AutoOrder] §fFound order at slot §e" + targetSlot);
                advanceTo(Stage.ORDERS_CONFIRM);
            }

            // ── Confirm order ─────────────────────────────────────
            case ORDERS_CONFIRM -> {
                if (now - stageStart < (long) moveDelay.get()) return;
                if (mc.currentScreen == null) { advanceTo(Stage.ORDERS); return; }

                // Confirm thường ở slot 11 (center left) hoặc 13 (center)
                clickGuiSlot(11);

                if (chatFeedback.get())
                    info("§a[AutoOrder] §fConfirmed order for: §e" + targetItemName.get());

                // Kiểm tra có item trong inventory không
                if (!hasTargetItemInInventory()) {
                    if (chatFeedback.get())
                        info("§e[AutoOrder] §fCompleted - no more §e" + targetItemName.get() + " §fin inventory.");
                    closeGui();
                    toggle();
                    return;
                }

                advanceTo(Stage.ORDERS_FINAL_EXIT);
            }

            // ── Đóng GUI ──────────────────────────────────────────
            case ORDERS_FINAL_EXIT -> {
                if (now - stageStart < (long) moveDelay.get()) return;
                closeGui();
                advanceTo(Stage.WAIT_CYCLE);
            }

            // ── Chờ cycle kế ──────────────────────────────────────
            case WAIT_CYCLE -> {
                if (now - stageStart >= CYCLE_DELAY) {
                    advanceTo(Stage.ORDERS);
                }
            }
        }
    }

    // ═══════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════

    /**
     * Tìm slot trong GUI orders chứa item target.
     * So khớp theo tên item (case-insensitive).
     */
    private int findTargetOrderSlot() {
        if (mc.player == null) return -1;
        var handler = mc.player.currentScreenHandler;
        if (handler == null) return -1;

        String keyword = targetItemName.get().toLowerCase();

        for (int i = 0; i < Math.min(handler.slots.size(), 54); i++) {
            var stack = handler.slots.get(i).getStack();
            if (stack.isEmpty()) continue;

            // Check item name
            String name = stack.getName().getString().toLowerCase();
            if (name.contains(keyword)) {
                // Kiểm tra seller có bị blacklist không
                if (!isBlacklisted(stack)) return i;
            }

            // Check theo item type
            if (snipingItem.get() != null && stack.getItem() == snipingItem.get()) {
                if (!isBlacklisted(stack)) return i;
            }
        }
        return -1;
    }

    /** Kiểm tra stack có thuộc seller bị blacklist không (qua lore) */
    private boolean isBlacklisted(net.minecraft.item.ItemStack stack) {
        if (blacklistedPlayers.get().isEmpty()) return false;
        // Đọc lore để tìm tên seller
        var lore = stack.get(net.minecraft.component.DataComponentTypes.LORE);
        if (lore == null) return false;
        for (var line : lore.lines()) {
            String text = line.getString().toLowerCase();
            for (String blocked : blacklistedPlayers.get()) {
                if (text.contains(blocked.toLowerCase())) return true;
            }
        }
        return false;
    }

    /** Kiểm tra có item target trong inventory không */
    private boolean hasTargetItemInInventory() {
        if (mc.player == null) return false;
        if (snipingItem.get() == null) return true; // Không cần check

        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == snipingItem.get()) return true;
        }
        return false;
    }

    private void clickGuiSlot(int slot) {
        if (mc.currentScreen == null || mc.player == null) return;
        var handler = mc.player.currentScreenHandler;
        if (handler == null || slot < 0 || slot >= handler.slots.size()) return;
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    private void closeGui() { if (mc.currentScreen != null) mc.setScreen(null); }

    private void advanceTo(Stage next) {
        stage = next; stageStart = System.currentTimeMillis();
    }
}
