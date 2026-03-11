package com.example.addon;

import com.example.addon.modules.combat.*;
import com.example.addon.modules.movement.*;
import com.example.addon.modules.player.*;
import com.example.addon.modules.misc.*;
import com.example.addon.modules.render.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ╔══════════════════════════════════════════════╗
 * ║           ExampleAddon — Main Class          ║
 * ╠══════════════════════════════════════════════╣
 * ║  Port từ Glazed Client + Zinc Client         ║
 * ║  Viết lại hoàn toàn theo Meteor Client API   ║
 * ║  Không phụ thuộc code nội bộ Glazed/Zinc     ║
 * ╚══════════════════════════════════════════════╝
 *
 * Để PUBLIC addon này:
 *   1. Đổi package: com.example.addon → com.yourgithub.addonname
 *   2. Đổi fabric.mod.json: id, name, authors
 *   3. Chạy ./gradlew build
 *   4. Upload .jar lên GitHub Releases
 *   5. Tạo README hướng dẫn cài đặt
 *
 * Để THÊM module mới:
 *   1. Tạo class trong modules/[category]/
 *   2. Extend Module
 *   3. Thêm modules.add(new YourModule()) bên dưới
 */
public class ExampleAddon extends MeteorAddon {

    public static final Logger LOG = LoggerFactory.getLogger("ExampleAddon");

    /** Category chính của addon trong Meteor GUI */
    public static Category CATEGORY;

    @Override
    public void onRegisterCategories() {
        // Icon và tên category — đổi tùy ý
        CATEGORY = new Category("ExAddon", Items.NETHER_STAR.getDefaultStack());
    }

    @Override
    public void onMeteorInit() {
        LOG.info("[ExampleAddon] Loading modules...");
        Modules modules = Modules.get();

        // ╔══════════════════════════════╗
        // ║         COMBAT (6)           ║
        // ╚══════════════════════════════╝
        modules.add(new AimAssist());        // Auto-aim, GrimAC bypass
        modules.add(new ShieldBreaker());    // Axe→kill combo
        modules.add(new CrystalMacro());     // Crystal PvP macro
        modules.add(new AnchorMacro());      // Respawn Anchor bomb
        modules.add(new WindPearlMacro());   // Wind Charge + Pearl combo
        modules.add(new AutoDoubleHand());   // Auto totem offhand

        // ╔══════════════════════════════╗
        // ║       MOVEMENT (2)           ║
        // ╚══════════════════════════════╝
        modules.add(new SpeedBridge());      // NinjaBridge pitch≥70°
        modules.add(new ElytraSwapper());    // Elytra ↔ Chestplate swap

        // ╔══════════════════════════════╗
        // ║         PLAYER (3)           ║
        // ╚══════════════════════════════╝
        modules.add(new HoverTotem());       // Auto refill totem offhand
        modules.add(new AutoXP());           // Move XP bottles to hotbar
        modules.add(new KeyPearl());         // Pearl on keybind

        // ╔══════════════════════════════╗
        // ║          MISC (7)            ║
        // ╚══════════════════════════════╝
        modules.add(new PlayerDetection());  // Player radar + Discord webhook
        modules.add(new SpawnerProtect());   // Auto mine spawners on threat
        modules.add(new HideScoreboard());   // Hide sidebar scoreboard
        modules.add(new PingSpoof());        // Delay packets to fake ping
        modules.add(new AutoOrder());        // Auto order items from shop
        modules.add(new AutoBlazeRodOrder()); // Auto buy/sell blaze rods
        modules.add(new ShopBuyer());        // Auto buy items from PvP shop
        modules.add(new HotbarDropper());    // Buy and drop items repeatedly

        // ╔══════════════════════════════╗
        // ║         RENDER (4)           ║
        // ╚══════════════════════════════╝
        modules.add(new BeehiveESP());       // ESP for full beehives
        modules.add(new PillagerESP());      // ESP + webhook for pillagers
        modules.add(new StorageESP());       // ESP for all storage blocks
        modules.add(new TargetHUD());        // Target HP + info HUD

        LOG.info("[ExampleAddon] {} modules registered successfully.", 22);
    }

    @Override
    public String getPackage() {
        // ⚠️ Đổi thành package của bạn khi publish!
        return "com.example.addon";
    }
}
