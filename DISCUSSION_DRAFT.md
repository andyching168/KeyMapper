# 💡 Feature Request: MQTT Trigger Support for IoT and Smart Home Integration

## Summary

I propose adding **MQTT trigger support** to KeyMapper, enabling users to trigger key maps through MQTT messages. This would unlock powerful IoT and smart home automation capabilities while maintaining KeyMapper's existing functionality.

## Motivation

### Why MQTT?

MQTT is the de facto standard protocol for IoT communication. Adding MQTT support would allow KeyMapper to integrate with:

- **Smart Home Systems**: Home Assistant, OpenHAB, Node-RED
- **IoT Devices**: Sensors, buttons, switches
- **Automation Platforms**: IFTTT, Zapier (via MQTT bridges)
- **Custom Hardware**: ESP32, Arduino, Raspberry Pi projects

### Real-World Use Cases

1. **Smart Home Automation**
   ```
   When front door opens (MQTT message)
   → Trigger: Unmute phone + Turn on lights (KeyMapper actions)
   ```

2. **IoT Sensor Integration**
   ```
   When motion detected (MQTT sensor)
   → Trigger: Open camera app + Start recording
   ```

3. **Remote Control**
   ```
   When button pressed on ESP32 device (MQTT)
   → Trigger: Play/pause media on phone
   ```

4. **Conditional Automation**
   ```
   When temperature > 80°C (MQTT message)
   → Trigger: Show warning notification
   ```

## Proposed Implementation

I have already implemented a **complete, working solution** with the following features:

### ✅ Core Features

- **HiveMQ MQTT Client Integration** (v1.3.3)
  - Automatic connection and reconnection
  - Clean, modern async API
  - Production-ready and well-maintained

- **4 Message Matching Modes**
  - **Exact Match**: Message must exactly match pattern
  - **Contains**: Message contains the pattern
  - **Regex**: Pattern matching with regular expressions
  - **Any**: Trigger on any message from topic

- **Dynamic Subscription Management**
  - Automatically subscribes to topics from enabled KeyMaps
  - Unsubscribes when KeyMaps are disabled
  - Stops MQTT client when no MQTT triggers exist

- **Full UI Integration**
  - Global MQTT broker settings page
  - MQTT trigger setup interface
  - Trigger details display
  - Auto-fill of saved broker settings

### 🏗️ Architecture

The implementation follows KeyMapper's existing patterns:

```
Data Layer
├── MqttTriggerKeyEntity (Room database)
├── MqttTriggerKey (Domain model)
└── Keys (DataStore preferences)

Business Layer
├── MqttClientAdapter (Singleton, Hilt-injected)
├── KeyMapAlgorithm (MQTT message matching)
└── KeyMapDetectionController (Message routing)

UI Layer
├── MqttSettingsScreen (Jetpack Compose)
├── TriggerSetupBottomSheet (MQTT setup)
└── TriggerKeyOptionsBottomSheet (Display)
```

### 📦 Dependencies

Only **one** new dependency:
```kotlin
implementation("com.hivemq:hivemq-mqtt-client:1.3.3")
```

- Well-maintained (active development)
- Small size (~500KB)
- Apache 2.0 license
- No conflicts with existing dependencies

### 🎯 Design Principles

1. **Non-intrusive**: Existing functionality remains unchanged
2. **Optional**: Users can ignore MQTT if they don't need it
3. **Consistent**: Uses same patterns as FingerprintTriggerKey, EvdevTriggerKey
4. **Complete**: Full feature parity with other trigger types
5. **Documented**: Comprehensive technical documentation included

## Code Quality

- ✅ Follows KeyMapper's Kotlin style guide
- ✅ Uses existing architecture (MVVM, Hilt, Flow, Coroutines)
- ✅ Jetpack Compose UI (consistent with project direction)
- ✅ Proper error handling and logging
- ✅ Clean separation of concerns

## Screenshots

