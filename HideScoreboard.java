package com.example.addon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * HideScoreboard - Ẩn sidebar scoreboard (bảng điểm bên phải màn hình).
 * Phân tích từ: glazed/com/nnpg/glazed/modules/main/HideScoreboard.class
 *
 * Logic đơn giản:
 *   - Mỗi tick: set display objective của sidebar thành null
 *   - Khi disable: khôi phục objective cũ
 */
public class HideScoreboard extends Module {

    // Lưu objective gốc để khôi phục khi tắt
    private ScoreboardObjective savedObjective = null;

    // Sidebar slot index = 1 (theo Minecraft protocol)
    private static final int SIDEBAR_SLOT = 1;

    // ==================== CONSTRUCTOR ====================
    public HideScoreboard() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "hide-scoreboard",
            "Hides the sidebar scoreboard."
        );
    }

    // ==================== EVENT HANDLER ====================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null) return;

        Scoreboard scoreboard = mc.world.getScoreboard();
        if (scoreboard == null) return;

        // Lưu objective hiện tại lần đầu
        var current = scoreboard.getObjectiveForSlot(
            net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR
        );

        if (current != null) {
            savedObjective = current;
            // Ẩn sidebar bằng cách set về null
            scoreboard.setObjectiveSlot(
                net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR,
                null
            );
        }
    }

    @Override
    public void onDeactivate() {
        // Khôi phục scoreboard khi tắt module
        if (mc.world == null || savedObjective == null) return;

        Scoreboard scoreboard = mc.world.getScoreboard();
        if (scoreboard != null) {
            scoreboard.setObjectiveSlot(
                net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR,
                savedObjective
            );
        }
        savedObjective = null;
    }
}
