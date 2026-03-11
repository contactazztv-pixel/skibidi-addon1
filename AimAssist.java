package com.example.addon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

import java.util.Random;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * AimAssist - Tự động aim vào entity với bypass GrimAC.
 * Phân tích từ: glazed/com/nnpg/glazed/modules/pvp/AimAssist.class
 *
 * Chức năng:
 * - Tự động nhắm vào entity trong tầm
 * - Hỗ trợ bypass Grim Antibleat v3 bằng randomize rotation
 * - Có thể nhắm vào body hoặc head
 */
public class AimAssist extends Module {

    // ==================== SETTING GROUPS ====================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSpeed   = settings.createGroup("Aim Speed");
    private final SettingGroup sgBypass  = settings.createGroup("Grim Bypass");

    // ==================== SETTINGS ====================

    /** Loại entity cần aim */
    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(
        new EntityTypeListSetting.Builder()
            .name("entities")
            .description("Entities to aim at.")
            .defaultValue(EntityType.PLAYER)
            .build()
    );

    /** Tầm nhắm tối đa */
    private final Setting<Double> range = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("range")
            .description("The range at which an entity can be targeted.")
            .defaultValue(4.5)
            .sliderRange(1, 10)
            .build()
    );

    /** Bỏ qua tường */
    private final Setting<Boolean> ignoreWalls = sgGeneral.add(
        new BoolSetting.Builder()
            .name("ignore-walls")
            .description("Whether or not to ignore aiming through walls.")
            .defaultValue(false)
            .build()
    );

    /** Ưu tiên mục tiêu */
    private final Setting<SortPriority> priority = sgGeneral.add(
        new EnumSetting.Builder<SortPriority>()
            .name("priority")
            .description("How to filter targets within range.")
            .defaultValue(SortPriority.LowestHealth)
            .build()
    );

    /** Nhắm vào phần nào của body */
    private final Setting<Target> bodyTarget = sgGeneral.add(
        new EnumSetting.Builder<Target>()
            .name("aim-target")
            .description("Which part of the entities body to aim at.")
            .defaultValue(Target.Body)
            .build()
    );

    /** Thông báo trong chat */
    private final Setting<Boolean> notifications = sgGeneral.add(
        new BoolSetting.Builder()
            .name("notifications")
            .description("Show chat feedback.")
            .defaultValue(false)
            .build()
    );

    // --- Aim Speed group ---

    /** Nhìn ngay tức thì (không lerp) */
    private final Setting<Boolean> instant = sgSpeed.add(
        new BoolSetting.Builder()
            .name("instant-look")
            .description("Instantly looks at the entity.")
            .defaultValue(false)
            .build()
    );

    /** Tốc độ aim (độ mỗi tick) */
    private final Setting<Double> speed = sgSpeed.add(
        new DoubleSetting.Builder()
            .name("speed")
            .description("How fast to aim at the entity.")
            .defaultValue(5.0)
            .sliderRange(0.1, 30.0)
            .visible(() -> !instant.get())
            .build()
    );

    // --- Grim Bypass group ---

    /** Thêm noise ngẫu nhiên để bypass AC */
    private final Setting<Boolean> randomizeRotation = sgBypass.add(
        new BoolSetting.Builder()
            .name("randomize-rotation")
            .description("Add random noise to rotations to bypass anticheat.")
            .defaultValue(false)
            .build()
    );

    /** Mức độ noise */
    private final Setting<Double> randomNoise = sgBypass.add(
        new DoubleSetting.Builder()
            .name("random-noise")
            .description("Maximum random noise in degrees added to rotation.")
            .defaultValue(0.5)
            .sliderRange(0.0, 3.0)
            .visible(randomizeRotation::get)
            .build()
    );

    /** Giới hạn góc xoay tối đa mỗi tick (Grim bypass) */
    private final Setting<Double> maxRotationDelta = sgBypass.add(
        new DoubleSetting.Builder()
            .name("max-rotation-delta")
            .description("Maximum yaw/pitch change per tick. Helps bypass Grim rotation checks.")
            .defaultValue(10.0)
            .sliderRange(1.0, 45.0)
            .build()
    );

    // ==================== INTERNAL STATE ====================
    private Entity target;
    private final Random random = new Random();

    // ==================== CONSTRUCTOR ====================
    public AimAssist() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "aim-assist",
            "Automatically aims at entities, with Grim AC v3 bypass."
        );
    }

    // ==================== EVENT HANDLERS ====================

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Tìm mục tiêu tốt nhất dựa trên priority
        target = TargetUtils.get(e -> isValidTarget(e), priority.get());

        if (target == null) return;

        // Tính toán vị trí nhắm
        double targetX = target.getX();
        double targetY;
        double targetZ = target.getZ();

        // Chọn body part
        switch (bodyTarget.get()) {
            case Head -> targetY = target.getY() + target.getEyeHeight(target.getPose()) - 0.2;
            case Feet -> targetY = target.getY();
            default   -> targetY = target.getY() + target.getHeight() / 2.0;
        }

        // Vector từ player đến mục tiêu
        double dx = targetX - mc.player.getX();
        double dy = targetY - mc.player.getEyeY();
        double dz = targetZ - mc.player.getZ();

        // Tính yaw và pitch
        double dist  = Math.sqrt(dx * dx + dz * dz);
        float  yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float  pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        // Thêm noise ngẫu nhiên (Grim bypass)
        if (randomizeRotation.get()) {
            double noise = randomNoise.get();
            yaw   += (random.nextDouble() * 2 - 1) * noise;
            pitch += (random.nextDouble() * 2 - 1) * noise;
        }

        if (instant.get()) {
            // Nhìn thẳng vào mục tiêu
            mc.player.setYaw(yaw);
            mc.player.setPitch(clampPitch(pitch));
        } else {
            // Lerp smooth theo tốc độ
            float currentYaw   = mc.player.getYaw();
            float currentPitch = mc.player.getPitch();

            float deltaYaw   = wrapAngle(yaw - currentYaw);
            float deltaPitch = pitch - currentPitch;

            // Giới hạn max rotation delta (Grim bypass)
            float maxDelta = maxRotationDelta.get().floatValue();
            deltaYaw   = clamp(deltaYaw,   -maxDelta, maxDelta);
            deltaPitch = clamp(deltaPitch, -maxDelta, maxDelta);

            // Áp dụng theo tốc độ
            float factor = (float) Math.min(1.0, speed.get() / 10.0);
            mc.player.setYaw(currentYaw + deltaYaw * factor);
            mc.player.setPitch(clampPitch(currentPitch + deltaPitch * factor));
        }
    }

    // ==================== HELPERS ====================

    /**
     * Kiểm tra entity có hợp lệ để aim không.
     */
    private boolean isValidTarget(Entity entity) {
        if (entity == mc.player) return false;
        if (!entity.isAlive()) return false;
        if (!entities.get().contains(entity.getType())) return false;

        double distance = mc.player.distanceTo(entity);
        if (distance > range.get()) return false;

        if (!ignoreWalls.get() && !mc.player.canSee(entity)) return false;

        return true;
    }

    /** Clamp pitch vào khoảng [-90, 90] */
    private float clampPitch(float pitch) {
        return Math.max(-90f, Math.min(90f, pitch));
    }

    /** Normalize góc vào khoảng [-180, 180] */
    private float wrapAngle(float angle) {
        angle = angle % 360f;
        if (angle > 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }

    /** Clamp giá trị float */
    private float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }
}
