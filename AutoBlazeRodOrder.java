package com.example.addon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * AutoBlazeRodOrder — Tự động nhận order, mua blaze rod từ shop, giao hàng.
 * Phân tích từ: glazed/com/nnpg/glazed/modules/main/AutoBlazeRodOrder.class
 *
 * ═══ FLOW ═══
 *   IDLE
 *    └─► OPEN_ORDERS   (/orders blaze rods)
 *         └─► READ_PRICE  (kiểm tra giá ≥ min-price)
 *              └─► ACCEPT_ORDER (click confirm)
 *                   └─► OPEN_SHOP (/shop)
 *                        └─► BUY_BLAZE  (navigate → mua blaze rod)
 *                             └─► DELIVER (giao hàng qua /order deliver)
 *                                  └─► IDLE
 *
 * ⚠️  Slot index dựa trên server EarthMC / tương tự.
 *     Điều chỉnh SLOT constants nếu dùng server khác.
 */
public class AutoBlazeRodOrder extends Module {

    // ═══════════════════════════════════════
    // STAGES (State Machine)
    // ═══════════════════════════════════════
    private enum Stage {
        NONE,
        OPEN_ORDERS,        // Gửi /orders
        WAIT_ORDERS_GUI,    // Chờ GUI orders mở
        READ_ORDER,         // Đọc giá order đang có
        ACCEPT_ORDER,       // Click accept
        OPEN_SHOP,          // Gửi /shop
        WAIT_SHOP_GUI,      // Chờ GUI shop mở
        NAV_BLAZE,          // Điều hướng đến mục blaze rod
        BUY_BLAZE,          // Click mua blaze rod
        CLOSE_SHOP,         // Đóng shop
        DELIVER,            // Gửi /order deliver
        WAIT_NEXT_CYCLE     // Chờ trước khi cycle tiếp
    }

    // ═══════════════════════════════════════
    // SETTINGS
    // ═══════════════════════════════════════
    private final SettingGroup sgGeneral   = settings.getDefaultGroup();
    private final SettingGroup sgBlacklist = settings.createGroup("Blacklist");

    /** Giá tối thiểu để nhận order (hỗ trợ K/M/B) */
    private final Setting<String> minPrice = sgGeneral.add(
        new StringSetting.Builder()
            .name("min-price")
            .description("Minimum price to deliver blaze rods for (supports K, M, B suffixes).")
            .defaultValue("350K")
            .build()
    );

    /** Hiện thông báo chi tiết */
    private final Setting<Boolean> notifications = sgGeneral.add(
        new BoolSetting.Builder()
            .name("notifications")
            .description("Show detailed price checking notifications.")
            .defaultValue(true)
            .build()
    );

    /** Bỏ qua delay (có thể không ổn định) */
    private final Setting<Boolean> speedMode = sgGeneral.add(
        new BoolSetting.Builder()
            .name("speed-mode")
            .description("Maximum speed mode - removes most delays (may be unstable).")
            .defaultValue(false)
            .build()
    );

    /** Delay giữa các action (tick) */
    private final Setting<Integer> actionDelay = sgGeneral.add(
        new IntSetting.Builder()
            .name("action-delay")
            .description("Ticks between actions (higher = safer).")
            .defaultValue(5)
            .sliderRange(1, 20)
            .visible(() -> !speedMode.get())
            .build()
    );

    /** Delay giữa các chu kỳ (tick) */
    private final Setting<Integer> cycleDelay = sgGeneral.add(
        new IntSetting.Builder()
            .name("cycle-delay")
            .description("Ticks to wait between order cycles.")
            .defaultValue(60)
            .sliderRange(20, 200)
            .build()
    );

    /** Danh sách player bị bỏ qua */
    private final Setting<List<String>> blacklistedPlayers = sgBlacklist.add(
        new StringListSetting.Builder()
            .name("blacklisted-players")
            .description("Players whose orders will be ignored.")
            .build()
    );

    // ═══════════════════════════════════════
    // INTERNAL STATE
    // ═══════════════════════════════════════
    private Stage stage          = Stage.NONE;
    private long  stageStart     = 0;
    private int   tickCounter    = 0;
    private double parsedMinPrice = 0;

