package com.example.addon.modules.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * StorageESP (BetterStorageESP) — ESP cho tất cả container/storage blocks.
 * Phân tích từ: zinc/com/vrzt/zinc/modules/esp/BetterStorageESP.class
 *
 * Hỗ trợ:
 *   - Chest / Trapped Chest / Ender Chest
 *   - Barrel
 *   - Shulker Box (mọi màu)
 *   - Furnace / Blast Furnace / Smoker
 *   - Hopper / Dropper / Dispenser
 *   - Tracer lines
 */
public class StorageESP extends Module {

    // ═══════════════════════════════════════
    // SETTING GROUPS
    // ═══════════════════════════════════════
    private final SettingGroup sgGeneral  = settings.getDefaultGroup();
    private final SettingGroup sgColors   = settings.createGroup("Colors");
    private final SettingGroup sgFilter   = settings.createGroup("Filter");
    private final SettingGroup sgTracers  = settings.createGroup("Tracers");

    // ═══════════════════════════════════════
    // SETTINGS — General
    // ═══════════════════════════════════════
    private final Setting<ShapeMode> shapeMode = sgGeneral.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("Render mode for storage ESP.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<Integer> renderDistance = sgGeneral.add(
        new IntSetting.Builder()
            .name("render-distance")
            .description("Maximum render distance (blocks).")
            .defaultValue(64)
            .sliderRange(10, 256)
            .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(
        new BoolSetting.Builder()
            .name("tracers")
            .description("Draw tracer lines to storage blocks.")
            .defaultValue(false)
            .build()
    );

    // ═══════════════════════════════════════
    // SETTINGS — Colors
    // ═══════════════════════════════════════
    private final Setting<SettingColor> chestColor   = sgColors.add(new ColorSetting.Builder().name("chest-color").defaultValue(new SettingColor(255, 165, 0, 150)).build());
    private final Setting<SettingColor> shulkerColor = sgColors.add(new ColorSetting.Builder().name("shulker-color").defaultValue(new SettingColor(160, 32, 240, 150)).build());
    private final Setting<SettingColor> barrelColor  = sgColors.add(new ColorSetting.Builder().name("barrel-color").defaultValue(new SettingColor(139, 90, 43, 150)).build());
    private final Setting<SettingColor> otherColor   = sgColors.add(new ColorSetting.Builder().name("other-color").defaultValue(new SettingColor(128, 128, 128, 150)).build());

    // ═══════════════════════════════════════
    // SETTINGS — Filter
    // ═══════════════════════════════════════
    private final Setting<Boolean> showChests     = sgFilter.add(new BoolSetting.Builder().name("chests").description("Show chests and trapped chests.").defaultValue(true).build());
    private final Setting<Boolean> showEnderChest = sgFilter.add(new BoolSetting.Builder().name("ender-chest").description("Show ender chests.").defaultValue(true).build());
    private final Setting<Boolean> showShulkers   = sgFilter.add(new BoolSetting.Builder().name("shulker-boxes").description("Show shulker boxes.").defaultValue(true).build());
    private final Setting<Boolean> showBarrels    = sgFilter.add(new BoolSetting.Builder().name("barrels").description("Show barrels.").defaultValue(true).build());
    private final Setting<Boolean> showFurnaces   = sgFilter.add(new BoolSetting.Builder().name("furnaces").description("Show furnaces.").defaultValue(false).build());
    private final Setting<Boolean> showHoppers    = sgFilter.add(new BoolSetting.Builder().name("hoppers").description("Show hoppers.").defaultValue(false).build());
    private final Setting<Boolean> showDroppers   = sgFilter.add(new BoolSetting.Builder().name("droppers-dispensers").description("Show droppers and dispensers.").defaultValue(false).build());

    // ═══════════════════════════════════════
    // SETTINGS — Tracers
    // ═══════════════════════════════════════
    private final Setting<SettingColor> tracerColor = sgTracers.add(
        new ColorSetting.Builder()
            .name("tracer-color")
            .defaultValue(new SettingColor(255, 255, 255, 150))
            .visible(tracers::get)
            .build()
    );

    // ═══════════════════════════════════════
    // INTERNAL STATE
    // ═══════════════════════════════════════
    // Map pos → color type ("chest"/"shulker"/"barrel"/"other")
    private final Map<BlockPos, String> storageBlocks = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════
    public StorageESP() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "storage-esp",
            "ESP for all storage blocks (chests, shulkers, barrels, etc.)."
        );
    }

    @Override
    public void onActivate() {
        storageBlocks.clear();
    }

    @Override
    public void onDeactivate() {
        storageBlocks.clear();
    }

    // ═══════════════════════════════════════
    // EVENT HANDLERS
    // ═══════════════════════════════════════

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        WorldChunk chunk = event.chunk;
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();

        // Chạy scan trong thread riêng để không block game
        new Thread(() -> {
            for (int x = startX; x < startX + 16; x++) {
                for (int z = startZ; z < startZ + 16; z++) {
                    for (int y = -64; y < 320; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (mc.world == null) return;
                        try {
                            Block block = mc.world.getBlockState(pos).getBlock();
                            String type = classifyBlock(block);
                            if (type != null) {
                                storageBlocks.put(pos, type);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }, "StorageESP-Scan").start();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        for (var entry : storageBlocks.entrySet()) {
            BlockPos pos  = entry.getKey();
            String  type  = entry.getValue();

            // Kiểm tra khoảng cách
            if (mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > (double) renderDistance.get() * renderDistance.get()) continue;

            // Kiểm tra block vẫn còn
            Block block = mc.world.getBlockState(pos).getBlock();
            if (classifyBlock(block) == null) {
                storageBlocks.remove(pos);
                continue;
            }

            // Chọn màu dựa trên type
            SettingColor color = switch (type) {
                case "chest"  -> chestColor.get();
                case "shulker" -> shulkerColor.get();
                case "barrel" -> barrelColor.get();
                default       -> otherColor.get();
            };

            // Render ESP box
            Box box = new Box(pos);
            event.renderer.box(
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                color, color, shapeMode.get(), 0
            );

            // Render tracer
            if (tracers.get()) {
                Vec3d center = Vec3d.ofCenter(pos);
                event.renderer.line(
                    mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(),
                    center.x, center.y, center.z,
                    tracerColor.get()
                );
            }
        }
    }

    // ═══════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════

    /**
     * Phân loại block có phải storage không.
     * @return type string hoặc null nếu không phải storage
     */
    private String classifyBlock(Block block) {
        if (showChests.get() && (block instanceof ChestBlock || block instanceof TrappedChestBlock)) return "chest";
        if (showEnderChest.get() && block instanceof EnderChestBlock) return "chest";
        if (showShulkers.get() && block instanceof ShulkerBoxBlock) return "shulker";
        if (showBarrels.get() && block instanceof BarrelBlock) return "barrel";
        if (showFurnaces.get() && (block instanceof FurnaceBlock || block instanceof BlastFurnaceBlock || block instanceof SmokerBlock)) return "other";
        if (showHoppers.get() && block instanceof HopperBlock) return "other";
        if (showDroppers.get() && (block instanceof DropperBlock || block instanceof DispenserBlock)) return "other";
        return null;
    }
}
