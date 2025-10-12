package io.github.sds100.keymapper.base.mqtt

import kotlinx.serialization.Serializable

@Serializable
data class MqttMessageEvent(
    val topic: String,
    val message: String,
)
