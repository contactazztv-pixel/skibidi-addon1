package com.example.addon.modules.render;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * BeehiveESP — ESP cho beehive/bee nest đầy mật ong với tracer.
 * Phân tích từ: glazed/com/nnpg/glazed/modules/esp/BeehiveESP.class
 *
 * Features:
 *   - Vẽ box ESP xung quanh beehive đầy mật (level 5)
 *   - Tracers từ màn hình đến beehive
 *   - Multi-threading cho chunk scan
 *   - Filter theo level mật ong (0-5)
 *   - Filter theo loại (beehive / bee nest)
 */
public class BeehiveESP extends Module {

    // ═══════════════════════════════════════
    // SETTING GROUPS
    // ═══════════════════════════════════════
    private final SettingGroup sgGeneral   = settings.createGroup("General");
    private final SettingGroup sgFiltering = settings.createGroup("Filtering");
    private final SettingGroup sgHiveTypes = settings.createGroup("Hive Types");
    private final SettingGroup sgRange     = settings.createGroup("Range");
    private final SettingGroup sgThreading = settings.createGroup("Threading");

    // ═══════════════════════════════════════
    // SETTINGS — General
    // ═══════════════════════════════════════
    private final Setting<SettingColor> beehiveColor = sgGeneral.add(
        new ColorSetting.Builder()
            .name("beehive-color")
            .description("Full beehive box color.")
            .defaultValue(new SettingColor(255, 165, 0, 150))
            .build()
    );

    private final Setting<ShapeMode> beehiveShapeMode = sgGeneral.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("Beehive box render mode.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<Boolean> tracers = sgGeneral.add(
        new BoolSetting.Builder()
            .name("tracers")
            .description("Draw tracers to full beehives.")
            .defaultValue(false)
            .build()
    );

    private final Setting<SettingColor> tracerColor = sgGeneral.add(
        new ColorSetting.Builder()
            .name("tracer-color")
            .description("Beehive tracer color.")
            .defaultValue(new SettingColor(255, 200, 0, 200))
            .visible(tracers::get)
            .build()
    );

    private final Setting<Boolean> beehiveChat = sgGeneral.add(
        new BoolSetting.Builder()
            .name("chat-feedback")
            .description("Announce full beehives in chat.")
            .defaultValue(false)
            .build()
    );

    // ═══════════════════════════════════════
    // SETTINGS — Filtering (Honey Levels)
    // ═══════════════════════════════════════
    private final Setting<Boolean> includeLevel5 = sgFiltering.add(new BoolSetting.Builder().name("level-5").description("Include full beehives (100% honey - harvestable).").defaultValue(true).build());
    private final Setting<Boolean> includeLevel4 = sgFiltering.add(new BoolSetting.Builder().name("level-4").description("Include beehives with 80% honey.").defaultValue(false).build());
    private final Setting<Boolean> includeLevel3 = sgFiltering.add(new BoolSetting.Builder().name("level-3").description("Include beehives with 60% honey.").defaultValue(false).build());
    private final Setting<Boolean> includeLevel2 = sgFiltering.add(new BoolSetting.Builder().name("level-2").description("Include beehives with 40% honey.").defaultValue(false).build());
    private final Setting<Boolean> includeLevel1 = sgFiltering.add(new BoolSetting.Builder().name("level-1").description("Include beehives with 20% honey.").defaultValue(false).build());
    private final Setting<Boolean> includeLevel0 = sgFiltering.add(new BoolSetting.Builder().name("level-0").description("Include empty beehives (0% honey).").defaultValue(false).build());

    // ═══════════════════════════════════════
    // SETTINGS — Hive Types
    // ═══════════════════════════════════════
    private final Setting<Boolean> includeBeehives  = sgHiveTypes.add(new BoolSetting.Builder().name("beehives").description("Include crafted beehives.").defaultValue(true).build());
    private final Setting<Boolean> includeBeeNests  = sgHiveTypes.add(new BoolSetting.Builder().name("bee-nests").description("Include natural bee nests.").defaultValue(true).build());

