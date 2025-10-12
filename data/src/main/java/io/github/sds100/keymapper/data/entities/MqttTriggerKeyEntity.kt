package io.github.sds100.keymapper.data.entities

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class MqttTriggerKeyEntity(
    @SerializedName(NAME_TOPIC)
    val topic: String,

    @SerializedName(NAME_MESSAGE_PATTERN)
    val messagePattern: String = "",

    @SerializedName(NAME_MATCH_TYPE)
    val matchType: Int = MATCH_EXACT,

    @SerializedName(NAME_CLICK_TYPE)
    override val clickType: Int = SHORT_PRESS,

    @SerializedName(NAME_UID)
    override val uid: String = UUID.randomUUID().toString(),
) : TriggerKeyEntity(),
    Parcelable {

    companion object {
        // DON'T CHANGE THESE. Used for JSON serialization and parsing.
        const val NAME_TOPIC = "mqttTopic"
        const val NAME_MESSAGE_PATTERN = "mqttMessagePattern"
        const val NAME_MATCH_TYPE = "mqttMatchType"

        // Match types
        const val MATCH_EXACT = 0
        const val MATCH_CONTAINS = 1
        const val MATCH_REGEX = 2
        const val MATCH_ANY = 3
    }
}
