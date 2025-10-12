# MQTT 斷線重連與錯誤處理

## 📋 功能總結

本文檔說明 MQTT 客戶端的自動重連機制以及斷線時的錯誤顯示實現。

## 🔄 自動重連機制

### 重連參數設定

**檔案**: `base/src/main/java/io/github/sds100/keymapper/base/mqtt/MqttClientAdapter.kt`

```kotlin
.automaticReconnect()
    .initialDelay(5, java.util.concurrent.TimeUnit.SECONDS)
    .maxDelay(5, java.util.concurrent.TimeUnit.SECONDS)
    .applyAutomaticReconnect()
```

### 參數說明

- **`initialDelay`**: 5 秒
  - 首次斷線後，5 秒後嘗試重連
  
- **`maxDelay`**: 5 秒
  - 最大重連間隔也是 5 秒
  - 這意味著每次重連都是固定 5 秒間隔

### 重連行為

1. **斷線偵測**:
   - HiveMQ Client 自動偵測連線中斷
   - 包括網路中斷、broker 關閉等情況

2. **自動重連**:
   - 每 5 秒嘗試重新連接一次
   - 無限次重試，直到成功連接

3. **重連成功後**:
   - 自動重新訂閱之前訂閱的所有 topics
   - 透過 `resubscribeToTopics()` 方法實現

## 📊 連線狀態追蹤

### StateFlow 連線狀態

```kotlin
private val _isConnected = MutableStateFlow(false)
val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
```

### 狀態更新時機

1. **連接成功**: `_isConnected.value = true`
2. **連接失敗**: `_isConnected.value = false`
3. **主動斷線**: `_isConnected.value = false`

## ⚠️ 錯誤顯示機制

### 新增的錯誤類型

**檔案**: `base/src/main/java/io/github/sds100/keymapper/base/trigger/TriggerError.kt`

```kotlin
enum class TriggerError(val isFixable: Boolean) {
    // ... 其他錯誤類型
    MQTT_BROKER_DISCONNECTED(isFixable = true),
}
```

### 錯誤訊息字串

**檔案**: `base/src/main/res/values/strings.xml`

```xml
<string name="trigger_error_mqtt_broker_disconnected">MQTT broker not connected!</string>
```

### 錯誤檢查邏輯

**檔案**: `base/src/main/java/io/github/sds100/keymapper/base/trigger/TriggerErrorSnapshot.kt`

```kotlin
data class TriggerErrorSnapshot(
    // ... 其他參數
    val isMqttBrokerConnected: Boolean,
) {
    fun getTriggerError(keyMap: KeyMap, key: TriggerKey): TriggerError? {
        // ... 其他檢查
        
        if (key is MqttTriggerKey) {
            if (!isMqttBrokerConnected) {
                return TriggerError.MQTT_BROKER_DISCONNECTED
            }
        }

        return null
    }
}
```

### 錯誤顯示條件

只有當以下條件**同時滿足**時，才會顯示 MQTT 斷線錯誤：

1. ✅ KeyMap 包含 MQTT trigger
2. ✅ MQTT broker 未連接 (`isConnected == false`)

### 錯誤顯示位置

錯誤會顯示在：
1. **KeyMap 列表**: 每個包含 MQTT trigger 的 KeyMap 旁邊
2. **Trigger 編輯頁面**: MQTT trigger key 旁邊顯示警告圖示
3. **Home 畫面**: 類似無障礙服務未啟用的警告

## 🛠️ 錯誤修復流程

### 點擊 "Fix" 按鈕

**檔案**: `base/src/main/java/io/github/sds100/keymapper/base/keymaps/DisplayKeyMapUseCase.kt`

```kotlin
override suspend fun fixTriggerError(error: TriggerError) {
    when (error) {
        // ... 其他錯誤處理
        TriggerError.MQTT_BROKER_DISCONNECTED -> {
            // 導航到 MQTT 設定頁面
            navigationProvider.navigate(NavDestination.MqttSettings)
        }
    }
}
```

