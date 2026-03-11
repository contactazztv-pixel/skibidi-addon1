# ExampleAddon — Meteor Client Addon
> Port từ **Glazed Client** và **Zinc Client** → Meteor Client API thuần túy

---

## 📋 Danh sách Module

| Module | Category | Nguồn | Chức năng |
|--------|----------|-------|-----------|
| `AimAssist` | Combat | Glazed | Auto-aim entity, GrimAC v3 bypass |
| `ShieldBreaker` | Combat | Glazed | Tự phá shield bằng rìu rồi giết |
| `CrystalMacro` | Combat | Glazed | Crystal PvP: đặt + phá End Crystal |
| `AnchorMacro` | Combat | Glazed | Respawn Anchor nether bomb |
| `WindPearlMacro` | Combat | Glazed | Wind Charge + Pearl combo |
| `AutoDoubleHand` | Combat | Glazed | Auto totem offhand sau pop |
| `SpeedBridge` | Movement | Zinc | NinjaBridge tự động pitch ≥70° |
| `ElytraSwapper` | Movement | Zinc | Auto swap Elytra ↔ Chestplate |
| `HoverTotem` | Player | Glazed | Giữ totem offhand, auto-refill |
| `AutoXP` | Player | Zinc | Tự chuyển XP bottle vào hotbar |
| `KeyPearl` | Player | Glazed | Ném Pearl theo keybind |
| `PlayerDetection` | Misc | Glazed | Phát hiện player + Discord webhook |
| `SpawnerProtect` | Misc | Glazed | Đào spawner khi bị đe dọa |
| `HideScoreboard` | Misc | Glazed | Ẩn sidebar scoreboard |
| `PingSpoof` | Misc | Zinc | Delay packet để fake ping |

---

## 🛠️ Cách Build

### Yêu cầu
- **Java 21** (JDK)
- **Gradle 8+** (hoặc dùng Gradle Wrapper)
- Internet để tải dependencies lần đầu

### Bước 1 — Clone / Tải project
```bash
git clone https://github.com/yourname/example-addon
cd example-addon
```

### Bước 2 — Build JAR
```bash
# Linux/macOS
./gradlew build

# Windows
gradlew.bat build
```

JAR output sẽ ở:
```
build/libs/example-addon-1.0.0.jar
```

> Lần đầu build sẽ tải Minecraft + Fabric + Meteor (~500MB), mất vài phút.

### Bước 3 — Copy vào Minecraft
```bash
# Linux/macOS
cp build/libs/example-addon-1.0.0.jar ~/.minecraft/mods/

# Windows
copy build\libs\example-addon-1.0.0.jar %APPDATA%\.minecraft\mods\
```

### Bước 4 — Chạy với Meteor
Khởi động Minecraft với **Fabric 1.21.4** profile có **Meteor Client** đã được cài.

---

## 📂 Cấu trúc Project

```
example-addon/
├── build.gradle                    ← Gradle build script
├── settings.gradle
├── gradle.properties
└── src/main/
    ├── java/com/example/addon/
    │   ├── ExampleAddon.java        ← Main entry point
    │   └── modules/
    │       ├── combat/
    │       │   ├── AimAssist.java
    │       │   ├── ShieldBreaker.java
    │       │   ├── CrystalMacro.java
    │       │   ├── AnchorMacro.java
    │       │   ├── WindPearlMacro.java
    │       │   └── AutoDoubleHand.java
    │       ├── movement/
    │       │   ├── SpeedBridge.java
    │       │   └── ElytraSwapper.java
    │       ├── player/
    │       │   ├── HoverTotem.java
    │       │   ├── AutoXP.java
    │       │   └── KeyPearl.java
    │       └── misc/
    │           ├── PlayerDetection.java
    │           ├── SpawnerProtect.java
    │           ├── HideScoreboard.java
    │           └── PingSpoof.java
    └── resources/
        └── fabric.mod.json          ← Mod metadata
```

---

## 🔧 Import vào IntelliJ IDEA

1. **File → Open** → chọn thư mục `example-addon`
2. Chọn **"Open as Gradle Project"**
3. IntelliJ tự động sync dependencies
4. Chạy task **`genSources`** để có source Minecraft:
   ```
   Gradle panel → Tasks → fabric → genSources
   ```
5. Build: `Ctrl+F9` hoặc chạy task `build`

---

## 🔧 Import vào VS Code

1. Cài extension **Extension Pack for Java**
2. Mở thư mục → chọn **"Open as Java Project"**
3. Terminal: `./gradlew build`

---

## 📝 Thêm Module Mới

```java
// 1. Tạo file mới trong modules/[category]/
public class MyModule extends Module {
    public MyModule() {
        super(ExampleAddon.CATEGORY, "my-module", "Description.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Logic của bạn
    }
}

// 2. Đăng ký trong ExampleAddon.java
modules.add(new MyModule());
```

---

## ⚠️ Lưu ý

- **PingSpoof**: Chỉ delay movement packets. Dùng sai có thể bị kick hoặc desync.
- **PlayerDetection Webhook**: Điền Discord Webhook URL thật vào Settings.
- **SpawnerProtect**: Cần Silk Touch để lấy spawner thật sự.
- Tất cả module đều cần **Meteor Client 0.5.8+** và **Fabric 1.21.4**.

---

## 🏗️ Dependencies

```groovy
minecraft:  1.21.4
fabric-api: 0.110.0+1.21.4
meteor:     0.5.8
java:       21
```
