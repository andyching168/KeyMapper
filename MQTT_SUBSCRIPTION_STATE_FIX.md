# MQTT 訂閱狀態管理修復

## 🐛 問題描述

### 症狀
```
2025-10-12 15:41:43.556 MqttClientAdapter W  Cannot subscribe to topic carTasker/message: MQTT client not connected, state=CONNECTING
2025-10-12 15:41:43.731 MqttClientAdapter I  Connected to MQTT broker successfully
2025-10-12 15:41:43.743 MqttClientAdapter D  Re-subscribed to MQTT topic: carTasker/message
```

**問題**: 重連後雖然顯示"Re-subscribed"，但實際上**無法收到訊息**。

### 根本原因

原來的實現有嚴重的狀態管理問題：

1. **時序問題**:
   ```
   訂閱請求 → client 還在 CONNECTING
   → 加入 subscribedTopics (錯誤!)
   → 連接成功
   → 重新訂閱 (但 topic 已經在 subscribedTopics 中)
   → 可能重複訂閱或跳過
   ```

2. **狀態混亂**:
   - `subscribedTopics` 既用來追蹤**期望狀態**（想訂閱什麼）
   - 又用來追蹤**實際狀態**（已經訂閱什麼）
   - 兩者混在一起導致邏輯錯誤

3. **重複添加**:
   ```kotlin
   // 連接前
   subscribedTopics.add(topic)  // ❌ 還沒真的訂閱
   
   // 訂閱成功後
   subscribedTopics.add(topic)  // ❌ 重複添加
   ```

## ✅ 解決方案

### 核心概念：分離期望狀態和實際狀態

```kotlin
// 期望狀態：我們想訂閱哪些 topics
private val desiredTopics = mutableSetOf<String>()

// 實際狀態：真正已訂閱的 topics
private val subscribedTopics = mutableSetOf<String>()
```

### 狀態轉換流程

```
使用者請求訂閱 topic
    ↓
加入 desiredTopics
    ↓
檢查連接狀態
    ↓
┌─────────┬─────────┐
│未連接    │已連接    │
│等待      │立即訂閱  │
└────┬────┴────┬────┘
     │         │
     │         ↓
     │    訂閱成功
     │         ↓
     │    加入 subscribedTopics
     │         
     ↓         
連接建立
     ↓
清空 subscribedTopics
     ↓
遍歷 desiredTopics
     ↓
全部重新訂閱
     ↓
成功後加入 subscribedTopics
```

## 🔧 修復的代碼

### 1. 添加兩個狀態集合

```kotlin
// Topics that we want to subscribe to (desired state)
private val desiredTopics = mutableSetOf<String>()

// Topics that are actually subscribed (current state)
private val subscribedTopics = mutableSetOf<String>()
```

### 2. 修復 `subscribeToTopic()`

**修改前**:
```kotlin
if (!client.state.isConnected) {
    // ❌ 錯誤：還沒訂閱就加入
    subscribedTopics.add(topic)
    return@launch
}
```

**修改後**:
```kotlin
fun subscribeToTopic(topic: String) {
    // ✅ 總是加入期望狀態
    desiredTopics.add(topic)
    
    if (!client.state.isConnected) {
        // ✅ 只記錄，不加入 subscribedTopics
        Timber.w("Will subscribe when connected")
        return@launch
    }
    
    // ✅ 檢查是否已訂閱，避免重複
    if (subscribedTopics.contains(topic)) {
        return@launch
    }
    
    // ✅ 實際訂閱
    client.subscribeWith()...
        .whenComplete { _, throwable ->
            if (throwable == null) {
                // ✅ 成功後才加入
                subscribedTopics.add(topic)
            }
        }
}
```

### 3. 修復 `resubscribeToTopics()`

**修改前**:
```kotlin
subscribedTopics.forEach { topic ->
    // ❌ 遍歷實際狀態，可能不完整
    mqttClient?.subscribeWith()...
}
```

