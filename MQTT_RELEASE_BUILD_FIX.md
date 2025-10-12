# MQTT Release Build - R8 ProGuard 修復

## 🐛 問題

在構建 Release 版本時，R8 代碼壓縮器報告 HiveMQ MQTT Client 的依賴項缺失錯誤。

### 錯誤訊息

```
Missing class io.netty.channel.epoll.Epoll
Missing class io.netty.handler.codec.http.websocketx.**
Missing class org.slf4j.**
Missing class org.apache.log4j.**
... (60+ missing classes)
```

## 🔍 原因分析

1. **HiveMQ MQTT Client 依賴 Netty**
   - Netty 是一個網路框架
   - 支援多種可選功能（WebSocket、代理、SSL/TLS 等）

2. **Netty 的可選依賴**
   - `io.netty.channel.epoll.**` - Linux 特定的高效能網路（Android 不支援）
   - `io.netty.handler.codec.http.websocketx.**` - WebSocket 支援（我們只用純 TCP MQTT）
   - `io.netty.handler.proxy.**` - 代理支援（不需要）
   - `io.netty.internal.tcnative.**` - 原生 SSL/TLS 加速（不需要）
   - `org.slf4j.**` - SLF4J 日誌框架（可選）
   - `org.apache.log4j.**` - Log4j 日誌框架（可選）
   - `reactor.blockhound.**` - 阻塞偵測工具（開發用）

3. **R8 的行為**
   - R8 在壓縮代碼時檢測到這些類別的引用
   - 但實際運行時不會用到（因為我們只用基本的 TCP MQTT）
   - 需要明確告訴 R8 忽略這些警告

## ✅ 解決方案

已在 `app/proguard-rules.pro` 添加以下規則：

```proguard
# ===== HiveMQ MQTT Client and Netty Rules =====

# Keep HiveMQ MQTT Client classes
-keep class com.hivemq.client.** { *; }
-keepclassmembers class com.hivemq.client.** { *; }

# Keep Netty core classes that are used
-keep class io.netty.** { *; }
-keepclassmembers class io.netty.** { *; }

# Suppress warnings for optional Netty dependencies that we don't use
-dontwarn io.netty.channel.epoll.**
-dontwarn io.netty.handler.codec.http.**
-dontwarn io.netty.handler.codec.http.websocketx.**
-dontwarn io.netty.handler.proxy.**
-dontwarn io.netty.internal.tcnative.**
-dontwarn io.netty.handler.ssl.**

# Suppress warnings for optional logging frameworks
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.**
-dontwarn org.eclipse.jetty.alpn.**
-dontwarn org.eclipse.jetty.npn.**

# Suppress reactor blockhound (not used in Android)
-dontwarn reactor.blockhound.**

# Keep classes that use native methods (Netty buffer operations)
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep Netty's platform detection and resource leak detector
-keep class io.netty.util.internal.PlatformDependent { *; }
-keep class io.netty.util.ResourceLeakDetector { *; }

# Keep Netty ByteBuf allocators
-keep class io.netty.buffer.** { *; }

# Keep classes accessed via reflection by HiveMQ client
-keepattributes Signature,InnerClasses,EnclosingMethod

# Preserve generic signatures for HiveMQ client builders
-keep,allowobfuscation,allowshrinking class * implements com.hivemq.client.mqtt.**

# Keep enums used in HiveMQ client
-keepclassmembers enum com.hivemq.client.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===== End of MQTT/Netty Rules =====
```

## 🎯 規則說明

### 1. Keep 規則
保留 MQTT 和 Netty 核心類別，防止被 R8 移除或混淆：
- HiveMQ MQTT Client 的所有類別
- Netty buffer 操作相關類別
- 平台檢測和資源管理類別

### 2. DontWarn 規則
告訴 R8 忽略這些**可選依賴**的警告：
- ✅ **安全忽略**：這些是可選功能，我們的 MQTT 實作不需要
- ✅ **不影響運行**：只用基本 TCP MQTT 連接