    // ═══════════════════════════════════════
    // SETTINGS — Range
    // ═══════════════════════════════════════
    private final Setting<Integer> minY = sgRange.add(new IntSetting.Builder().name("min-y").description("Minimum Y level.").defaultValue(-64).sliderRange(-64, 320).build());
    private final Setting<Integer> maxY = sgRange.add(new IntSetting.Builder().name("max-y").description("Maximum Y level.").defaultValue(320).sliderRange(-64, 320).build());

    // ═══════════════════════════════════════
    // SETTINGS — Threading
    // ═══════════════════════════════════════
    private final Setting<Boolean> useThreading   = sgThreading.add(new BoolSetting.Builder().name("enable-threading").description("Use multi-threading for chunk scanning (better performance).").defaultValue(true).build());
    private final Setting<Integer> threadPoolSize  = sgThreading.add(new IntSetting.Builder().name("thread-pool-size").description("Number of threads to use for scanning.").defaultValue(2).sliderRange(1, 8).visible(useThreading::get).build());

    // ═══════════════════════════════════════
    // INTERNAL STATE
    // ═══════════════════════════════════════
    private final Set<BlockPos> beehivePositions = ConcurrentHashMap.newKeySet();
    private ExecutorService threadPool;

    // ═══════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════
    public BeehiveESP() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "beehive-esp",
            "ESP for beehives and bee nests when full of honey with threading and tracer support."
        );
    }

    @Override
    public void onActivate() {
        beehivePositions.clear();
        if (useThreading.get()) {
            threadPool = Executors.newFixedThreadPool(threadPoolSize.get());
        }
    }

    @Override
    public void onDeactivate() {
        beehivePositions.clear();
        if (threadPool != null) { threadPool.shutdownNow(); threadPool = null; }
    }

    // ═══════════════════════════════════════
    // EVENT HANDLERS
    // ═══════════════════════════════════════

    /** Khi chunk data về: scan chunk đó tìm beehive */
    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        WorldChunk chunk = event.chunk;
        if (useThreading.get() && threadPool != null) {
            threadPool.submit(() -> scanChunk(chunk));
        } else {
            scanChunk(chunk);
        }
    }

    /** Render ESP boxes và tracers */
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;

        for (BlockPos pos : beehivePositions) {
            // Kiểm tra block vẫn còn tồn tại
            if (mc.world == null) continue;
            var state = mc.world.getBlockState(pos);
            if (!isBeehiveBlock(state.getBlock())) {
                beehivePositions.remove(pos);
                continue;
            }

            // Kiểm tra honey level
            if (!isLevelIncluded(state)) continue;

            // Vẽ ESP box
            Box box = new Box(pos);
            event.renderer.box(
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                beehiveColor.get(), beehiveColor.get(),
                beehiveShapeMode.get(), 0
            );

            // Vẽ tracer
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

    /** Scan toàn bộ chunk tìm beehive */
    private void scanChunk(WorldChunk chunk) {
        if (mc.world == null) return;
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();

        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int y = minY.get(); y <= maxY.get(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    try {
                        var block = mc.world.getBlockState(pos);
                        if (isBeehiveBlock(block.getBlock()) && isLevelIncluded(block)) {
                            beehivePositions.add(pos);
                            if (beehiveChat.get()) {
                                mc.execute(() -> info("§e[BeehiveESP] §fFound full beehive at §a" + pos.toShortString()));
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private boolean isBeehiveBlock(net.minecraft.block.Block block) {
        boolean isHive = (includeBeehives.get() && block == Blocks.BEEHIVE);
        boolean isNest = (includeBeeNests.get() && block == Blocks.BEE_NEST);
        return isHive || isNest;
    }

    private boolean isLevelIncluded(net.minecraft.block.BlockState state) {
        if (!state.contains(BeehiveBlock.HONEY_LEVEL)) return false;
        int level = state.get(BeehiveBlock.HONEY_LEVEL);
        return switch (level) {
            case 5 -> includeLevel5.get();
            case 4 -> includeLevel4.get();
            case 3 -> includeLevel3.get();
            case 2 -> includeLevel2.get();
            case 1 -> includeLevel1.get();
            case 0 -> includeLevel0.get();
            default -> false;
        };
    }
}
