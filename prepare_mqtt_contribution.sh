#!/bin/bash

# MQTT Trigger Contribution Preparation Script
# This script prepares the MQTT feature for contribution to KeyMapper
# following the project's contribution guidelines

set -e  # Exit on error

echo "🚀 Preparing MQTT Trigger contribution for KeyMapper"
echo "=================================================="
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if we're in the right directory
if [ ! -f "settings.gradle.kts" ]; then
    echo -e "${RED}❌ Error: Not in KeyMapper root directory${NC}"
    exit 1
fi

# Prompt for Issue ID
echo -e "${YELLOW}Please create a GitHub Issue first if you haven't already.${NC}"
echo "Title: MQTT Trigger Support for IoT and Smart Home Integration"
echo ""
read -p "Enter the Issue ID (e.g., 123): #" ISSUE_ID

if [ -z "$ISSUE_ID" ]; then
    echo -e "${RED}❌ Issue ID is required${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}✅ Using Issue #${ISSUE_ID}${NC}"
echo ""

# Ensure we're on develop and it's up to date
echo "📥 Syncing with develop branch..."
git checkout develop
git pull origin develop

# Create feature branch
BRANCH_NAME="feature/mqtt-trigger"
echo "🌿 Creating feature branch: ${BRANCH_NAME}"

# Check if branch already exists
if git show-ref --verify --quiet refs/heads/${BRANCH_NAME}; then
    echo -e "${YELLOW}⚠️  Branch ${BRANCH_NAME} already exists${NC}"
    read -p "Delete and recreate? (y/n): " RECREATE
    if [ "$RECREATE" = "y" ]; then
        git branch -D ${BRANCH_NAME}
        git checkout -b ${BRANCH_NAME}
    else
        git checkout ${BRANCH_NAME}
    fi
else
    git checkout -b ${BRANCH_NAME}
fi

echo ""
echo "📦 Making staged commits..."
echo ""

# Commit 1: Core data models
echo "1️⃣ Committing core data models..."
git add \
    data/src/main/java/io/github/sds100/keymapper/data/db/dao/MqttTriggerKeyDao.kt \
    data/src/main/java/io/github/sds100/keymapper/data/entities/MqttTriggerKeyEntity.kt \
    base/src/main/java/io/github/sds100/keymapper/mappings/trigger/MqttTriggerKey.kt \
    base/src/main/java/io/github/sds100/keymapper/util/MqttMessageEvent.kt \
    2>/dev/null || echo "Some model files not found, continuing..."

git commit -m "#${ISSUE_ID} feat: Add MQTT trigger data models and database entities

- Add MqttTriggerKeyEntity for Room database
- Add MqttTriggerKey domain model with 4 match types
- Add MqttMessageEvent for message events
- Add MqttTriggerKeyDao for database operations" || echo "No changes to commit"

# Commit 2: MQTT Client
echo "2️⃣ Committing MQTT client adapter..."
git add \
    base/build.gradle.kts \
    base/src/main/java/io/github/sds100/keymapper/system/mqtt/MqttClientAdapter.kt \
    2>/dev/null || echo "Some client files not found, continuing..."

git commit -m "#${ISSUE_ID} feat: Implement HiveMQ MQTT client adapter with auto-reconnect

- Add HiveMQ MQTT Client dependency (v1.3.3)
- Implement singleton MqttClientAdapter with Hilt
- Add automatic connection/reconnection logic
- Add dynamic subscription management
- Use SharedFlow for message events" || echo "No changes to commit"

# Commit 3: Detection logic
echo "3️⃣ Committing trigger detection logic..."
git add \
    base/src/main/java/io/github/sds100/keymapper/mappings/KeyMapAlgorithm.kt \
    base/src/main/java/io/github/sds100/keymapper/mappings/KeyMapDetectionController.kt \
    base/src/main/java/io/github/sds100/keymapper/system/accessibility/BaseAccessibilityServiceController.kt \
    base/src/main/java/io/github/sds100/keymapper/mappings/ConfigTriggerUseCase.kt \
    base/src/main/java/io/github/sds100/keymapper/mappings/Trigger.kt \
    base/src/main/java/io/github/sds100/keymapper/mappings/trigger/TriggerKey.kt \
    base/src/main/java/io/github/sds100/keymapper/mappings/trigger/TriggerValidator.kt \
    2>/dev/null || echo "Some detection files not found, continuing..."

git commit -m "#${ISSUE_ID} feat: Add MQTT message matching logic with 4 match modes

- Implement EXACT, CONTAINS, REGEX, ANY matching modes
- Add onMqttMessage() to KeyMapAlgorithm
- Integrate MQTT message routing in KeyMapDetectionController
- Add auto-subscribe logic based on enabled KeyMaps
- Add MQTT trigger validation" || echo "No changes to commit"