### 3. Native Methods
保留使用 JNI 的方法（Netty 的底層 buffer 操作）

### 4. Reflection Support
保留泛型簽名，確保反射和序列化正常運作

## 🧪 測試步驟

### 1. 清理並重新構建

在 Android Studio 中：
1. **Build → Clean Project**
2. **Build → Rebuild Project**

或使用命令行：
```bash
./gradlew clean
./gradlew :app:assembleFreeRelease
```

### 2. 檢查構建輸出

應該看到：
```
✅ BUILD SUCCESSFUL
✅ No R8 errors about missing classes
⚠️ 可能還有一些 warning（正常，可忽略）
```

### 3. 驗證 APK

```bash
# 檢查 APK 大小（應該在合理範圍）
ls -lh app/build/outputs/apk/free/release/

# 安裝並測試
adb install app/build/outputs/apk/free/release/app-free-release.apk
```

### 4. 功能測試

安裝後測試：
- ✅ MQTT 連接功能正常
- ✅ 訊息接收和匹配正常
- ✅ 觸發器執行正常
- ✅ App 啟動無崩潰

## 📊 影響評估

### APK 大小影響

**預期增加**：~500-800 KB
- HiveMQ MQTT Client: ~400 KB
- Netty (僅保留使用的部分): ~300-400 KB
- 總計: ~700-800 KB

**R8 優化後實際大小**可能更小，因為：
- 未使用的類別會被移除
- 代碼會被壓縮和優化

### 性能影響

✅ **無顯著影響**
- MQTT 客戶端只在需要時啟動
- Netty 的 ByteBuf 操作高效
- 連接池和重用機制

## 🔧 故障排除

### 如果還有錯誤

1. **檢查具體的 missing class**
   ```bash
   # 查看完整的錯誤訊息
   cat app/build/outputs/mapping/release/missing_rules.txt
   ```

2. **添加額外的 -dontwarn 規則**
   ```proguard
   -dontwarn <缺失的類別包名>.**
   ```

3. **檢查是否需要 keep 特定類別**
   - 如果運行時崩潰，可能需要添加 `-keep` 規則
   - 查看崩潰日誌確定需要保留的類別

### 常見錯誤

#### 錯誤 1: ClassNotFoundException at runtime
```
解決：添加 -keep 規則保留該類別
-keep class <完整類名> { *; }
```

#### 錯誤 2: MethodNotFoundException
```
解決：保留特定方法
-keepclassmembers class <類名> {
    <返回類型> <方法名>(...);
}
```

#### 錯誤 3: Reflection 失敗
```
解決：保留反射訪問的類別和屬性
-keepattributes Signature,InnerClasses
-keep class <類名> { *; }
```

## 📝 相關資源