### MQTT Settings Page
![MQTT Settings](https://via.placeholder.com/800x1600/1E88E5/FFFFFF?text=MQTT+Settings+Screen)
*Global broker configuration with URL, port, credentials*

### MQTT Trigger Setup
![MQTT Trigger Setup](https://via.placeholder.com/800x1600/43A047/FFFFFF?text=MQTT+Trigger+Setup)
*Topic, message pattern, and match type selection*

### Trigger in Action
![Trigger List](https://via.placeholder.com/800x1600/FB8C00/FFFFFF?text=Trigger+List+with+MQTT)
*MQTT triggers displayed alongside other trigger types*

## Testing

Tested with:
- **Public brokers**: broker.hivemq.com, mqtt.eclipseprojects.io
- **Local brokers**: Mosquitto, EMQX
- **Smart home**: Home Assistant MQTT integration
- **IoT devices**: ESP32 with MQTT publish

All test scenarios working correctly:
- ✅ Connection/reconnection
- ✅ Message matching (all 4 modes)
- ✅ Dynamic subscriptions
- ✅ Trigger execution
- ✅ Settings persistence

## Documentation

Complete technical documentation available:
- Architecture overview
- User guide (setup, configuration, usage)
- Test guide (with examples)
- Troubleshooting tips
- FAQ section
- Real-world use cases

## Impact Assessment

### Pros
- ✅ Opens entirely new use cases
- ✅ Attracts IoT/smart home users
- ✅ Minimal code changes to existing functionality
- ✅ Clean, maintainable implementation
- ✅ Well-documented

### Cons
- ⚠️ Adds ~20 new files
- ⚠️ One new external dependency
- ⚠️ Requires ongoing MQTT feature maintenance
- ⚠️ Slightly increased APK size (~500KB)

## Implementation Status

🎉 **100% Complete and Working**

All code is ready, tested, and documented. The implementation:
- ✅ Works with both `free` and `pro` build flavors (uses only FOSS libraries)
- ✅ Compatible with all build types (`debug`, `release`, `ci`)
- ✅ Follows KeyMapper's existing architectural patterns
- ✅ No breaking changes to existing code

I'm ready to contribute this following your development workflow:

1. **Create GitHub Issue**: Document the feature request with use cases
2. **Feature Branch**: Create `feature/mqtt-trigger` off `develop`
3. **Structured Commits**: Break down into logical commits following your commit convention
4. **Pull Request**: Submit PR with proper documentation

Or alternatively:
- **Staged PRs**: Multiple smaller PRs if preferred (models → client → UI)
- **Independent Fork**: Maintain as "KeyMapper MQTT Edition" if this doesn't align with roadmap

## Questions for Maintainers

1. **Interest**: Is MQTT trigger support aligned with KeyMapper's roadmap?
2. **Approach**: Would you prefer:
   - Single comprehensive PR?
   - Multiple staged PRs (easier review)?
   - Feature flag for beta testing first?
3. **Build Variants**: Any concerns about the `free` vs `pro` flavor compatibility?
   - *Note: MQTT uses HiveMQ client which is Apache 2.0 licensed (FOSS)*
4. **Testing Requirements**: Should I:
   - Add unit tests for MQTT matching logic?
   - Add integration tests?
   - Provide signed test APK?
5. **Documentation**: Is the current MQTT_INTEGRATION.md sufficient, or should I add to the official docs website?

## Alternative Approaches Considered

### Why not use Android's built-in MQTT support?
Android doesn't have built-in MQTT support. We need a client library.

### Why HiveMQ instead of Eclipse Paho?
- Eclipse Paho Android Service is **deprecated and unmaintained**
- HiveMQ has modern async API with coroutines support
- Better documentation and active community
- Smaller and more efficient

### Why not make this a plugin/extension?
KeyMapper's current architecture doesn't support plugins. This requires core integration to access KeyMap trigger system.

## Related Issues/Discussions

I couldn't find any existing issues about MQTT support. This appears to be a new feature request.

## Community Feedback Welcome

I'd love to hear thoughts from the community:
- Would you use MQTT triggers?
- What use cases do you have in mind?
- Any concerns or suggestions?

---

**Note**: I have the complete implementation ready and can provide:
- Source code
- Demo APK for testing
- Additional screenshots/videos
- Any other information needed

Looking forward to your feedback! 🙏
