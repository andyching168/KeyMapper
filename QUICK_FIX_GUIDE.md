# MQTT Release Build - 快速修復指南

## 🎯 三個主要問題及解決方案

### 1️⃣ Netty 可選依賴缺失

**錯誤**: Missing class io.netty.*, org.slf4j.*, etc.

**解決**: 添加 `-dontwarn` 規則忽略

```proguard
-dontwarn io.netty.channel.epoll.**
-dontwarn io.netty.handler.codec.http.**
-dontwarn org.slf4j.**
# ... (更多在 proguard-rules.pro)
```

### 2️⃣ Gradle JVM 記憶體不足

**錯誤**: Daemon will expire after running out of JVM heap space

**解決**: 增加記憶體到 4GB

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx4096M -XX:MaxMetaspaceSize=1024M ...
```

### 3️⃣ Navigation 序列化失敗

**錯誤**: Cannot find class "TriggerSetupShortcut?"

**解決**: 保留 Navigation enums

```proguard
-keep enum io.github.sds100.keymapper.base.trigger.TriggerSetupShortcut { *; }
-keep enum io.github.sds100.keymapper.base.** { *; }
```

### 4️⃣ JCTools 反射失敗

**錯誤**: NoSuchFieldException: No field consumerIndex in class LE8/b

**解決**: 保留 JCTools queue 欄位

```proguard
-keep class org.jctools.queues.** { *; }
-keepclassmembers class org.jctools.queues.** {
    long consumerIndex;
    long producerIndex;
    *;
}
```

## ✅ 完整流程

### 步驟 1: 停止 Gradle Daemon

```bash
./gradlew --stop
```

### 步驟 2: 清理構建

```bash
./gradlew clean
```

或在 Android Studio: `Build → Clean Project`

### 步驟 3: 重新構建 Release

```bash
./gradlew :app:assembleFreeRelease
```

或在 Android Studio: 
1. `Build Variants → freeRelease`
2. `Build → Build Bundle(s) / APK(s) → Build APK(s)`

### 步驟 4: 安裝測試

```bash
adb install app/build/outputs/apk/free/release/app-free-release.apk
```

## 🔍 驗證清單

啟動 App 並測試：

- [ ] App 啟動無崩潰
- [ ] 能夠進入 Settings
- [ ] 能夠進入 MQTT Settings
- [ ] 能夠創建 KeyMap
- [ ] 能夠添加 MQTT Trigger
- [ ] MQTT 連接正常
- [ ] 訊息觸發正常

## 📊 檔案變更總結

```
修改的檔案:
├── app/proguard-rules.pro      (+70 行 ProGuard 規則)
├── gradle.properties            (記憶體 2GB → 4GB)
└── MQTT_RELEASE_BUILD_FIX.md    (文檔)

新增的規則類別:
├── Netty 可選依賴 (HTTP, WebSocket, SSL, 代理)
├── 壓縮編解碼器 (Brotli, LZ4, LZMA, Zstd)
├── 序列化框架 (Protobuf, Marshalling)
├── 日誌框架 (SLF4J, Log4j)
├── JCTools 並發佇列 (Netty 內部使用，反射訪問)
└── Navigation 序列化 (Enums, Destinations)
```

## 🚨 如果還有問題

### 檢查構建日誌

```bash
./gradlew :app:assembleFreeRelease --info
```

查看詳細輸出，找出具體錯誤。

### 檢查生成的規則

```bash
cat app/build/outputs/mapping/release/missing_rules.txt
```

R8 會建議需要的規則。

### 增加更多記憶體

如果還是 OOM，增加到 6GB：

```properties
org.gradle.jvmargs=-Xmx6144M ...
```

### 停用優化（最後手段）

```proguard
-dontoptimize
-dontobfuscate
```

⚠️ 會增加 APK 大小

## 📝 相關文檔

- 完整文檔: `MQTT_RELEASE_BUILD_FIX.md`
- ProGuard 規則: `app/proguard-rules.pro`
- Gradle 設定: `gradle.properties`

---

**最後更新**: 2025-10-12
**狀態**: ✅ 所有已知問題已修復