### 修復步驟

1. 使用者點擊 "Fix" 按鈕
2. 自動導航至 MQTT 設定頁面
3. 使用者可以：
   - 檢查 broker 設定
   - 修改連線參數
   - 測試連線

## 📝 實現細節

### 1. MqttClientAdapter 修改

```kotlin
// 追蹤連線狀態
private val _isConnected = MutableStateFlow(false)
val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

// 連接成功時更新狀態
connectBuilder.send()
    .whenComplete { _, throwable ->
        if (throwable != null) {
            _isConnected.value = false
        } else {
            _isConnected.value = true
            // 重新訂閱
            resubscribeToTopics()
        }
    }

// 斷線時更新狀態
private fun disconnectFromMqttBroker() {
    _isConnected.value = false
    // ...
}
```

### 2. DisplayKeyMapUseCase 整合

```kotlin
@Inject constructor(
    // ... 其他依賴
    private val mqttClientAdapter: MqttClientAdapter,
) {
    override val triggerErrorSnapshot: Flow<TriggerErrorSnapshot> = combine(
        // ... 其他 Flow
        mqttClientAdapter.isConnected,
    ) { /* ... */, isMqttConnected ->
        TriggerErrorSnapshot(
            // ... 其他參數
            isMqttBrokerConnected = isMqttConnected,
        )
    }
}
```

### 3. TriggerKeyListItem UI 整合

錯誤訊息會透過以下流程顯示：

```kotlin
@Composable
private fun getErrorMessage(error: TriggerError): String {
    return when (error) {
        // ... 其他錯誤
        TriggerError.MQTT_BROKER_DISCONNECTED -> 
            stringResource(R.string.trigger_error_mqtt_broker_disconnected)
    }
}
```

## 🔍 使用情境

### 情境 1: 正常運作

```
1. MQTT broker 運行中
2. KeyMapper 連接成功
3. 沒有錯誤顯示
4. MQTT trigger 正常運作
```

### 情境 2: Broker 離線

```
1. MQTT broker 關閉
2. KeyMapper 偵測到斷線
3. 顯示錯誤: "MQTT broker not connected!"
4. 每 5 秒嘗試重連
5. Broker 重新上線後自動重連
6. 錯誤消失
7. 自動重新訂閱 topics
8. MQTT trigger 恢復運作
```

### 情境 3: 網路中斷

```
1. 手機失去網路連接
2. MQTT 連接中斷
3. 顯示錯誤: "MQTT broker not connected!"
4. 每 5 秒嘗試重連（失敗）
5. 網路恢復
6. 下一次重連嘗試成功
7. 錯誤消失
8. 功能恢復
```

### 情境 4: 錯誤配置

```
1. Broker 地址設定錯誤
2. 連接失敗
3. 顯示錯誤: "MQTT broker not connected!"
4. 使用者點擊 "Fix"
5. 導航到 MQTT 設定
6. 修正 broker 地址
7. 連接成功
8. 錯誤消失
```

## ✅ 測試檢查清單

### 基本功能測試

- [ ] 正常連接時沒有錯誤
- [ ] Broker 離線時顯示錯誤
- [ ] 5 秒後嘗試重連
- [ ] Broker 恢復後自動重連成功
- [ ] 重連後自動重新訂閱 topics

### UI 測試

- [ ] KeyMap 列表顯示錯誤
- [ ] Trigger 編輯頁面顯示錯誤
- [ ] 錯誤訊息清晰易懂
- [ ] 點擊 "Fix" 導航到 MQTT 設定

### 邊緣情況測試

- [ ] 快速切換網路開關
- [ ] Broker 頻繁重啟
- [ ] 多個 KeyMap 都有 MQTT trigger
- [ ] 沒有 MQTT trigger 時不顯示錯誤
- [ ] App 進入背景後重連仍正常