**修改後**:
```kotlin
private fun resubscribeToTopics() {
    Timber.d("Resubscribing to ${desiredTopics.size} topics: $desiredTopics")
    
    // ✅ 遍歷期望狀態
    desiredTopics.forEach { topic ->
        client.subscribeWith()...
            .whenComplete { _, throwable ->
                if (throwable == null) {
                    // ✅ 成功後加入實際狀態
                    subscribedTopics.add(topic)
                }
            }
    }
}
```

### 4. 修復連接成功處理

**修改前**:
```kotlin
.whenComplete { _, throwable ->
    if (throwable == null) {
        _isConnected.value = true
        resubscribeToTopics()  // ❌ subscribedTopics 可能有殘留
    }
}
```

**修改後**:
```kotlin
.whenComplete { _, throwable ->
    if (throwable == null) {
        _isConnected.value = true
        
        // ✅ 清空實際狀態（重新開始）
        subscribedTopics.clear()
        
        // ✅ 根據期望狀態重新訂閱
        resubscribeToTopics()
    }
}
```

### 5. 修復 `updateSubscriptions()`

**修改前**:
```kotlin
fun updateSubscriptions(topics: Set<String>) {
    // ❌ 與實際狀態比較
    val topicsToAdd = topics - subscribedTopics
    val topicsToRemove = subscribedTopics - topics
}
```

**修改後**:
```kotlin
fun updateSubscriptions(topics: Set<String>) {
    // ✅ 與期望狀態比較
    val currentDesired = desiredTopics.toSet()
    val topicsToAdd = topics - currentDesired
    val topicsToRemove = currentDesired - topics
}
```

### 6. 修復斷線處理

**修改後**:
```kotlin
private fun disconnectFromMqttBroker() {
    _isConnected.value = false
    
    // ✅ 清空實際狀態（已斷線，沒有訂閱）
    subscribedTopics.clear()
    // ✅ 保留期望狀態（重連時會用到）
    
    mqttClient?.disconnect()...
}
```

## 📊 狀態圖

### 完整的狀態管理流程

```
初始狀態
desiredTopics: []
subscribedTopics: []
connected: false

    ↓
    
使用者創建 MQTT trigger (topic: "test/topic")
    ↓
updateSubscriptions(["test/topic"])
    ↓
subscribeToTopic("test/topic")
    ↓
desiredTopics: ["test/topic"]  ← 加入期望
subscribedTopics: []           ← 還沒訂閱
connected: false (CONNECTING)
    ↓
連接失敗，記錄警告 ⚠️
"Will subscribe when connected"
    ↓
5 秒後重試...
    ↓
連接成功! 🎉
    ↓
subscribedTopics.clear()       ← 清空實際狀態
resubscribeToTopics()
    ↓
遍歷 desiredTopics: ["test/topic"]
    ↓
實際訂閱 "test/topic"
    ↓
訂閱成功! ✅
    ↓
desiredTopics: ["test/topic"]
subscribedTopics: ["test/topic"]  ← 現在一致了
connected: true
    ↓
收到訊息 📨
觸發 trigger ⚡
```

## 🔍 重連場景測試

### 場景 1: 啟動時 Broker 離線

```
1. App 啟動
2. 載入 KeyMap (有 MQTT trigger)
3. updateSubscriptions(["topic1"])
   → desiredTopics = ["topic1"]
   → subscribedTopics = []
   
4. 嘗試連接 → 失敗
5. 5 秒後重試...
6. Broker 上線
7. 連接成功
   → subscribedTopics.clear()
   → resubscribeToTopics()
   → 訂閱 "topic1"
   → subscribedTopics = ["topic1"]
   
✅ 結果: 成功訂閱，可以收到訊息
```

### 場景 2: 運行中 Broker 重啟

```
1. 正常運行
   desiredTopics = ["topic1", "topic2"]
   subscribedTopics = ["topic1", "topic2"]
   
2. Broker 重啟 → 連接中斷
   → subscribedTopics.clear()
   → subscribedTopics = []
   
3. 5 秒後自動重連
4. 連接成功
   → subscribedTopics.clear() (已經是空的)
   → resubscribeToTopics()
   → 遍歷 desiredTopics: ["topic1", "topic2"]
   → 訂閱兩個 topic
   → subscribedTopics = ["topic1", "topic2"]
   
✅ 結果: 成功重新訂閱所有 topics
```

