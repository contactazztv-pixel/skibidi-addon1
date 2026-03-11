package com.example.addon.modules.render;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * TargetHUD — Hiển thị thông tin mục tiêu đang tấn công (HP, tên, armor).
 * Phân tích từ: zinc/com/vrzt/zinc/modules/Render/TargetHUD.class
 *
 * Hiển thị:
 *   - Tên player/entity
 *   - Health bar với animation
 *   - Health số
 *   - Armor durability
 *   - Auto-fade khi không target
 */
public class TargetHUD extends Module {

    // ═══════════════════════════════════════
    // SETTINGS
    // ═══════════════════════════════════════
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender  = settings.createGroup("Render");

    private final Setting<Integer> posX = sgGeneral.add(
        new IntSetting.Builder()
            .name("pos-x")
            .description("Horizontal position on screen.")
            .defaultValue(20)
            .sliderMax(1920)
            .build()
    );

    private final Setting<Integer> posY = sgGeneral.add(
        new IntSetting.Builder()
            .name("pos-y")
            .description("Vertical position on screen.")
            .defaultValue(100)
            .sliderMax(1080)
            .build()
    );

    private final Setting<Double> scale = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("scale")
            .description("HUD scale.")
            .defaultValue(1.0)
            .sliderRange(0.5, 3.0)
            .build()
    );

    private final Setting<Boolean> playersOnly = sgGeneral.add(
        new BoolSetting.Builder()
            .name("players-only")
            .description("Only show HUD when targeting players.")
            .defaultValue(true)
            .build()
    );

    private final Setting<SettingColor> bgColor = sgRender.add(
        new ColorSetting.Builder()
            .name("background")
            .description("Background color.")
            .defaultValue(new SettingColor(20, 20, 20, 160))
            .build()
    );

    private final Setting<SettingColor> outlineColor = sgRender.add(
        new ColorSetting.Builder()
            .name("outline")
            .description("Outline color.")
            .defaultValue(new SettingColor(80, 80, 80, 255))
            .build()
    );

    private final Setting<SettingColor> healthColor = sgRender.add(
        new ColorSetting.Builder()
            .name("health-color")
            .description("Health bar color.")
            .defaultValue(new SettingColor(80, 220, 80, 255))
            .build()
    );

    private final Setting<SettingColor> healthLowColor = sgRender.add(
        new ColorSetting.Builder()
            .name("health-low-color")
            .description("Health bar color when low.")
            .defaultValue(new SettingColor(220, 50, 50, 255))
            .build()
    );

    // ═══════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════
    private static final int    WIDTH  = 160;
    private static final int    HEIGHT = 48;
    private static final long   TIMEOUT = 5000; // ms

    // ═══════════════════════════════════════
    // INTERNAL STATE
    // ═══════════════════════════════════════
    private LivingEntity target     = null;
    private long         lastHitTime = 0;
    private float        healthAnim  = 1f;  // Animasi health bar
    private float        alpha       = 0f;  // Fade in/out
    private float        slide       = 0f;  // Slide animation

    // ═══════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════
    public TargetHUD() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "target-hud",
            "Shows current target's health and info on screen."
        );
    }

    @Override
    public void onDeactivate() {
        target = null;
    }

    // ═══════════════════════════════════════
    // EVENT HANDLERS
    // ═══════════════════════════════════════

    /** Set target khi tấn công entity */
    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!(event.entity instanceof LivingEntity living)) return;
        if (playersOnly.get() && !(living instanceof PlayerEntity)) return;
        setTarget(living);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;

        // Clear target nếu đã timeout hoặc target chết
        if (target != null) {
            if (!target.isAlive() || System.currentTimeMillis() - lastHitTime > TIMEOUT) {
                target = null;
            }
        }

        // Tính alpha
        float targetAlpha = (target != null) ? 1f : 0f;
        alpha = lerp(alpha, targetAlpha, 0.1f);
        if (alpha < 0.01f) return;

        // Tính slide
        slide = lerp(slide, (target != null) ? 0f : -(WIDTH + 10), 0.15f);

        drawHUD(event);
    }

    // ═══════════════════════════════════════
    // RENDER
    // ═══════════════════════════════════════

    private void drawHUD(Render2DEvent event) {
        float sc   = scale.get().floatValue();
        int   x    = (int)(posX.get() + slide * sc);
        int   y    = posY.get();
        int   w    = (int)(WIDTH  * sc);
        int   h    = (int)(HEIGHT * sc);

        var renderer = Renderer2D.COLOR;
        renderer.begin();

        // === Background ===
        Color bg = applyAlpha(bgColor.get(), alpha);
        renderer.quad(x, y, w, h, bg);

        // === Outline ===
        Color ol = applyAlpha(outlineColor.get(), alpha);
        renderer.quad(x, y,         w, 1, ol); // top
        renderer.quad(x, y + h - 1, w, 1, ol); // bottom
        renderer.quad(x, y,         1, h, ol); // left
        renderer.quad(x + w - 1, y, 1, h, ol); // right

        if (target != null) {
            // === Health bar ===
            float maxHp   = target.getMaxHealth();
            float hp      = target.getHealth();
            float hpRatio = maxHp > 0 ? Math.max(0, Math.min(1, hp / maxHp)) : 0;

            // Smooth animation
            healthAnim = lerp(healthAnim, hpRatio, 0.1f);

            // Color interpolation green → red
            Color hpColor = hpRatio > 0.5f
                ? applyAlpha(healthColor.get(), alpha)
                : applyAlpha(healthLowColor.get(), alpha);

            int barX = x + (int)(4 * sc);
            int barY = y + (int)(30 * sc);
            int barW = (int)((WIDTH - 8) * sc);
            int barH = (int)(8 * sc);

            // Bar background
            renderer.quad(barX, barY, barW, barH, new Color(30, 30, 30, (int)(200 * alpha)));
            // Bar fill
            renderer.quad(barX, barY, (int)(barW * healthAnim), barH, hpColor);

            // === Text (vẽ bằng TextRenderer) ===
            // Name
            String name  = target instanceof PlayerEntity p ? p.getName().getString() : target.getType().getName().getString();
            String hpStr = String.format("%.1f / %.1f", hp, maxHp);

            var textRenderer = mc.textRenderer;
            // Vẽ name
            mc.execute(() -> {
                // Scale matrix
                var matrices = new net.minecraft.client.util.math.MatrixStack();
                matrices.push();
                matrices.translate(x + 4 * sc, y + 4 * sc, 0);
                matrices.scale(sc, sc, 1);
                textRenderer.draw(name, 0, 0, applyAlpha(new Color(255, 255, 255), alpha).getPacked(), false,
                    matrices.peek().getPositionMatrix(),
                    mc.getBufferBuilders().getEntityVertexConsumers(),
                    net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
                // HP text
                textRenderer.draw(hpStr, 0, 12, hpColor.getPacked(), false,
                    matrices.peek().getPositionMatrix(),
                    mc.getBufferBuilders().getEntityVertexConsumers(),
                    net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
                matrices.pop();
            });
        }

        renderer.render(event.matrices);
    }

    // ═══════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════

    private void setTarget(LivingEntity entity) {
        target      = entity;
        lastHitTime = System.currentTimeMillis();
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private Color applyAlpha(Color c, float a) {
        return new Color(c.r, c.g, c.b, (int)(c.a * a));
    }
}