- [HiveMQ Client GitHub](https://github.com/hivemq/hivemq-mqtt-client)
- [Netty ProGuard Rules](https://github.com/netty/netty/wiki/Native-transports#using-proguard)
- [R8 文檔](https://developer.android.com/studio/build/shrink-code)
- [Android ProGuard 規則](https://www.guardsquare.com/manual/configuration/usage)

## ✅ 完成檢查清單

在提交之前確認：

- [x] 添加了完整的 ProGuard 規則
- [x] 增加了 Gradle JVM 記憶體（2GB → 4GB）
- [x] 添加了 Navigation Component 的 keep 規則
- [x] 添加了 JCTools 的 keep 規則（反射訪問）
- [ ] 清理並重新構建成功
- [ ] Release APK 可以安裝並啟動
- [ ] Navigation 正常運作（無崩潰）
- [ ] MQTT 連接正常（無 JCTools 錯誤）
- [ ] MQTT 功能運行正常
- [ ] APK 大小在合理範圍

## 🐛 問題 3: Navigation Component 序列化錯誤

### 錯誤訊息

```
java.lang.IllegalArgumentException: Cannot find class with name 
"io.github.sds100.keymapper.base.trigger.TriggerSetupShortcut?". 
Ensure that the serialName for this argument is the default fully qualified name.
If the build is minified, try annotating the Enum class with 
"androidx.annotation.Keep" to ensure the Enum is not removed.
```

### 原因

R8 混淆了用於 Navigation 參數的 enum 類別，導致：
1. **類別名稱改變**：`TriggerSetupShortcut` → `a`
2. **Serialization 找不到類別**：Navigation 使用完整類別名稱進行序列化
3. **運行時崩潰**：無法恢復導航狀態

### 解決方案

添加 ProGuard 規則保留 Navigation 相關的類別和 enums：

```proguard
# Keep all NavDestination classes
-keep class io.github.sds100.keymapper.base.utils.navigation.NavDestination** { *; }

# Keep all enums used in Navigation arguments
-keep enum io.github.sds100.keymapper.base.trigger.TriggerSetupShortcut { *; }
-keep enum io.github.sds100.keymapper.base.** { *; }

# Keep Navigation serialization
-keepnames class * extends androidx.navigation.** { *; }
```

## � 問題 4: JCTools 反射訪問失敗

### 發生時機

Release APK 安裝並啟動後，嘗試使用 MQTT 功能時崩潰。

### 錯誤訊息

```
FATAL EXCEPTION: main
Process: io.github.sds100.keymapper, PID: 17055
java.lang.ExceptionInInitializerError
	at com.hivemq.client.internal.mqtt.handler.publish.outgoing.MqttOutgoingQosHandler.<init>
	...
Caused by: java.lang.RuntimeException: java.lang.NoSuchFieldException: 
No field consumerIndex in class LE8/b; 
(declaration of 'E8.b' appears in /data/app/.../base.apk!classes3.dex)
	at F8.a.a(Unknown Source:14)
	at E8.b.<clinit>(Unknown Source:4)
	...
Caused by: java.lang.NoSuchFieldException: No field consumerIndex in class LE8/b
	at java.lang.Class.getDeclaredField(Native Method)
	...
```

### 原因分析

1. **JCTools 是什麼**
   - JCTools = Java Concurrency Tools（Java 並發工具）
   - 高效能的無鎖並發佇列實現
   - Netty 內部使用來處理訊息佇列

2. **問題根源**
   - Netty 使用 JCTools 的 `MpscArrayQueue` 等類別
   - 這些類別透過**反射**訪問內部欄位（`consumerIndex`, `producerIndex`）
   - R8 混淆後：
     - 類別名稱：`org.jctools.queues.MpscArrayQueue` → `E8.b`
     - 欄位名稱：`consumerIndex` → 可能被刪除或重命名
     - 反射失敗：`getDeclaredField("consumerIndex")` 找不到欄位

3. **為什麼要用反射**
   - JCTools 使用 `sun.misc.Unsafe` 進行高效能操作
   - 需要透過反射獲取欄位偏移量（field offset）
   - 然後使用 Unsafe 直接操作記憶體

### 解決方案

添加 ProGuard 規則保留 JCTools 的類別和欄位：

```proguard
# Keep JCTools concurrent queues (used by Netty, accessed via reflection)
-keep class org.jctools.queues.** { *; }
-keepclassmembers class org.jctools.queues.** {
    long consumerIndex;
    long producerIndex;
    *;
}

# Keep Netty internal shaded JCTools (if using shaded version)
-keep class io.netty.util.internal.shaded.org.jctools.queues.** { *; }
-keepclassmembers class io.netty.util.internal.shaded.org.jctools.queues.** {
    long consumerIndex;
    long producerIndex;
    *;
}
```

### 為什麼這樣修復有效

1. **`-keep class org.jctools.queues.** { *; }`**
   - 保留所有 JCTools queue 類別不被刪除
   - 保留類別名稱不被混淆

2. **`-keepclassmembers`**
   - 特別保留 `consumerIndex` 和 `producerIndex` 欄位
   - 這兩個欄位是反射訪問的目標
   - 保留所有其他成員（`*;`）以防萬一

3. **處理 shaded 版本**
   - Netty 可能包含 shaded（重新打包）的 JCTools
   - 包名變成 `io.netty.util.internal.shaded.org.jctools.queues.**`
   - 同樣需要保留

### Debug 技巧

如果還有類似問題，查看完整的 stack trace：
```bash
adb logcat | grep -A 50 "NoSuchFieldException"
```

常見的反射訪問模式：
- `getDeclaredField("fieldName")` → 需要 `-keepclassmembers`
- `Class.forName("ClassName")` → 需要 `-keep class`
- `getMethod("methodName")` → 需要 `-keepclassmembers`

## �💾 Gradle 記憶體設定

### 問題：JVM Heap Space 不足

```
The Daemon will expire after running out of JVM heap space.
The currently configured max heap space is '2 GiB'
```

### 解決方案

已在 `gradle.properties` 增加記憶體配置：

```properties
# 從 2GB 增加到 4GB
org.gradle.jvmargs=-Xmx4096M -XX:MaxMetaspaceSize=1024M -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8

# 同時增加 Kotlin daemon 記憶體
-Dkotlin.daemon.jvm.options="-Xmx4096M"

# 啟用優化選項
org.gradle.parallel=true
org.gradle.configureondemand=true
org.gradle.caching=true
```

### 參數說明

- **-Xmx4096M**: 最大堆記憶體 4GB（從 2GB 增加）
- **-XX:MaxMetaspaceSize=1024M**: 元空間最大 1GB（用於類別元數據）
- **-XX:+HeapDumpOnOutOfMemoryError**: OOM 時產生 heap dump（除錯用）
- **-Dfile.encoding=UTF-8**: 確保檔案編碼正確
- **org.gradle.parallel=true**: 啟用平行構建（加快速度）
- **org.gradle.configureondemand=true**: 按需配置專案
- **org.gradle.caching=true**: 啟用構建快取

### 為什麼需要更多記憶體？

R8 在 release 構建時需要：
1. **載入所有類別**（包括 HiveMQ + Netty）
2. **分析依賴關係**（60+ 缺失類別的分析）
3. **代碼壓縮和混淆**（整個 app + 所有依賴）
4. **優化字節碼**（多次優化遍歷）

Netty 是一個**大型框架**，包含：
- Buffer 管理
- 網路協議
- SSL/TLS
- 編解碼器
- 等等

### 記憶體需求估算

| 組件 | 記憶體需求 |
|------|-----------|
| Base Gradle Daemon | ~500MB |
| KeyMapper App | ~800MB |
| Kotlin Compilation | ~500MB |
| R8 分析 (無 MQTT) | ~500MB |
| **R8 分析 (含 Netty)** | **~1.5GB** |
| **總計** | **~3.8GB** |

所以 2GB 不夠，4GB 是安全的選擇。

### 如果還是記憶體不足

如果 4GB 還不夠（unlikely），可以進一步增加：

```properties
# 增加到 6GB（如果你的電腦有足夠 RAM）
org.gradle.jvmargs=-Xmx6144M -XX:MaxMetaspaceSize=1024M ...
```

或者**停用某些 R8 優化**（會增加 APK 大小）：

```proguard
# 在 app/proguard-rules.pro 添加
-dontoptimize
```

### 監控記憶體使用

構建時查看記憶體：
```bash
# 查看 Gradle daemon 狀態
./gradlew --status

# 停止所有 daemon（釋放記憶體）
./gradlew --stop
```

## 🎯 後續建議

### 1. 進一步優化（可選）

如果 APK 太大，可以考慮：

```proguard
# 更激進的優化
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# 但要小心測試，可能導致問題
```

### 2. 分析 APK 大小

使用 Android Studio 的 **Analyze APK** 功能：
1. Build → Analyze APK
2. 選擇生成的 release APK
3. 查看各個依賴的大小占比

### 3. 考慮使用 App Bundle

如果準備發布到 Play Store：
```bash
./gradlew :app:bundleFreeRelease
```

好處：
- Google Play 動態交付
- 更小的下載大小
- 按需下載功能模組

---

**修改記錄**
- 2025-10-12: 初始版本，添加 HiveMQ/Netty ProGuard 規則
