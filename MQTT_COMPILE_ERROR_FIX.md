# MQTT 功能編譯錯誤修復

## 📝 修復的編譯錯誤

### 錯誤 1: KeyMapListScreen.kt - when 表達式不完整

**錯誤訊息**:
```
'when' expression must be exhaustive. Add the 'MQTT_BROKER_DISCONNECTED' branch or an 'else' branch.
```

**原因**: 
在 `getTriggerErrorMessage()` 函數中，`TriggerError` enum 新增了 `MQTT_BROKER_DISCONNECTED`，但 when 表達式沒有包含這個新的 case。

**修復**:
```kotlin
@Composable
private fun getTriggerErrorMessage(error: TriggerError): String {
    return when (error) {
        // ... 其他錯誤
        TriggerError.MQTT_BROKER_DISCONNECTED -> stringResource(R.string.trigger_error_mqtt_broker_disconnected)
    }
}
```

**檔案**: `base/src/main/java/io/github/sds100/keymapper/base/home/KeyMapListScreen.kt`

---

### 錯誤 2: DisplayKeyMapUseCase.kt - combine 參數數量超過限制

**錯誤訊息**:
```
Argument type mismatch: actual type is 'SuspendFunction6<...>', but 'SuspendFunction1<...>' was expected.
Cannot infer type for this parameter.
```

**原因**: 
Kotlin Flow 的 `combine` 函數最多只支援 5 個參數。我們嘗試組合 6 個 Flow：
1. merge(permissions + ime)
2. purchasesFlow
3. showDpadImeSetupError
4. systemBridgeConnectionState
5. evdevDevices
6. mqttClientAdapter.isConnected ⬅️ 超過限制

**修復**:
使用嵌套的 `combine` 將最後兩個參數組合成一個 Pair：

```kotlin
override val triggerErrorSnapshot: Flow<TriggerErrorSnapshot> = combine(
    merge(
        permissionAdapter.onPermissionsUpdate.onStart { emit(Unit) },
        inputMethodAdapter.chosenIme,
    ),
    purchasesFlow,
    showDpadImeSetupError,
    systemBridgeConnectionState,
    combine(evdevDevices, mqttClientAdapter.isConnected) { devices, mqttConnected ->
        Pair(devices, mqttConnected)
    }
) { _, purchases, showDpadImeSetupError, systemBridgeConnectionState, deviceAndMqtt ->
    TriggerErrorSnapshot(
        // ...
        evdevDevices = deviceAndMqtt.first,
        isMqttBrokerConnected = deviceAndMqtt.second,
    )
}
```

**檔案**: `base/src/main/java/io/github/sds100/keymapper/base/keymaps/DisplayKeyMapUseCase.kt`

**技術說明**:
- Kotlin Flow 的 `combine` 函數有多個重載版本
- 支援 2-5 個參數的直接組合
- 超過 5 個時必須使用嵌套或 `combineTransform`

---

### 錯誤 3: DisplayKeyMapUseCase.kt - NavigateEvent 類型不匹配

**錯誤訊息**:
```
Argument type mismatch: actual type is 'NavDestination.MqttSettings', but 'NavigateEvent' was expected.
```

**原因**: 
`NavigationProvider.navigate()` 有兩個重載版本：
1. `navigate(event: NavigateEvent)` - 需要完整的 NavigateEvent 物件
2. `navigate(key: String, destination: NavDestination<R>)` - 擴展函數

我們錯誤地使用了第一個版本但只傳了 NavDestination。

**修復**:
```kotlin
TriggerError.MQTT_BROKER_DISCONNECTED -> {
    // Navigate to MQTT settings
    navigationProvider.navigate("fix_mqtt_error", NavDestination.MqttSettings)
}
```

**檔案**: `base/src/main/java/io/github/sds100/keymapper/base/keymaps/DisplayKeyMapUseCase.kt`

**技術說明**:
- `navigate(key, destination)` 是 inline suspend 函數
- `key` 參數用於追蹤導航事件和結果
- 在這個場景中，key 是 "fix_mqtt_error"，表示修復 MQTT 錯誤的導航

---

