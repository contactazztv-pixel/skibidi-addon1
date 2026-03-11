package com.example.addon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * WindPearlMacro - Tự động dùng Wind Charge rồi ném Pearl (combo).
 * Phân tích từ: glazed/com/nnpg/glazed/modules/pvp/WindPearlMacro.class
 *
 * Logic:
 *   1. Tìm Wind Charge trong hotbar
 *   2. Switch sang và use Wind Charge (tạo knockup)
 *   3. Sau delay, tìm Ender Pearl trong hotbar
 *   4. Switch và ném Pearl (để fly xa hơn nhờ boost của Wind)
 */
public class WindPearlMacro extends Module {

    // ==================== SETTINGS ====================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    /** Hotbar slot của Pearl (0-8, -1 = tự tìm) */
    private final Setting<Integer> pearlSlot = sgGeneral.add(
        new IntSetting.Builder()
            .name("pearl-slot")
            .description("Hotbar slot of Ender Pearl (0-8, -1 = auto find).")
            .defaultValue(-1)
            .sliderRange(-1, 8)
            .build()
    );

    /** Hotbar slot của Wind Charge (0-8, -1 = tự tìm) */
    private final Setting<Integer> windSlot = sgGeneral.add(
        new IntSetting.Builder()
            .name("wind-slot")
            .description("Hotbar slot of Wind Charge (0-8, -1 = auto find).")
            .defaultValue(-1)
            .sliderRange(-1, 8)
            .build()
    );

    /** Delay giữa Wind Charge và Pearl (tick) */
    private final Setting<Integer> delay = sgGeneral.add(
        new IntSetting.Builder()
            .name("delay")
            .description("Ticks between Wind Charge use and Pearl throw.")
            .defaultValue(3)
            .sliderRange(0, 20)
            .build()
    );

    /** Tự toggle off sau khi thực hiện */
    private final Setting<Boolean> autoDisable = sgGeneral.add(
        new BoolSetting.Builder()
            .name("auto-disable")
            .description("Automatically disable after combo.")
            .defaultValue(true)
            .build()
    );

    // ==================== INTERNAL STATE ====================
    private enum State { IDLE, WIND, WAIT, PEARL }
    private State state       = State.IDLE;
    private int   tickCounter = 0;
    private int   prevSlot    = 0;

    // ==================== CONSTRUCTOR ====================
    public WindPearlMacro() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "wind-pearl-macro",
            "Uses Wind Charge then throws Ender Pearl for a big boost."
        );
    }

    @Override
    public void onActivate() {
        state = State.WIND;
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
        if (mc.player == null) return;

        switch (state) {

            case WIND -> {
                // Tìm và dùng Wind Charge
                int wSlot = windSlot.get() >= 0 ? windSlot.get() : findInHotbar(Items.WIND_CHARGE);
                if (wSlot == -1) {
                    error("No Wind Charge found in hotbar.");
                    toggle();
                    return;
                }
                useWindCharge(wSlot);
                tickCounter = 0;
                state = State.WAIT;
            }

            case WAIT -> {
                tickCounter++;
                if (tickCounter >= delay.get()) {
                    state = State.PEARL;
                }
            }

            case PEARL -> {
                // Tìm và ném Pearl
                int pSlot = pearlSlot.get() >= 0 ? pearlSlot.get() : findInHotbar(Items.ENDER_PEARL);
                if (pSlot == -1) {
                    error("No Ender Pearl found in hotbar.");
                } else {
                    throwPearl(pSlot);
                }

                // Trở về slot gốc
                mc.player.getInventory().selectedSlot = prevSlot;

                if (autoDisable.get()) toggle();
                state = State.IDLE;
            }
        }
    }

    // ==================== HELPERS ====================

    /** Dùng Wind Charge từ slot cho trước */
    private void useWindCharge(int slot) {
        mc.player.getInventory().selectedSlot = slot;
        // Gửi packet use item
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    /** Ném Ender Pearl từ slot cho trước */
    private void throwPearl(int slot) {
        mc.player.getInventory().selectedSlot = slot;
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    /** Tìm item trong hotbar (0-8), trả về -1 nếu không thấy */
    private int findInHotbar(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }
}