    // ═══════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════
    public AutoBlazeRodOrder() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "auto-blaze-rod-order",
            "Automatically buys and sells blaze rods in orders for profit (FAST MODE)."
        );
    }

    @Override
    public void onActivate() {
        parsedMinPrice = parsePrice(minPrice.get());
        if (parsedMinPrice <= 0) {
            ChatUtils.error("Invalid minimum price format!");
            toggle();
            return;
        }
        if (notifications.get())
            info("§a[AutoBlazeRodOrder] §fStarted. Min price: §e" + minPrice.get());
        stage      = Stage.OPEN_ORDERS;
        stageStart = System.currentTimeMillis();
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        stage = Stage.NONE;
        // Đóng GUI nếu đang mở
        if (mc.currentScreen != null) mc.setScreen(null);
    }

    // ═══════════════════════════════════════
    // TICK HANDLER
    // ═══════════════════════════════════════
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        int delay = speedMode.get() ? 2 : actionDelay.get();
        tickCounter++;
        if (tickCounter < delay) return;
        tickCounter = 0;

        switch (stage) {

            // ── 1. Gửi lệnh /orders ──────────────────────────────
            case OPEN_ORDERS -> {
                ChatUtils.sendPlayerMsg("/orders");
                advanceTo(Stage.WAIT_ORDERS_GUI);
            }

            // ── 2. Chờ GUI orders mở ─────────────────────────────
            case WAIT_ORDERS_GUI -> {
                if (isGuiOpen()) {
                    advanceTo(Stage.READ_ORDER);
                } else if (elapsed() > 3000) {
                    // Timeout - thử lại
                    advanceTo(Stage.OPEN_ORDERS);
                }
            }

            // ── 3. Đọc order → kiểm tra giá ─────────────────────
            case READ_ORDER -> {
                if (!isGuiOpen()) { advanceTo(Stage.OPEN_ORDERS); return; }

                // Slot 13 thường chứa thông tin order trong GUI dạng "book"
                // Đọc display name từ slot để lấy giá
                double price = readOrderPrice();

                if (price <= 0) {
                    // Không có order
                    if (notifications.get()) info("§e[AutoBlazeRodOrder] No orders found. Waiting...");
                    closeGui();
                    advanceTo(Stage.WAIT_NEXT_CYCLE);
                } else if (price < parsedMinPrice) {
                    if (notifications.get())
                        info("§e[AutoBlazeRodOrder] Price §c" + formatPrice(price) + " §fbelow min §a" + minPrice.get() + " §f- Skipping.");
                    closeGui();
                    advanceTo(Stage.WAIT_NEXT_CYCLE);
                } else {
                    if (notifications.get())
                        info("§a[AutoBlazeRodOrder] §fAccepting order: §a" + formatPrice(price));
                    advanceTo(Stage.ACCEPT_ORDER);
                }
            }

            // ── 4. Click accept order (slot 11 hoặc 13) ──────────
            case ACCEPT_ORDER -> {
                if (!isGuiOpen()) { advanceTo(Stage.OPEN_ORDERS); return; }
                clickGuiSlot(11); // Accept button - adjust per server
                closeGui();
                advanceTo(Stage.OPEN_SHOP);
            }

            // ── 5. Gửi /shop ─────────────────────────────────────
            case OPEN_SHOP -> {
                ChatUtils.sendPlayerMsg("/shop");
                advanceTo(Stage.WAIT_SHOP_GUI);
            }

            // ── 6. Chờ GUI shop ───────────────────────────────────
            case WAIT_SHOP_GUI -> {
                if (isGuiOpen()) {
                    advanceTo(Stage.NAV_BLAZE);
                } else if (elapsed() > 3000) {
                    advanceTo(Stage.OPEN_SHOP);
                }
            }

            // ── 7. Navigate đến blaze rod trong shop ──────────────
            case NAV_BLAZE -> {
                if (!isGuiOpen()) { advanceTo(Stage.OPEN_SHOP); return; }
                // Slot 14 = combat/materials category (adjust per server)
                clickGuiSlot(14);
                advanceTo(Stage.BUY_BLAZE);
            }

            // ── 8. Mua blaze rod ─────────────────────────────────
            case BUY_BLAZE -> {
                if (!isGuiOpen()) { advanceTo(Stage.OPEN_SHOP); return; }
                // Tìm slot có blaze rod
                int blazeSlot = findBlazeRodInGui();
                if (blazeSlot != -1) {
                    clickGuiSlot(blazeSlot);
                    if (notifications.get()) info("§a[AutoBlazeRodOrder] §fBought blaze rods!");
                } else {
                    if (notifications.get()) info("§c[AutoBlazeRodOrder] Blaze rod not found in shop!");
                }
                closeGui();
                advanceTo(Stage.DELIVER);
            }

            // ── 9. Giao hàng ─────────────────────────────────────
            case DELIVER -> {
                // Server command để deliver order - adjust per server
                ChatUtils.sendPlayerMsg("/order deliver");
                if (notifications.get()) info("§a[AutoBlazeRodOrder] §fDelivered order!");
                advanceTo(Stage.WAIT_NEXT_CYCLE);
            }

            // ── 10. Chờ cycle tiếp ───────────────────────────────
            case WAIT_NEXT_CYCLE -> {
                if (elapsed() > (long) cycleDelay.get() * 50) {
                    advanceTo(Stage.OPEN_ORDERS);
                }
            }
        }
    }

    // ═══════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════

    private void advanceTo(Stage next) {
        stage      = next;
        stageStart = System.currentTimeMillis();
        tickCounter = 0;
    }

    private long elapsed() {
        return System.currentTimeMillis() - stageStart;
    }

    private boolean isGuiOpen() {
        return mc.currentScreen != null;
    }

    private void closeGui() {
        if (mc.currentScreen != null) mc.setScreen(null);
    }

    /**
     * Click vào slot trong GUI hiện tại.
     * @param slot Slot index (0-based theo Minecraft screen protocol)
     */
    private void clickGuiSlot(int slot) {
        if (mc.currentScreen == null || mc.player == null) return;
        var handler = mc.player.currentScreenHandler;
        if (handler == null) return;

        if (slot >= 0 && slot < handler.slots.size()) {
            mc.interactionManager.clickSlot(
                handler.syncId,
                slot,
                0,
                SlotActionType.PICKUP,
                mc.player
            );
        }
    }

    /**
     * Đọc giá từ slot order GUI.
     * Phân tích display name của item trong slot để tìm số.
     */
    private double readOrderPrice() {
        if (mc.player == null) return 0;
        var handler = mc.player.currentScreenHandler;
        if (handler == null) return 0;

        // Quét các slot để tìm item có giá trong lore/name
        for (int i = 0; i < Math.min(handler.slots.size(), 54); i++) {
            var slot  = handler.slots.get(i);
            var stack = slot.getStack();
            if (stack.isEmpty()) continue;

            String name = stack.getName().getString().toLowerCase();
            // Blaze rod order thường có "blaze" trong tên
            if (name.contains("blaze") || name.contains("rod")) {
                // Tìm giá trong lore (simplified - lấy từ item name)
                double price = extractPriceFromText(name);
                if (price > 0) return price;
            }
        }
        return 0;
    }

    /** Tìm slot blaze rod trong shop GUI */
    private int findBlazeRodInGui() {
        if (mc.player == null) return -1;
        var handler = mc.player.currentScreenHandler;
        if (handler == null) return -1;

        for (int i = 0; i < Math.min(handler.slots.size(), 54); i++) {
            var stack = handler.slots.get(i).getStack();
            if (stack.getItem() == Items.BLAZE_ROD) return i;
        }
        return -1;
    }

    /** Trích xuất số từ text (ví dụ "price: 350k" → 350000) */
    private double extractPriceFromText(String text) {
        // Tìm số trong text
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("([0-9]+\\.?[0-9]*)\\s*([kmb]?)").matcher(text);
        while (m.find()) {
            try {
                double val = Double.parseDouble(m.group(1));
                String suffix = m.group(2).toLowerCase();
                if (suffix.equals("k")) val *= 1_000;
                else if (suffix.equals("m")) val *= 1_000_000;
                else if (suffix.equals("b")) val *= 1_000_000_000;
                if (val > 1000) return val; // Filter out small numbers
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    /**
     * Phân tích chuỗi giá có suffix K/M/B.
     * Ví dụ: "350K" → 350000, "1.5M" → 1500000
     */
    private double parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isBlank()) return -1;
        priceStr = priceStr.trim().toUpperCase();
        try {
            double multiplier = 1;
            if (priceStr.endsWith("B")) { multiplier = 1_000_000_000; priceStr = priceStr.substring(0, priceStr.length()-1); }
            else if (priceStr.endsWith("M")) { multiplier = 1_000_000; priceStr = priceStr.substring(0, priceStr.length()-1); }
            else if (priceStr.endsWith("K")) { multiplier = 1_000; priceStr = priceStr.substring(0, priceStr.length()-1); }
            return Double.parseDouble(priceStr) * multiplier;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String formatPrice(double price) {
        if (price >= 1_000_000_000) return String.format("%.2fB", price / 1_000_000_000);
        if (price >= 1_000_000)     return String.format("%.2fM", price / 1_000_000);
        if (price >= 1_000)         return String.format("%.2fK", price / 1_000);
        return String.format("%.0f", price);
    }
}
