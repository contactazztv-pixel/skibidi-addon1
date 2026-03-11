package com.example.addon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * CrystalMacro - Crystal tự động: đặt và phá End Crystal nhanh.
 * Phân tích từ: glazed/com/nnpg/glazed/modules/pvp/CrystalMacro.class
 *
 * Logic:
 *   - Tick lẻ: đặt crystal lên obsidian/bedrock dưới chân target
 *   - Tick chẵn: phá crystal gần target nhất
 *   - stop-on-kill: dừng 5 giây khi player chết
 *   - place-obsidian-if-missing: tự đặt obsidian nếu không có
 */
public class CrystalMacro extends Module {

    // ==================== SETTINGS ====================
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    /** Key để bật manual crystal (slot index 0-8, -1 = không dùng) */
    private final Setting<Integer> activateKey = sgGeneral.add(
        new IntSetting.Builder()
            .name("activate-key")
            .description("Key that does the crystalling. (-1 = always active when module on)")
            .defaultValue(-1)
            .sliderRange(-1, 8)
            .build()
    );

    /** Delay giữa hai lần đặt crystal (tick) */
    private final Setting<Double> placeDelay = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("place-delay")
            .description("The delay in ticks between placing crystals.")
            .defaultValue(0.0)
            .sliderMax(5.0)
            .build()
    );

    /** Delay giữa hai lần phá crystal (tick) */
    private final Setting<Double> breakDelay = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("break-delay")
            .description("The delay in ticks between breaking crystals.")
            .defaultValue(0.0)
            .sliderMax(5.0)
            .build()
    );

    /** Dừng macro 5s khi có player chết gần đó */
    private final Setting<Boolean> stopOnKill = sgGeneral.add(
        new BoolSetting.Builder()
            .name("stop-on-kill")
            .description("Pauses the macro when a nearby player dies, then resumes after 5 seconds.")
            .defaultValue(true)
            .build()
    );

    /** Tự đặt obsidian nếu không có block phù hợp */
    private final Setting<Boolean> placeObsidianIfMissing = sgGeneral.add(
        new BoolSetting.Builder()
            .name("place-obsidian-if-missing")
            .description("Places obsidian if the target block isn't obsidian or bedrock.")
            .defaultValue(false)
            .build()
    );

    // ==================== INTERNAL STATE ====================
    private double placeDelayCounter = 0;
    private double breakDelayCounter = 0;
    private boolean paused           = false;
    private long    resumeTime       = 0;
    private final Set<PlayerEntity> deadPlayers = new HashSet<>();

    // ==================== CONSTRUCTOR ====================
    public CrystalMacro() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "crystal-macro",
            "Automatically crystals fast for you."
        );
    }

    @Override
    public void onActivate() {
        resetCounters();
    }

    @Override
    public void onDeactivate() {
        deadPlayers.clear();
    }

    // ==================== EVENT HANDLERS ====================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Kiểm tra stop-on-kill
        if (stopOnKill.get()) {
            if (paused) {
                if (System.currentTimeMillis() >= resumeTime) {
                    paused = false;
                    info("§7[§bCrystalMacro§7] §aResumed after stop-on-kill");
                } else return;
            }

            // Kiểm tra player mới chết
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player) continue;
                if (!player.isAlive() && !deadPlayers.contains(player)) {
                    deadPlayers.add(player);
                    paused     = true;
                    resumeTime = System.currentTimeMillis() + 5000;
                    return;
                }
            }
            deadPlayers.removeIf(PlayerEntity::isAlive);
        }

        // Cập nhật delay counters
        placeDelayCounter = Math.max(0, placeDelayCounter - 1);
        breakDelayCounter = Math.max(0, breakDelayCounter - 1);

        // Tìm target gần nhất
        PlayerEntity target = findNearestPlayer();
        if (target == null) return;

        // Tìm crystal của Meteor để phá
        if (breakDelayCounter <= 0) {
            EndCrystalEntity crystal = findNearestCrystal(target);
            if (crystal != null) {
                mc.interactionManager.attackEntity(mc.player, crystal);
                mc.player.swingHand(Hand.MAIN_HAND);
                breakDelayCounter = breakDelay.get();
            }
        }

        // Đặt crystal mới
        if (placeDelayCounter <= 0) {
            // Tìm slot crystal
            int crystalSlot = findItemInHotbar(Items.END_CRYSTAL);
            if (crystalSlot == -1) return;

            // Tìm block để đặt (obsidian/bedrock dưới target)
            BlockPos placePos = findPlacePos(target);
            if (placePos == null) {
                if (placeObsidianIfMissing.get()) {
                    placeObsidian(target);
                }
                return;
            }

            int prevSlot = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = crystalSlot;

            // Đặt crystal
            mc.interactionManager.interactBlock(
                mc.player,
                Hand.MAIN_HAND,
                new BlockHitResult(
                    Vec3d.ofCenter(placePos),
                    Direction.UP,
                    placePos,
                    false
                )
            );

            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.getInventory().selectedSlot = prevSlot;
            placeDelayCounter = placeDelay.get();
        }
    }

    // ==================== HELPERS ====================

    /** Tìm player gần nhất để target */
    private PlayerEntity findNearestPlayer() {
        PlayerEntity nearest  = null;
        double        minDist = Double.MAX_VALUE;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (!player.isAlive()) continue;
            double d = mc.player.distanceTo(player);
            if (d < minDist && d < 6.0) {
                minDist = d;
                nearest = player;
            }
        }
        return nearest;
    }

    /** Tìm end crystal gần target nhất */
    private EndCrystalEntity findNearestCrystal(PlayerEntity target) {
        EndCrystalEntity nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            double d = target.distanceTo(crystal);
            if (d < minDist && d < 5.0) {
                minDist = d;
                nearest = crystal;
            }
        }
        return nearest;
    }

    /**
     * Tìm vị trí đặt crystal (block obsidian/bedrock ở dưới target).
     * Crystal chỉ đặt được trên obsidian hoặc bedrock.
     */
    private BlockPos findPlacePos(PlayerEntity target) {
        BlockPos base = target.getBlockPos().down();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = base.add(dx, 0, dz);
                var block = mc.world.getBlockState(pos).getBlock();
                if ((block == Blocks.OBSIDIAN || block == Blocks.BEDROCK)
                    && mc.world.getBlockState(pos.up()).isAir()
                    && mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)) < 36) {
                    return pos;
                }
            }
        }
        return null;
    }

    /** Đặt obsidian dưới target */
    private void placeObsidian(PlayerEntity target) {
        int obsSlot = findItemInHotbar(Items.OBSIDIAN);
        if (obsSlot == -1) return;

        BlockPos placePos = target.getBlockPos().down();
        if (!mc.world.getBlockState(placePos).isAir()) return;

        // Cần có block bên cạnh để interact
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = placePos.offset(dir);
            if (!mc.world.getBlockState(neighbor).isAir()) {
                int prev = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = obsSlot;
                mc.interactionManager.interactBlock(
                    mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(placePos), dir.getOpposite(), neighbor, false)
                );
                mc.player.swingHand(Hand.MAIN_HAND);
                mc.player.getInventory().selectedSlot = prev;
                break;
            }
        }
    }

    /** Tìm item trong hotbar, trả về index slot hoặc -1 */
    private int findItemInHotbar(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private void resetCounters() {
        placeDelayCounter = 0;
        breakDelayCounter = 0;
        paused            = false;
        deadPlayers.clear();
    }
}
