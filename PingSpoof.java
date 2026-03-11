package com.example.addon.modules.misc;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;

import java.util.ArrayList;
import java.util.List;

/**
 * PingSpoof - Trì hoãn gói tin gửi đi để giả mạo ping cao hơn.
 * Phân tích từ: zinc/com/vrzt/zinc/modules/pvp/PingSpoof.class
 *
 * Cảnh báo: Module này có thể gây mất packet hoặc desync.
 * Chỉ dùng cho mục đích testing.
 *
 * Logic:
 *   - Chặn packet khi gửi
 *   - Lưu vào queue với timestamp
 *   - Khi đủ delay, send packet thật
 */
public class PingSpoof extends Module {

    // ==================== SETTINGS ====================
    public enum Mode { Delay, Drop }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(
        new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Delay: hold packets; Drop: discard movement packets.")
            .defaultValue(Mode.Delay)
            .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(
        new IntSetting.Builder()
            .name("delay")
            .description("The delay in milliseconds to spoof ping.")
            .defaultValue(200)
            .sliderMax(2000)
            .build()
    );

    // ==================== INTERNAL STATE ====================
    private record PacketEntry(Packet<?> packet, long sendTime) {}
    private final List<PacketEntry> entries    = new ArrayList<>();
    private final List<Packet<?>>   dontRepeat = new ArrayList<>();

    // ==================== CONSTRUCTOR ====================
    public PingSpoof() {
        super(
            com.example.addon.ExampleAddon.CATEGORY,
            "ping-spoof",
            "Spoof your ping to the given value by delaying outgoing packets."
        );
    }

    @Override
    public void onActivate() {
        entries.clear();
        dontRepeat.clear();
    }

    // ==================== EVENT HANDLERS ====================

    /** Chặn packet gửi đi và lưu vào queue */
    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        Packet<?> packet = event.packet;

        // Tránh loop vô hạn (packet đã được resend)
        if (dontRepeat.contains(packet)) {
            dontRepeat.remove(packet);
            return;
        }

        // Chỉ delay movement packets để tránh desync nghiêm trọng
        if (!shouldDelayPacket(packet)) return;

        if (mode.get() == Mode.Drop) {
            event.setCancelled(true);
            return;
        }

        // Delay mode: lưu packet vào queue
        event.setCancelled(true);
        entries.add(new PacketEntry(packet, System.currentTimeMillis()));
    }

    /** Mỗi tick: gửi packet đã đủ delay */
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (meteordevelopment.meteorclient.MeteorClient.mc.getNetworkHandler() == null) return;

        long now        = System.currentTimeMillis();
        long delayMs    = delay.get();

        // Dùng toArray để tránh ConcurrentModificationException
        var toSend = entries.stream()
            .filter(e -> now - e.sendTime() >= delayMs)
            .toList();

        for (var entry : toSend) {
            entries.remove(entry);
            dontRepeat.add(entry.packet());
            meteordevelopment.meteorclient.MeteorClient.mc.getNetworkHandler()
                .sendPacket(entry.packet());
        }
    }

    // ==================== HELPERS ====================

    /**
     * Chỉ delay player movement packets.
     * Không delay login/handshake/other để tránh kick.
     */
    private boolean shouldDelayPacket(Packet<?> packet) {
        String name = packet.getClass().getSimpleName();
        return name.contains("Move")        // PlayerMoveC2SPacket
            || name.contains("Position")    // PlayerPositionC2SPacket
            || name.contains("Rotation")    // PlayerRotationC2SPacket
            || name.contains("FullC2S");    // PlayerFullC2SPacket
    }
}
