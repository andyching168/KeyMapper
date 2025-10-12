package io.github.sds100.keymapper.base.trigger

import io.github.sds100.keymapper.base.keymaps.ClickType
import io.github.sds100.keymapper.data.entities.MqttTriggerKeyEntity
import io.github.sds100.keymapper.data.entities.TriggerKeyEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class MqttTriggerKey(
    override val uid: String = UUID.randomUUID().toString(),

    @SerialName("mqttTopic")
    val topic: String,

    @SerialName("mqttMessagePattern")
    val messagePattern: String = "",

    @SerialName("mqttMatchType")
    val matchType: MqttMatchType = MqttMatchType.EXACT,

    override val clickType: ClickType,
) : TriggerKey() {
    override val allowedLongPress: Boolean = false
    override val allowedDoublePress: Boolean = false

    override fun compareTo(other: TriggerKey) = when (other) {
        is MqttTriggerKey -> compareValuesBy(
            this,
            other,
            { it.topic },
            { it.messagePattern },
            { it.matchType },
            { it.clickType },
        )

        else -> super.compareTo(other)
    }

    companion object {
        fun fromEntity(entity: MqttTriggerKeyEntity): TriggerKey {
            val matchType: MqttMatchType = when (entity.matchType) {
                MqttTriggerKeyEntity.MATCH_EXACT -> MqttMatchType.EXACT
                MqttTriggerKeyEntity.MATCH_CONTAINS -> MqttMatchType.CONTAINS
                MqttTriggerKeyEntity.MATCH_REGEX -> MqttMatchType.REGEX
                MqttTriggerKeyEntity.MATCH_ANY -> MqttMatchType.ANY
                else -> MqttMatchType.EXACT
            }

            val clickType: ClickType = when (entity.clickType) {
                TriggerKeyEntity.SHORT_PRESS -> ClickType.SHORT_PRESS
                TriggerKeyEntity.LONG_PRESS -> ClickType.LONG_PRESS
                TriggerKeyEntity.DOUBLE_PRESS -> ClickType.DOUBLE_PRESS
                else -> ClickType.SHORT_PRESS
            }

            return MqttTriggerKey(
                uid = entity.uid,
                topic = entity.topic,
                messagePattern = entity.messagePattern,
                matchType = matchType,
                clickType = clickType,
            )
        }

        fun toEntity(key: MqttTriggerKey): MqttTriggerKeyEntity {
            val matchType: Int = when (key.matchType) {
                MqttMatchType.EXACT -> MqttTriggerKeyEntity.MATCH_EXACT
                MqttMatchType.CONTAINS -> MqttTriggerKeyEntity.MATCH_CONTAINS
                MqttMatchType.REGEX -> MqttTriggerKeyEntity.MATCH_REGEX
                MqttMatchType.ANY -> MqttTriggerKeyEntity.MATCH_ANY
            }

            val clickType: Int = when (key.clickType) {
                ClickType.SHORT_PRESS -> TriggerKeyEntity.SHORT_PRESS
                ClickType.LONG_PRESS -> TriggerKeyEntity.LONG_PRESS
                ClickType.DOUBLE_PRESS -> TriggerKeyEntity.DOUBLE_PRESS
            }

            return MqttTriggerKeyEntity(
                topic = key.topic,
                messagePattern = key.messagePattern,
                matchType = matchType,
                clickType = clickType,
                uid = key.uid,
            )
        }
    }
}

@Serializable
enum class MqttMatchType {
    EXACT,
    CONTAINS,
    REGEX,
    ANY,
}