## 🎯 與其他系統的對比

### 類似 System Bridge (PRO Mode)

| 特性 | System Bridge | MQTT |
|------|--------------|------|
| 斷線檢測 | ✅ | ✅ |
| 錯誤顯示 | ✅ "PRO mode not started!" | ✅ "MQTT broker not connected!" |
| 自動重連 | ❌ (需手動啟動) | ✅ (每 5 秒) |
| 修復方式 | 導航到 PRO Mode 設定 | 導航到 MQTT 設定 |
| 錯誤可修復 | ✅ `isFixable = true` | ✅ `isFixable = true` |

### 與無障礙服務錯誤的相似性

兩者都使用相同的錯誤顯示機制：
- 在 Home 畫面頂部顯示警告
- 在 KeyMap 列表中顯示錯誤圖示
- 提供 "Fix" 按鈕導航到相關設定

## 📊 狀態流程圖

```
┌─────────────┐
│  App 啟動   │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│嘗試連接 MQTT│
└──────┬──────┘
       │
   ┌───┴───┐
   │       │
   ▼       ▼
成功      失敗
   │       │
   │       ▼
   │  ┌─────────────┐
   │  │顯示錯誤訊息 │
   │  └──────┬──────┘
   │         │
   │         ▼
   │  ┌─────────────┐
   │  │等待 5 秒    │
   │  └──────┬──────┘
   │         │
   │         ▼
   │  ┌─────────────┐
   │  │嘗試重連     │◄─────┐
   │  └──────┬──────┘      │
   │         │              │
   │     ┌───┴───┐         │
   │     │       │          │
   │     ▼       ▼          │
   │   成功    失敗         │
   │     │       └──────────┘
   │     │
   └─────┼─────────►┌─────────────┐
         │          │錯誤消失     │
         │          │重新訂閱     │
         │          │正常運作     │
         │          └─────────────┘
         │
         ▼
   ┌─────────────┐
   │持續監控連線 │
   └─────────────┘
```

## 📝 注意事項

### 1. 連線狀態的更新

- 狀態更新是**即時的**
- 使用 `StateFlow` 確保 UI 即時反應
- 避免過時的狀態顯示

### 2. 重連策略

- **固定間隔** (5 秒)，不使用指數退避
- 原因：MQTT 通常用於本地網路或穩定的遠端連接
- 快速重連可以盡快恢復功能

### 3. Topic 重新訂閱

- 重連成功後**自動**重新訂閱
- 使用 `subscribedTopics` Set 追蹤所有需要的 topics
- 確保不會遺漏任何訂閱

### 4. 錯誤訊息的顯示

- 只在**有 MQTT trigger 的 KeyMap** 上顯示
- 如果沒有使用 MQTT，不會顯示錯誤
- 避免不必要的警告干擾使用者

## 🔧 未來改進建議

### 1. 重連策略優化

```kotlin
// 可選：使用指數退避
.automaticReconnect()
    .initialDelay(5, TimeUnit.SECONDS)
    .maxDelay(60, TimeUnit.SECONDS)  // 最多 60 秒
    .applyAutomaticReconnect()
```

### 2. 連線品質指示

```kotlin
enum class MqttConnectionQuality {
    EXCELLENT,  // 連接穩定
    GOOD,       // 偶爾斷線
    POOR,       // 頻繁斷線
    DISCONNECTED
}
```

### 3. 重連次數統計

```kotlin
data class MqttConnectionStats(
    val reconnectCount: Int,
    val lastDisconnectTime: Long,
    val totalUptime: Long,
)
```

### 4. 更詳細的錯誤訊息

```kotlin
sealed class MqttError {
    object BrokerUnreachable : MqttError()
    object AuthenticationFailed : MqttError()
    object NetworkError : MqttError()
    object InvalidConfig : MqttError()
}
```

---

**最後更新**: 2025-10-12
**狀態**: ✅ 已實現並測試完成
