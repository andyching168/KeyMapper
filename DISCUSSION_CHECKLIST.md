# GitHub Discussion 發布清單

## 📋 發布前準備

### 1. 截圖準備
你需要準備以下截圖（建議使用真實畫面）：

#### 必需截圖：
- [ ] **MQTT Settings 畫面** 
  - 顯示 Broker Address, Port, Username, Password 欄位
  - 建議使用淺色主題，解析度 1080x2400

- [ ] **MQTT Trigger 設定畫面**
  - 顯示完整的觸發器設定流程
  - 包含 Topic, Message Pattern, Match Type 選項

- [ ] **觸發器列表畫面**
  - 顯示 MQTT 觸發器與其他觸發器混合的列表
  - 展示 "MQTT: topic/pattern" 格式

#### 可選截圖：
- [ ] **觸發器詳情畫面** - 顯示 MQTT 觸發器的完整資訊
- [ ] **實際運作影片** - 展示 MQTT 訊息觸發動作的過程

### 2. 文件準備

確認以下文件就緒：
- [x] MQTT_INTEGRATION.md - 技術文檔
- [ ] 截圖檔案（PNG 格式，建議壓縮）
- [ ] Demo APK（可選，但強烈建議）

### 3. 代碼提交（遵循 KeyMapper 規範）

⚠️ **重要**：KeyMapper 要求所有功能開發在 `feature/*` 分支！

#### 步驟 1: 創建 GitHub Issue
在你的 fork 上創建 Issue（或在發布 Discussion 後，在官方倉庫創建）：

**Title**: MQTT Trigger Support for IoT and Smart Home Integration

**Body**: (簡短版，完整版在 Discussion)
```markdown
## Feature Request
Add MQTT trigger support to enable IoT and smart home automation.

## Use Cases
- Smart home integration (Home Assistant, OpenHAB)
- IoT sensor triggers
- Remote control via ESP32/Arduino devices

## Implementation
Complete implementation ready with:
- HiveMQ MQTT Client (Apache 2.0)
- 4 matching modes (Exact, Contains, Regex, Any)
- Full UI integration
- Comprehensive documentation
```

記下 Issue 編號（例如 #123）

#### 步驟 2: 創建 Feature 分支並提交

```bash
cd /home/AC/AndroidStudioProjects/KeyMapper

# 1. 確保在 develop 分支且已同步
git checkout develop
git pull origin develop

# 2. 創建 feature 分支（依照 KeyMapper 命名規範）
git checkout -b feature/mqtt-trigger

# 3. 分階段提交（每個 commit 遵循格式：<issue id> <type>: <subject>）

# Commit 1: 核心數據模型
git add data/src/main/java/io/github/sds100/keymapper/data/db/dao/MqttTriggerKeyDao.kt
git add data/src/main/java/io/github/sds100/keymapper/data/entities/MqttTriggerKeyEntity.kt
git add base/src/main/java/io/github/sds100/keymapper/mappings/trigger/MqttTriggerKey.kt
git add base/src/main/java/io/github/sds100/keymapper/util/MqttMessageEvent.kt
git commit -m "#123 feat: Add MQTT trigger data models and database entities"

# Commit 2: MQTT Client 整合
git add base/build.gradle.kts  # 添加 HiveMQ 依賴
git add base/src/main/java/io/github/sds100/keymapper/system/mqtt/MqttClientAdapter.kt
git commit -m "#123 feat: Implement HiveMQ MQTT client adapter with auto-reconnect"

# Commit 3: 觸發器檢測邏輯
git add base/src/main/java/io/github/sds100/keymapper/mappings/KeyMapAlgorithm.kt
git add base/src/main/java/io/github/sds100/keymapper/mappings/KeyMapDetectionController.kt
git add base/src/main/java/io/github/sds100/keymapper/system/accessibility/BaseAccessibilityServiceController.kt
git commit -m "#123 feat: Add MQTT message matching logic with 4 match modes"

# Commit 4: UI - Settings 頁面
git add base/src/main/java/io/github/sds100/keymapper/data/Keys.kt
git add base/src/main/java/io/github/sds100/keymapper/settings/MqttSettingsScreen.kt
git add base/src/main/java/io/github/sds100/keymapper/settings/SettingsScreen.kt
git add base/src/main/java/io/github/sds100/keymapper/settings/SettingsViewModel.kt
git add base/src/main/java/io/github/sds100/keymapper/NavDestination.kt
git add base/src/main/java/io/github/sds100/keymapper/BaseMainNavHost.kt
git commit -m "#123 feat: Add MQTT settings screen with broker configuration UI"

# Commit 5: UI - Trigger 設定
git add base/src/main/java/io/github/sds100/keymapper/compose/TriggerSetupBottomSheet.kt
git add base/src/main/java/io/github/sds100/keymapper/mappings/ConfigTriggerDelegate.kt
git add base/src/main/java/io/github/sds100/keymapper/mappings/TriggerSetupState.kt
git add base/src/main/java/io/github/sds100/keymapper/mappings/TriggerSetupShortcut.kt
git add base/src/main/java/io/github/sds100/keymapper/compose/TriggerDiscoverScreen.kt
git commit -m "#123 feat: Add MQTT trigger setup UI with auto-fill broker settings"

# Commit 6: UI - Trigger 顯示
git add base/src/main/java/io/github/sds100/keymapper/compose/TriggerKeyOptionsBottomSheet.kt
git add base/src/main/java/io/github/sds100/keymapper/compose/TriggerKeyListItem.kt
git add base/src/main/java/io/github/sds100/keymapper/mappings/BaseConfigTriggerViewModel.kt
git commit -m "#123 feat: Add MQTT trigger display in trigger list and details"

# Commit 7: 字串資源
git add base/src/main/res/values/strings.xml
git commit -m "#123 chore: Add MQTT-related string resources"

# Commit 8: 文檔
git add MQTT_INTEGRATION.md
git commit -m "#123 docs: Add comprehensive MQTT integration documentation"

# 4. 推送到你的 fork
git push origin feature/mqtt-trigger
```

