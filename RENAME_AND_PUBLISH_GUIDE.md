# 📦 Hướng Dẫn Đổi Tên, Thêm Module, và Public Addon

---

## 🔤 Bước 1 — Đổi Tên Addon

### Các file cần chỉnh sửa:

#### 1. `settings.gradle`
```groovy
rootProject.name = 'ten-addon-cua-ban'  // ← Đổi ở đây
```

#### 2. `build.gradle`
```groovy
version = '1.0.0'
group   = 'com.github.username.addonname'  // ← Đổi package group
```

#### 3. `src/main/resources/fabric.mod.json`
```json
{
  "id":          "ten-addon-cua-ban",           // lowercase, no spaces
  "name":        "Tên Addon Của Bạn",
  "description": "Mô tả addon của bạn.",
  "authors":     ["TênBạn"],
  "entrypoints": {
    "main":   ["com.username.addonname.MyAddon"],
    "meteor": ["com.username.addonname.MyAddon"]
  }
}
```

#### 4. Đổi toàn bộ package (IntelliJ):
- **Chuột phải** vào folder `com/example/addon`
- Chọn **Refactor → Rename**
- Nhập `com.username.addonname`
- IntelliJ sẽ tự cập nhật tất cả import

Hoặc dùng terminal:
```bash
# Tìm và thay thế tất cả
find src -name "*.java" -exec sed -i 's/com\.example\.addon/com.username.addonname/g' {} \;
mv src/main/java/com/example/addon src/main/java/com/username/addonname
```

#### 5. `ExampleAddon.java` → đổi tên file và class:
```java
// Đổi tên class
public class MyAddon extends MeteorAddon {

    // Đổi tên logger
    public static final Logger LOG = LoggerFactory.getLogger("MyAddon");

    // Đổi category
    public static Category CATEGORY;

    @Override
    public void onRegisterCategories() {
        CATEGORY = new Category("MyAddon", Items.NETHER_STAR.getDefaultStack());
    }

    @Override
    public String getPackage() {
        return "com.username.addonname"; // ← Đổi package
    }
}
```

---

## ➕ Bước 2 — Thêm Module Mới

### Template module cơ bản:

```java
package com.username.addonname.modules.misc; // Đổi package

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class MyNewModule extends Module {

    // ── Settings ──────────────────────────────────
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> myBool = sg.add(
        new BoolSetting.Builder()
            .name("my-setting")          // Tên hiện trong GUI
            .description("Mô tả.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> myInt = sg.add(
        new IntSetting.Builder()
            .name("my-number")
            .defaultValue(5)
            .sliderRange(1, 20)
            .build()
    );

    private final Setting<Double> myDouble = sg.add(
        new DoubleSetting.Builder()
            .name("my-double")
            .defaultValue(1.5)
            .sliderRange(0.1, 10.0)
            .build()
    );

    // ── Constructor ───────────────────────────────
    public MyNewModule() {
        super(
            com.username.addonname.MyAddon.CATEGORY, // Dùng category của addon
            "my-new-module",                          // ID (kebab-case)
            "Mô tả module của bạn."                  // Description
        );
    }

    // ── Lifecycle ─────────────────────────────────
    @Override
    public void onActivate() {
        info("Module bật!");  // Chat message
    }

    @Override
    public void onDeactivate() {
        info("Module tắt!");
    }

    // ── Event Handlers ────────────────────────────
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (myBool.get()) {
            // Logic của bạn ở đây
        }
    }
}
```

### Đăng ký module trong `MyAddon.java`:
```java
@Override
public void onMeteorInit() {
    Modules modules = Modules.get();
    // ... modules khác ...
    modules.add(new MyNewModule()); // ← Thêm dòng này
}
```

### Các Setting types có sẵn:
| Type | Class | Mô tả |
|------|-------|-------|
| Boolean | `BoolSetting` | Toggle on/off |
| Integer | `IntSetting` | Số nguyên + slider |
| Double | `DoubleSetting` | Số thực + slider |
| String | `StringSetting` | Text input |
| Enum | `EnumSetting<E>` | Dropdown chọn |
| Color | `ColorSetting` | Color picker |
| Item | `ItemSetting` | Chọn Minecraft item |
| EntityType | `EntityTypeListSetting` | Danh sách entity |
| StringList | `StringListSetting` | Danh sách text |
| ModuleList | `ModuleListSetting` | Danh sách module |
| Keybind | `KeybindSetting` | Phím tắt |