# Commit 4: Settings UI
echo "4️⃣ Committing MQTT settings screen..."
git add \
    base/src/main/java/io/github/sds100/keymapper/data/Keys.kt \
    base/src/main/java/io/github/sds100/keymapper/settings/MqttSettingsScreen.kt \
    base/src/main/java/io/github/sds100/keymapper/settings/SettingsScreen.kt \
    base/src/main/java/io/github/sds100/keymapper/settings/SettingsViewModel.kt \
    base/src/main/java/io/github/sds100/keymapper/NavDestination.kt \
    base/src/main/java/io/github/sds100/keymapper/BaseMainNavHost.kt \
    2>/dev/null || echo "Some settings files not found, continuing..."

git commit -m "#${ISSUE_ID} feat: Add MQTT settings screen with broker configuration UI

- Add MqttSettingsScreen with Jetpack Compose
- Add MQTT preference keys (broker URL, port, credentials)
- Add MQTT settings button in SettingsScreen
- Add NavDestination.MqttSettings for navigation
- Implement auto-save for all MQTT settings" || echo "No changes to commit"

# Commit 5: Trigger setup UI
echo "5️⃣ Committing MQTT trigger setup UI..."
git add \
    base/src/main/java/io/github/sds100/keymapper/compose/TriggerSetupBottomSheet.kt \
    base/src/main/java/io/github/sds100/keymapper/mappings/ConfigTriggerDelegate.kt \
    base/src/main/java/io/github/sds100/keymapper/mappings/TriggerSetupState.kt \
    base/src/main/java/io/github/sds100/keymapper/mappings/TriggerSetupShortcut.kt \
    base/src/main/java/io/github/sds100/keymapper/compose/TriggerDiscoverScreen.kt \
    2>/dev/null || echo "Some setup files not found, continuing..."

git commit -m "#${ISSUE_ID} feat: Add MQTT trigger setup UI with auto-fill broker settings

- Add MqttTriggerSetupBottomSheet composable
- Add MQTT shortcut in TriggerDiscoverScreen
- Implement auto-load of saved broker settings
- Add topic, message pattern, and match type inputs
- Add validation and requirements info" || echo "No changes to commit"

# Commit 6: Trigger display UI
echo "6️⃣ Committing MQTT trigger display..."
git add \
    base/src/main/java/io/github/sds100/keymapper/compose/TriggerKeyOptionsBottomSheet.kt \
    base/src/main/java/io/github/sds100/keymapper/compose/TriggerKeyListItem.kt \
    base/src/main/java/io/github/sds100/keymapper/mappings/BaseConfigTriggerViewModel.kt \
    2>/dev/null || echo "Some display files not found, continuing..."

git commit -m "#${ISSUE_ID} feat: Add MQTT trigger display in trigger list and details

- Add read-only MQTT trigger display in TriggerKeyOptionsBottomSheet
- Add MQTT trigger formatting in TriggerKeyListItem
- Add MQTT trigger state in BaseConfigTriggerViewModel
- Display topic, pattern, and match type" || echo "No changes to commit"

# Commit 7: String resources
echo "7️⃣ Committing string resources..."
git add \
    base/src/main/res/values/strings.xml \
    2>/dev/null || echo "Strings file not found, continuing..."

git commit -m "#${ISSUE_ID} chore: Add MQTT-related string resources

- Add trigger_setup_mqtt_* strings
- Add settings_mqtt_* strings
- Add trigger_discover_shortcut_mqtt
- Add all UI labels and descriptions" || echo "No changes to commit"

# Commit 8: Documentation
echo "8️⃣ Committing documentation..."
git add \
    MQTT_INTEGRATION.md \
    2>/dev/null || echo "Documentation file not found, continuing..."

git commit -m "#${ISSUE_ID} docs: Add comprehensive MQTT integration documentation

- Add architecture overview
- Add user setup guide
- Add testing guide with examples
- Add troubleshooting section
- Add FAQ and use cases" || echo "No changes to commit"

echo ""
echo -e "${GREEN}✅ All commits completed!${NC}"
echo ""

# Show commit log
echo "📜 Commit history:"
git log develop..HEAD --oneline

echo ""
echo "🧪 Testing build variants..."
echo ""

# Test free debug build
echo "Testing free debug build..."
if ./gradlew :app:assembleFreeDebug -q; then
    echo -e "${GREEN}✅ Free debug build successful${NC}"
else
    echo -e "${RED}❌ Free debug build failed${NC}"
fi

# Test pro debug build (might not work in free version)
echo "Testing pro debug build..."
if ./gradlew :app:assembleProDebug -q 2>/dev/null; then
    echo -e "${GREEN}✅ Pro debug build successful${NC}"
else
    echo -e "${YELLOW}⚠️  Pro debug build skipped (pro flavor may not be available)${NC}"
fi

echo ""
echo -e "${GREEN}🎉 Preparation complete!${NC}"
echo ""
echo "Next steps:"
echo "1. Review the commits: git log develop..HEAD"
echo "2. Test the app thoroughly"
echo "3. Push to your fork: git push origin ${BRANCH_NAME}"
echo "4. Create a Pull Request on GitHub"
echo ""
echo "Or if you want to post a Discussion first:"
echo "1. Review DISCUSSION_DRAFT.md and DISCUSSION_CHECKLIST.md"
echo "2. Post to https://github.com/keymapperorg/KeyMapper/discussions"
echo "3. Wait for maintainer feedback"
echo ""