### 場景 3: 連接中添加新 Trigger

```
1. 已連接，有一個 trigger
   desiredTopics = ["topic1"]
   subscribedTopics = ["topic1"]
   
2. 使用者創建新 trigger (topic2)
   → updateSubscriptions(["topic1", "topic2"])
   → topicsToAdd = ["topic2"]
   → subscribeToTopic("topic2")
   → desiredTopics = ["topic1", "topic2"]
   
3. 連接正常，立即訂閱
   → subscribedTopics = ["topic1", "topic2"]
   
✅ 結果: 新 topic 立即可用
```

## 🎯 關鍵改進

### Before vs After

| 情況 | 修改前 | 修改後 |
|------|--------|--------|
| **連接前訂閱** | subscribedTopics.add() ❌ | 只加入 desiredTopics ✅ |
| **訂閱成功** | subscribedTopics.add() ❌ (可能重複) | 檢查後再加入 ✅ |
| **重連** | 遍歷 subscribedTopics ❌ | 遍歷 desiredTopics ✅ |
| **狀態清理** | 不清理 ❌ | subscribedTopics.clear() ✅ |
| **比較基準** | subscribedTopics ❌ | desiredTopics ✅ |

### 日誌變化

**修改前**:
```
W Cannot subscribe: not connected, state=CONNECTING
  (topic 被錯誤地加入 subscribedTopics)
I Connected successfully
D Re-subscribed to topic  (可能跳過或重複)
❌ 無法收到訊息
```

**修改後**:
```
W Cannot subscribe: not connected, will subscribe when connected
I Connected successfully
D Resubscribing to 1 topics: [topic]
D Resubscribing to MQTT topic: topic
I Successfully re-subscribed to MQTT topic: topic
✅ 可以收到訊息
```

## 📝 代碼審查重點

### 狀態不變量 (Invariants)

1. **`desiredTopics` ⊇ `subscribedTopics`**
   - 期望狀態總是包含或等於實際狀態
   - 如果 topic 在 subscribedTopics 中，必定在 desiredTopics 中

2. **連接時**: `subscribedTopics.isEmpty() || isConnected`
   - 只有連接時才能有實際訂閱
   - 斷線時 subscribedTopics 應該清空

3. **訂閱時機**: 
   ```kotlin
   if (desiredTopics.contains(topic) && 
       !subscribedTopics.contains(topic) && 
       isConnected) {
       // 應該訂閱
   }
   ```

## 🧪 測試建議

### 單元測試

```kotlin
@Test
fun `subscribeToTopic when not connected should add to desired topics only`() {
    // Given
    mqttClient = null
    
    // When
    adapter.subscribeToTopic("test/topic")
    
    // Then
    assertTrue(desiredTopics.contains("test/topic"))
    assertFalse(subscribedTopics.contains("test/topic"))
}

@Test
fun `resubscribe should use desired topics not subscribed topics`() {
    // Given
    desiredTopics.add("topic1")
    desiredTopics.add("topic2")
    subscribedTopics.clear() // 模擬重連
    
    // When
    resubscribeToTopics()
    
    // Then
    assertEquals(2, subscribedTopics.size)
}
```

### 整合測試

1. **啟動時 Broker 離線**
   - 啟動 App
   - Broker 離線
   - 等待 10 秒
   - Broker 上線
   - 驗證可以收到訊息

2. **運行中斷線重連**
   - 正常運行
   - 關閉 Broker
   - 等待 10 秒
   - 重啟 Broker
   - 驗證可以收到訊息

3. **多次重連**
   - 重複關閉/開啟 Broker 5 次
   - 每次驗證重連後都能收到訊息

---

**修復日期**: 2025-10-12
**狀態**: ✅ 已修復並測試
**影響**: 重連後現在可以正確接收 MQTT 訊息
