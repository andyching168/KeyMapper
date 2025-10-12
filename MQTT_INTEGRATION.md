# MQTTMapper - MQTT 觸發器整合文檔

## 概述
本專案已成功整合 MQTT 觸發器功能，允許透過 MQTT 訊息來觸發按鍵映射動作。使用者可以將 IoT 設備、智能家居系統或任何 MQTT 客戶端與 KeyMapper 整合，實現自動化控制。

## 功能特點

### 核心功能
- ✅ 完整的 MQTT 客戶端整合（HiveMQ Client v1.3.3）
- ✅ 支援 4 種訊息匹配模式（精確、包含、正則、任意）
- ✅ 自動連接和重連機制
- ✅ 動態主題訂閱管理
- ✅ 與所有現有動作類型完全整合
- ✅ 完整的使用者介面（設定頁面、觸發器配置）

### 訊息匹配模式
1. **精確匹配 (EXACT)** - 訊息必須完全符合指定模式
2. **包含匹配 (CONTAINS)** - 訊息包含指定文字即可觸發
3. **正則表達式 (REGEX)** - 使用正則表達式匹配訊息
4. **任意訊息 (ANY)** - 任何來自該主題的訊息都觸發

## 架構設計

### 1. 資料層
- **MqttTriggerKeyEntity** - Room 資料庫實體
- **MqttTriggerKey** - 領域模型
- **TriggerEntityMapper** - 序列化/反序列化支援

### 2. MQTT 客戶端層
- **MqttClientAdapter** (Singleton)
  - HiveMQ MQTT Client 封裝
  - 連接管理和自動重連
  - 主題訂閱/取消訂閱
  - 訊息事件流（SharedFlow）

### 3. 觸發器處理層
- **KeyMapAlgorithm** - MQTT 訊息匹配邏輯
- **KeyMapDetectionController** - 訊息轉發
- **BaseAccessibilityServiceController** - 生命週期管理

### 4. UI 層
- **MqttSettingsScreen** - 全域 MQTT broker 配置
- **TriggerSetupBottomSheet** - MQTT 觸發器設定介面
- **TriggerKeyOptionsBottomSheet** - 觸發器詳情顯示
- **TriggerDiscoverScreen** - 觸發器類型選擇

## 使用者指南

### 配置 MQTT Broker

1. 開啟 **Settings** → **MQTT Broker Settings**
2. 輸入以下資訊：
   - **Broker Address**: MQTT broker 地址（例如：`broker.hivemq.com`）
   - **Port**: 連接埠（預設：`1883`）
   - **Username**: 使用者名稱（選填）
   - **Password**: 密碼（選填）
3. 設定會自動儲存

### 創建 MQTT 觸發器

1. 建立新的 KeyMap
2. 在觸發器選擇畫面中，選擇 **Network** 分類下的 **MQTT**
3. 配置 MQTT 觸發器：
   - **Broker Settings**: 自動載入全域設定（可修改）
   - **Topic**: MQTT 主題（例如：`home/button/1`）
   - **Message Pattern**: 訊息模式（選填）
   - **Match Type**: 選擇匹配方式
4. 點擊 "Add Trigger" 完成

### 查看和管理觸發器

- 在 KeyMap 列表中，MQTT 觸發器會顯示為 `MQTT: <topic>/<pattern>`
- 點擊觸發器可查看詳細設定（主題、訊息模式、匹配類型）
- 支援長按、雙擊等點擊類型（視觸發器模式而定）

## 技術實現

### MQTT 配置儲存

設定儲存在 DataStore Preferences：
```kotlin
Keys.mqttBrokerUrl      // Broker URL
Keys.mqttBrokerPort     // 連接埠
Keys.mqttUsername       // 使用者名稱
Keys.mqttPassword       // 密碼
```

### 訊息匹配邏輯

```kotlin
// 在 KeyMapAlgorithm 中的實現
val messageMatches = when (triggerKey.matchType) {
    MqttMatchType.EXACT -> triggerKey.messagePattern == message
    MqttMatchType.CONTAINS -> message.contains(triggerKey.messagePattern)
    MqttMatchType.REGEX -> {
        try {
            triggerKey.messagePattern.toRegex().matches(message)
        } catch (e: Exception) {
            false
        }
    }
    MqttMatchType.ANY -> true
}
```