## ✅ 驗證清單

修復後的編譯檢查：

- [x] KeyMapListScreen.kt - when 表達式完整
- [x] DisplayKeyMapUseCase.kt - combine 參數數量正確
- [x] DisplayKeyMapUseCase.kt - NavigateEvent 類型正確

## 🔍 相關檔案

### 修改的檔案

1. **base/src/main/java/io/github/sds100/keymapper/base/home/KeyMapListScreen.kt**
   - 添加 `MQTT_BROKER_DISCONNECTED` case

2. **base/src/main/java/io/github/sds100/keymapper/base/keymaps/DisplayKeyMapUseCase.kt**
   - 修復 combine 嵌套問題
   - 修正 navigate 呼叫方式

### 未修改但相關的檔案

- `base/src/main/java/io/github/sds100/keymapper/base/trigger/TriggerError.kt` - 定義錯誤類型
- `base/src/main/java/io/github/sds100/keymapper/base/trigger/TriggerKeyListItem.kt` - 也有相同的 when 表達式（已修復）
- `base/src/main/java/io/github/sds100/keymapper/base/utils/navigation/NavigationProvider.kt` - 導航系統

## 📚 技術學習點

### 1. Kotlin When 表達式的完整性

Kotlin 的 when 表達式如果用作表達式（有返回值），必須是**完整的**：
```kotlin
// ✅ 完整 - 涵蓋所有 enum 值
return when (error) {
    TriggerError.ERROR1 -> "message1"
    TriggerError.ERROR2 -> "message2"
    // 必須涵蓋所有可能的值
}

// ❌ 不完整 - 缺少某些 enum 值
return when (error) {
    TriggerError.ERROR1 -> "message1"
    // 遺漏 ERROR2 會導致編譯錯誤
}

// ✅ 使用 else 分支
return when (error) {
    TriggerError.ERROR1 -> "message1"
    else -> "default message"
}
```

### 2. Flow.combine 的限制

```kotlin
// ✅ 最多 5 個參數
combine(flow1, flow2, flow3, flow4, flow5) { ... }

// ❌ 超過 5 個參數 - 編譯錯誤
combine(flow1, flow2, flow3, flow4, flow5, flow6) { ... }

// ✅ 解決方案 1: 嵌套 combine
combine(
    flow1, flow2, flow3, flow4,
    combine(flow5, flow6) { a, b -> Pair(a, b) }
) { v1, v2, v3, v4, pair -> ... }

// ✅ 解決方案 2: combineTransform
combineTransform(flow1, flow2, flow3, flow4, flow5, flow6) { ... }
```

### 3. NavigationProvider 的使用

```kotlin
// ❌ 錯誤用法
navigationProvider.navigate(NavDestination.MqttSettings)

// ✅ 正確用法 1: 使用擴展函數
navigationProvider.navigate("unique_key", NavDestination.MqttSettings)

// ✅ 正確用法 2: 使用 NavigateEvent
navigationProvider.navigate(
    NavigateEvent(
        key = "unique_key",
        destination = NavDestination.MqttSettings
    )
)
```

## 🎯 最佳實踐

### 添加新 Enum 值的檢查清單

當添加新的 enum 值時（例如 `TriggerError.MQTT_BROKER_DISCONNECTED`），記得檢查：

1. ✅ **所有 when 表達式**
   - 搜尋: `when (.*TriggerError)`
   - 確保每個 when 都包含新的 case

2. ✅ **錯誤訊息字串**
   - 確保 `strings.xml` 有對應的字串資源

3. ✅ **錯誤修復邏輯**
   - 在 `fixTriggerError()` 中添加處理邏輯

4. ✅ **文檔更新**
   - 更新相關文檔說明新錯誤類型

### Flow 組合的策略

當需要組合多個 Flow 時：

1. **少於 5 個**: 直接使用 `combine`
2. **5-10 個**: 使用嵌套 `combine`，將相關的 Flow 組合成 Pair/Triple
3. **超過 10 個**: 考慮重構，可能設計有問題

---

**最後更新**: 2025-10-12
**狀態**: ✅ 所有編譯錯誤已修復