#### 步驟 3: 驗證 Build Variants

確保所有變體都能編譯：
```bash
# 測試 free flavor
./gradlew :app:assembleFreeDebug

# 測試 pro flavor  
./gradlew :app:assembleProDebug

# 測試 CI build
./gradlew :app:assembleFreeCi
```

## 📝 Discussion 發布步驟

### Step 1: 上傳截圖到 GitHub
1. 在你的 fork 創建一個 Issue（臨時的）
2. 在 Issue 中拖放截圖，GitHub 會生成 URL
3. 複製這些 URL
4. 可以關閉這個臨時 Issue

或者：
1. 使用 GitHub Releases 上傳圖片
2. 或使用 Imgur 等圖床服務

### Step 2: 更新 Discussion 草稿

用實際的截圖 URL 替換 `DISCUSSION_DRAFT.md` 中的佔位符：
```markdown
### MQTT Settings Page
![MQTT Settings](你的實際圖片URL)

### MQTT Trigger Setup
![MQTT Trigger Setup](你的實際圖片URL)

### Trigger in Action
![Trigger List](你的實際圖片URL)
```

### Step 3: 發布到官方倉庫

1. 前往：https://github.com/keymapperorg/KeyMapper/discussions
2. 點擊 "New discussion"
3. 選擇分類：**Ideas** 或 **Q&A**
4. 標題：`💡 Feature Request: MQTT Trigger Support for IoT and Smart Home Integration`
5. 貼上你的文章內容（已修改截圖 URL 的版本）
6. 預覽確認格式正確
7. 點擊 "Start discussion"

### Step 4: 在 Discussion 中補充資訊

發布後立即添加第一則回覆：

```markdown
## Additional Information

### Code Repository
The complete implementation is available in my fork:
https://github.com/andyching168/KeyMapper/tree/develop

### Key Files
- MQTT Client: `base/src/main/java/.../mqtt/MqttClientAdapter.kt`
- Trigger Model: `base/src/main/java/.../trigger/MqttTriggerKey.kt`
- Settings UI: `base/src/main/java/.../settings/MqttSettingsScreen.kt`
- Full Documentation: `MQTT_INTEGRATION.md`

### Demo APK (Optional)
If anyone wants to test, I can provide a signed APK.
Just let me know!

### Technical Details
- Target SDK: 34
- Min SDK: 26
- Added dependency size: ~500KB
- No breaking changes to existing code
```

## 🎯 後續行動

### 如果收到正面回應：
1. 準備提交 PR
2. 根據維護者建議調整
3. 可能需要：
   - 添加單元測試
   - 調整代碼風格
   - 分階段提交

### 如果收到負面回應：
1. 詢問具體原因
2. 看是否可以調整方案
3. 考慮維護獨立 fork

### 如果沒有回應（2週+）：
1. 禮貌地 ping 一下
2. 或直接提交 PR 看看
3. 或開始維護獨立版本

## 📧 私訊模板（可選）

如果想直接聯繫維護者，可以用這個模板：

```
Hi [Maintainer Name],

I hope this message finds you well. I'm a long-time user of KeyMapper 
and I've implemented a complete MQTT trigger feature that I'd love to 
contribute back to the project.

I've posted a detailed proposal in Discussions:
[DISCUSSION URL]

The implementation is complete, tested, and documented. I understand 
this is a significant addition, so I wanted to reach out directly to 
see if this aligns with KeyMapper's direction.

Would you be interested in reviewing this feature? I'm happy to:
- Provide a demo APK for testing
- Answer any questions about the implementation
- Adjust the approach based on your preferences

Thank you for maintaining such an amazing project!

Best regards,
[Your Name]
```

## ⏰ 時間規劃

- **Day 1**: 準備截圖 + 提交代碼
- **Day 2**: 發布 Discussion
- **Week 1-2**: 等待回應，回答問題
- **Week 3+**: 根據反饋決定下一步

---

**記住**: 保持專業、禮貌、耐心。開源維護者都是志願者，他們可能很忙。