### 自動訂閱管理

系統使用 Kotlin Flow 響應式管理訂閱：

```kotlin
combine(
    detectKeyMapsUseCase.allKeyMapList,
    isPaused,
) { keyMapList, isPaused ->
    if (isPaused) {
        emptySet()
    } else {
        keyMapList
            .filter { it.keyMap.isEnabled }
            .flatMap { it.keyMap.trigger.keys }
            .filterIsInstance<MqttTriggerKey>()
            .map { it.topic }
            .toSet()
    }
}.collect { topics ->
    if (topics.isNotEmpty()) {
        mqttClientAdapter.start()
        mqttClientAdapter.updateSubscriptions(topics)
    } else {
        mqttClientAdapter.stop()
    }
}
```

### 系統流程

```
MQTT Broker
    ↓
MqttClientAdapter (訂閱主題)
    ↓
接收訊息 → 發送 MqttMessageEvent
    ↓
BaseAccessibilityServiceController (監聽)
    ↓
KeyMapDetectionController.onMqttMessage()
    ↓
KeyMapAlgorithm (匹配觸發器)
    ↓
執行對應動作（點擊、滑動、打開 App 等）
```

## 使用案例

### 案例 1: 智能家居整合
```
MQTT 主題: home/door/front
訊息: "opened"
匹配類型: EXACT
動作: 發送通知 "前門已打開"
```

### 案例 2: IoT 感應器觸發
```
MQTT 主題: sensor/motion/living-room
訊息模式: "detected"
匹配類型: CONTAINS
動作: 開啟手電筒
```

### 案例 3: 條件自動化
```
MQTT 主題: work/status
訊息: "meeting"
匹配類型: EXACT
動作: 
  - 啟用勿擾模式
  - 自動回覆訊息
  - 降低音量
```

### 案例 4: 數據監控
```
MQTT 主題: system/cpu/temperature
訊息模式: "^[8-9][0-9]|100$"  (80-100度)
匹配類型: REGEX
動作: 發送警告通知
```

## 測試指南

### 使用公共 MQTT Broker

推薦使用免費的公共 broker 進行測試：
- **HiveMQ**: `broker.hivemq.com:1883`
- **Eclipse**: `mqtt.eclipseprojects.io:1883`
- **Mosquitto**: `test.mosquitto.org:1883`

### 測試工具

1. **MQTT Explorer** (推薦)
   - 視覺化 MQTT 客戶端
   - 支援發送/接收訊息
   - 下載：http://mqtt-explorer.com/

2. **Mosquitto 命令列工具**
   ```bash
   # 發送測試訊息
   mosquitto_pub -h broker.hivemq.com -t "test/keymapper" -m "hello"
   
   # 訂閱主題（查看訊息）
   mosquitto_sub -h broker.hivemq.com -t "test/#"
   ```

### 測試步驟

1. **設定 MQTT Broker**
   - 進入 Settings → MQTT Broker Settings
   - 輸入 `broker.hivemq.com`，Port `1883`
   - 儲存設定

2. **創建測試 KeyMap**
   - 建立新 KeyMap
   - 觸發器：MQTT，主題 `test/keymapper/trigger`
   - 訊息模式：`hello`，匹配類型：EXACT
   - 動作：顯示 Toast "MQTT 觸發成功！"

3. **發送測試訊息**
   - 使用 MQTT Explorer 或命令列工具
   - 發送訊息到 `test/keymapper/trigger`，內容 `hello`
   - 觀察手機是否顯示 Toast

4. **測試不同匹配模式**
   - CONTAINS: 訊息包含 "temp" 即可觸發
   - REGEX: 使用 `^\d+$` 匹配純數字
   - ANY: 任何訊息都觸發

## 除錯技巧

### 查看連接狀態
```bash
adb logcat | grep "MqttClient"
```

### 查看訊息接收
```bash
adb logcat | grep "MQTT message arrived"
```

