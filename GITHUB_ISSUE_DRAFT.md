# GitHub Issue 草稿

在你的 fork 或官方倉庫創建 Issue，用於追蹤此功能開發。

---

## Title
MQTT Trigger Support for IoT and Smart Home Integration

## Body

### Feature Request

Add **MQTT trigger support** to KeyMapper, enabling users to trigger key maps through MQTT messages from IoT devices and smart home systems.

### Use Cases

1. **Smart Home Automation**
   - Trigger phone actions when smart home events occur (door opens, motion detected)
   - Integration with Home Assistant, OpenHAB, Node-RED

2. **IoT Sensor Integration**
   - React to sensor data (temperature, humidity, proximity)
   - Remote control via ESP32/Arduino/Raspberry Pi devices

3. **Automation Platforms**
   - IFTTT, Zapier integration via MQTT bridges
   - Custom automation workflows

### Proposed Solution

**Complete implementation ready** with:
- ✅ HiveMQ MQTT Client (Apache 2.0 licensed - FOSS compatible)
- ✅ 4 message matching modes (Exact, Contains, Regex, Any)
- ✅ Full Jetpack Compose UI
- ✅ Dynamic subscription management
- ✅ Auto-reconnection logic
- ✅ Comprehensive documentation

### Implementation Details

For detailed technical proposal, see Discussion: [LINK_TO_DISCUSSION]

### Additional Context

- Works with both `free` and `pro` build flavors
- Compatible with all build types
- No breaking changes to existing code
- ~500KB additional dependency size

---

**Labels**: `enhancement`, `feature request`, `IoT`

