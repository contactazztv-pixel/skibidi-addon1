package com.example.addon.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * SpeedBridge (NinjaBridge) - Tự động bridge khi nhìn xuống góc 70°+.
 * Phân tích từ: zinc/com/vrzt/zinc/modules/pvp/SpeedBridge.class
 *
 * Logic:
 *   - Khi player nhìn xuống (pitch >= 70°) và đang giữ forward
 *   - Tự động sneak, đặt block dưới chân, và un-sneak
 *   - Không cần mixin invoker (viết thuần Meteor API)
 */
public class SpeedBridge extends Module {

    // ==================== SETTINGS ====================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    /** Tự động đặt block */
    private final Setting<Boolean> autoPlace = sgGeneral.add(
        new BoolSetting.Builder()
            .name("auto-place")
            .description("Automatically place blocks while bridging.")
            .defaultValue(true)
            .build()
    );

    /** Góc pitch tối thiểu để kích hoạt bridge (mặc định 70°) */
    private final Setting<Double> pitchThreshold = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("pitch-threshold")
            .description("Minimum downward pitch to trigger bridge (degrees).")
            .defaultValue(70.0)
            .sliderRange(45.0, 90.0)
            .build()
    );

    /** Sneak khi đặt block để không bị rơi */
    private final Setting<Boolean> autoSneak = sgGeneral.add(
        new BoolSetting.Builder()
            .name("auto-sneak")
            .description("Automatically sneak while placing to prevent falling off edge.")
            .defaultValue(true)
            .build()
    );

    // ==================== INTERNAL STATE ====================
    private boolean isCrouchingForBridge = false;
    private int     placeTimer           = 0;

    // ==================== CONSTRUCTOR ====================
    public SpeedBridge() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "speed-bridge",
            "Ninja bridge: automatically places blocks when looking down 70°+."
        );
    }

    @Override
    public void onDeactivate() {
        releaseKeys();
    }

    // ==================== EVENT HANDLER ====================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        boolean isLookingDown = mc.player.getPitch() >= pitchThreshold.get();
        boolean isMovingForward = mc.options.forwardKey.isPressed();

        if (isLookingDown && isMovingForward) {
            // Kiểm tra block trong tay
            var heldStack = mc.player.getMainHandStack();
            if (!(heldStack.getItem() instanceof BlockItem)) return;

            // Bắt đầu sneak nếu cần
            if (autoSneak.get() && !isCrouchingForBridge) {
                mc.options.sneakKey.setPressed(true);
                isCrouchingForBridge = true;
            }

            // Đặt block dưới chân
            if (autoPlace.get()) {
                placeTimer++;
                if (placeTimer >= 1) { // Mỗi tick hoặc mỗi 2 tick
                    placeBlockBelow();
                    placeTimer = 0;
                }
            }
        } else {
            // Thả sneak khi không bridge
            if (isCrouchingForBridge) {
                releaseKeys();
            }
        }
    }

    // ==================== HELPERS ====================

    /**
     * Đặt block dưới chân player.
     * Tìm block dưới chân và interact vào mặt trên của block thấp hơn.
     */
    private void placeBlockBelow() {
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos below     = playerPos.down();

        // Nếu đã có block dưới chân -> không đặt
        if (!mc.world.getBlockState(below).isAir()) return;

        // Tìm block để đặt lên (block dưới nữa)
        BlockPos support = below.down();
        if (mc.world.getBlockState(support).isAir()) return;

        // Interact vào mặt trên của block support
        Vec3d hitVec = Vec3d.of(support).add(0.5, 1.0, 0.5);

        mc.interactionManager.interactBlock(
            mc.player,
            Hand.MAIN_HAND,
            new BlockHitResult(hitVec, Direction.UP, support, false)
        );
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    /** Thả phím sneak */
    private void releaseKeys() {
        if (isCrouchingForBridge) {
            mc.options.sneakKey.setPressed(false);
            isCrouchingForBridge = false;
        }
    }
}