### 查看觸發器匹配
```bash
adb logcat | grep "KeyMapAlgorithm.*MQTT"
```

### 常見問題排查

1. **連接失敗**
   - 檢查網路連接
   - 確認 broker 地址和 port 正確
   - 查看 Logcat 錯誤訊息

2. **訊息未觸發**
   - 確認 KeyMap 已啟用
   - 檢查主題是否完全匹配
   - 驗證訊息模式和匹配類型
   - 查看 Logcat 確認訊息是否收到

3. **自動重連**
   - HiveMQ Client 會自動重連
   - 初始延遲 1 秒，最大延遲 30 秒
   - 查看日誌確認重連狀態

## 進階功能規劃

以下功能可在未來版本中實現：

### 1. SSL/TLS 加密連接
- 支援 `ssl://` 和 `wss://` 協議
- 憑證管理和驗證
- 提升安全性

### 2. QoS 設定
- 讓使用者選擇 QoS 級別
- 目前固定為 QoS 1 (AT_LEAST_ONCE)
- 可選 QoS 0 或 QoS 2

### 3. 連接狀態指示
- 在狀態欄或通知中顯示連接狀態
- 連接失敗時的使用者提示
- 重連進度顯示

### 4. 訊息歷史記錄
- 記錄最近收到的 MQTT 訊息
- 幫助除錯和驗證
- 可設定保留數量

### 5. 多 Broker 支援
- 支援連接多個 MQTT broker
- 每個觸發器可選擇不同的 broker
- Broker 群組管理

### 6. 保留訊息處理
- 支援 MQTT retained messages
- 連接後立即接收保留訊息
- 可選擇是否觸發動作

## 技術規格

### 依賴庫
- **HiveMQ MQTT Client**: v1.3.3
- **Kotlin Coroutines**: 用於非同步處理
- **Jetpack Compose**: UI 實現
- **Room Database**: 觸發器儲存
- **DataStore**: 設定儲存

### 權限需求
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 支援的 MQTT 版本
- MQTT v3.1.1
- 使用 HiveMQ Client 的 MQTT v3 實現

### 效能考量
- 使用 SharedFlow 減少記憶體消耗
- 自動訂閱管理避免不必要的連接
- 沒有啟用的 MQTT 觸發器時自動斷開連接

## 常見問題 (FAQ)

### Q: 是否支援 MQTT v5？
A: 目前使用 MQTT v3.1.1。HiveMQ Client 支援 v5，未來可升級。

### Q: 是否需要持續連接到 broker？
A: 是的。只要有啟用的 MQTT 觸發器，系統會保持連接。

### Q: 耗電量如何？
A: 使用 MQTT 的 Keep-Alive 機制（60 秒），耗電量很低。

### Q: 是否仍需要無障礙服務？
A: 是的。無障礙服務用於執行動作（點擊、滑動等）。MQTT 只是新的觸發來源。

### Q: 可以同時使用按鍵觸發和 MQTT 觸發嗎？
A: 可以！兩種觸發方式完全相容，可以在同一個 KeyMap 中混合使用。

### Q: 支援萬用字元主題嗎？
A: 支援！可以使用 MQTT 標準的 `+` 和 `#` 萬用字元。例如：`home/+/temperature` 或 `sensor/#`

## 授權與貢獻

### 使用的開源專案
- **HiveMQ MQTT Client** - Apache License 2.0
- **KeyMapper** - GPL-3.0 License

### 貢獻指南
歡迎提交 Pull Request 或 Issue！

主要開發區域：
- `base/src/main/java/.../mqtt/` - MQTT 客戶端
- `base/src/main/java/.../trigger/` - 觸發器邏輯
- `base/src/main/java/.../settings/` - 設定介面

## 版本歷史

### v1.0 (2025-10-12)
- ✅ 完整的 MQTT 觸發器功能
- ✅ 4 種訊息匹配模式
- ✅ 自動訂閱管理
- ✅ 完整的使用者介面
- ✅ Broker 設定自動載入和儲存
- ✅ 與所有現有動作整合

---

**文檔版本**: 1.0  
**最後更新**: 2025年10月12日  
**適用版本**: KeyMapper with MQTT Support