### Các Event có sẵn:
| Event | Khi nào trigger |
|-------|----------------|
| `TickEvent.Pre/Post` | Mỗi game tick |
| `Render3DEvent` | Render 3D world |
| `Render2DEvent` | Render HUD/2D |
| `PacketEvent.Send/Receive` | Gửi/nhận packet |
| `AttackEntityEvent` | Tấn công entity |
| `ChunkDataEvent` | Chunk data về |
| `BlockInteractEvent` | Click block |

---

## 🌐 Bước 3 — Public Addon (GitHub)

### 3.1 Tạo GitHub Repository

1. Vào **github.com** → **New repository**
2. Đặt tên: `ten-addon-cua-ban` (lowercase)
3. Chọn **Public**
4. Nhấn **Create repository**

### 3.2 Push code lên GitHub

```bash
cd ten-addon-cua-ban
git init
git add .
git commit -m "Initial release v1.0.0"
git branch -M main
git remote add origin https://github.com/username/ten-addon-cua-ban.git
git push -u origin main
```

### 3.3 Build và tạo Release

```bash
# Build .jar
./gradlew build

# Jar ở đây:
# build/libs/ten-addon-cua-ban-1.0.0.jar
```

1. GitHub → **Releases** → **Draft a new release**
2. Tag: `v1.0.0`
3. Upload file `.jar` (KHÔNG upload file sources.jar)
4. Nhấn **Publish release**

### 3.4 README.md cho người dùng

Tạo file `README.md` với nội dung:
```markdown
# Tên Addon Của Bạn

Addon cho Meteor Client 0.5.8+ trên Minecraft 1.21.4.

## Cài đặt

1. Tải [Fabric Loader](https://fabricmc.net)
2. Tải [Meteor Client](https://meteorclient.com)
3. Tải addon từ [Releases](link)
4. Thả .jar vào folder `/mods`
5. Chạy game!

## Modules
(liệt kê modules)

## Yêu cầu
- Minecraft 1.21.4
- Fabric Loader 0.16+
- Meteor Client 0.5.8+
```

---

## 🔒 Bước 4 — Đảm Bảo Không Có RAT/Malware

### Addon này an toàn vì:

✅ **Không có HTTP request ẩn** — Webhook chỉ gửi khi user bật setting và điền URL  
✅ **Không lưu credentials** — Không đọc/lưu session token, API key  
✅ **Không exec/reflection** — Không dùng `Runtime.exec()`, `ProcessBuilder`, `Class.forName()` từ remote  
✅ **Không download code** — Không load .class file từ internet  
✅ **Open source hoàn toàn** — User có thể đọc và verify từng dòng  
✅ **Không obfuscation** — Source code rõ ràng, có comment  

### Checklist an toàn khi thêm feature mới:

```
□ Không gọi URL ẩn (hardcoded webhook/API không phải của user)
□ Không đọc file ngoài .minecraft/
□ Không spawn process mới
□ Không serialize/send player data
□ Không dùng reflection để bypass security
□ Tất cả network call phải do USER cấu hình
```

### Công cụ verify addon an toàn:
- **jadx** hoặc **procyon**: Decompile .jar để đọc code
- **Checkmarx / SonarQube**: Static analysis
- **Wireshark**: Monitor network traffic khi chạy

---

## 📁 Cấu Trúc Project Hoàn Chỉnh

```
ten-addon-cua-ban/
├── .github/
│   └── workflows/
│       └── build.yml          ← GitHub Actions auto-build
├── src/main/
│   ├── java/com/username/addonname/
│   │   ├── MyAddon.java        ← Main class
│   │   └── modules/
│   │       ├── combat/         ← PvP modules
│   │       ├── movement/       ← Movement modules
│   │       ├── player/         ← Player utility
│   │       ├── misc/           ← Automation modules
│   │       └── render/         ← ESP & HUD
│   └── resources/
│       └── fabric.mod.json
├── build.gradle
├── settings.gradle
├── README.md
└── LICENSE                    ← Thêm license (MIT recommended)
```

### GitHub Actions auto-build (`build.yml`):
```yaml
name: Build
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: ./gradlew build
      - uses: actions/upload-artifact@v4
        with:
          name: addon-jar
          path: build/libs/*.jar
```
