package io.github.sds100.keymapper.base.mqtt

import android.content.Context
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.sds100.keymapper.data.Keys
import io.github.sds100.keymapper.data.repositories.PreferenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MqttClientAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceRepository: PreferenceRepository,
) {
    private var mqttClient: Mqtt3AsyncClient? = null
    private val adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _messageEvents = MutableSharedFlow<MqttMessageEvent>(replay = 0)
    val messageEvents: SharedFlow<MqttMessageEvent> = _messageEvents.asSharedFlow()

    private val subscribedTopics = mutableSetOf<String>()

    suspend fun start() {
        if (mqttClient?.state?.isConnected == true) {
            Timber.d("MQTT client already connected")
            return
        }

        connectToMqttBroker()
    }

    fun stop() {
        disconnectFromMqttBroker()
    }

    private suspend fun connectToMqttBroker() {
        try {
            val brokerUrl = preferenceRepository.get(Keys.mqttBrokerUrl).firstOrNull() ?: "broker.hivemq.com"
            val brokerPort = preferenceRepository.get(Keys.mqttBrokerPort).firstOrNull()?.toIntOrNull() ?: 1883
            val clientId = "KeyMapper_${UUID.randomUUID()}"
            val username = preferenceRepository.get(Keys.mqttUsername).firstOrNull()
            val password = preferenceRepository.get(Keys.mqttPassword).firstOrNull()

            Timber.i("Connecting to MQTT broker: $brokerUrl:$brokerPort with client ID: $clientId")

            // Build MQTT client
            val clientBuilder = MqttClient.builder()
                .useMqttVersion3()
                .identifier(clientId)
                .serverHost(brokerUrl)
                .serverPort(brokerPort)
                .automaticReconnect()
                    .initialDelay(1, java.util.concurrent.TimeUnit.SECONDS)
                    .maxDelay(30, java.util.concurrent.TimeUnit.SECONDS)
                    .applyAutomaticReconnect()

            mqttClient = clientBuilder.buildAsync()

            // Build connect options
            val connectBuilder = mqttClient!!.connectWith()
                .keepAlive(60)
                .cleanSession(true)

            // Add authentication if provided
            if (!username.isNullOrEmpty()) {
                val authBuilder = connectBuilder.simpleAuth()
                    .username(username)
                
                // Only add password if it's not null or empty
                if (!password.isNullOrEmpty()) {
                    authBuilder.password(password.toByteArray(StandardCharsets.UTF_8))
                }
                
                authBuilder.applySimpleAuth()
            }

            // Connect to broker
            connectBuilder.send()
                .whenComplete { _, throwable ->
                    if (throwable != null) {
                        Timber.e(throwable, "Failed to connect to MQTT broker")
                    } else {
                        Timber.i("Connected to MQTT broker successfully")
                        adapterScope.launch {
                            // Re-subscribe to previously subscribed topics
                            resubscribeToTopics()
                        }
                    }
                }

            // Set up message callback
            mqttClient?.publishes(com.hivemq.client.mqtt.MqttGlobalPublishFilter.ALL) { publish ->
                handleMessage(publish)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to MQTT broker")
        }
    }

    private fun handleMessage(publish: Mqtt3Publish) {
        val topic = publish.topic.toString()
        val payload = publish.payloadAsBytes
        val message = String(payload, StandardCharsets.UTF_8)

        Timber.d("MQTT message arrived: topic=$topic, message=$message")

        adapterScope.launch {
            _messageEvents.emit(MqttMessageEvent(topic, message))
        }
    }

    private fun disconnectFromMqttBroker() {
        try {
            mqttClient?.disconnect()?.whenComplete { _, _ ->
                Timber.i("Disconnected from MQTT broker")
            }
            mqttClient = null
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting from MQTT broker")
        }
    }

    fun subscribeToTopic(topic: String) {
        adapterScope.launch {
            try {
                val client = mqttClient
                if (client == null) {
                    Timber.w("Cannot subscribe to topic $topic: MQTT client is null")
                    return@launch
                }
                
                if (!client.state.isConnected) {
                    Timber.w("Cannot subscribe to topic $topic: MQTT client not connected, state=${client.state}")
                    // Add to subscribed topics anyway so it will be re-subscribed when connected
                    subscribedTopics.add(topic)
                    return@launch
                }
                
                Timber.d("Attempting to subscribe to MQTT topic: $topic")
                client.subscribeWith()
                    .topicFilter(topic)
                    .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                    .send()
                    .whenComplete { _, throwable ->
                        if (throwable != null) {
                            Timber.e(throwable, "Failed to subscribe to MQTT topic: $topic")
                        } else {
                            subscribedTopics.add(topic)
                            Timber.i("Subscribed to MQTT topic: $topic")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to subscribe to MQTT topic: $topic")
            }
        }
    }

    fun unsubscribeFromTopic(topic: String) {
        adapterScope.launch {
            try {
                mqttClient?.unsubscribeWith()
                    ?.topicFilter(topic)
                    ?.send()
                    ?.whenComplete { _, throwable ->
                        if (throwable != null) {
                            Timber.e(throwable, "Failed to unsubscribe from MQTT topic: $topic")
                        } else {
                            subscribedTopics.remove(topic)
                            Timber.i("Unsubscribed from MQTT topic: $topic")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to unsubscribe from MQTT topic: $topic")
            }
        }
    }

    private fun resubscribeToTopics() {
        subscribedTopics.forEach { topic ->
            try {
                mqttClient?.subscribeWith()
                    ?.topicFilter(topic)
                    ?.qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                    ?.send()
                    ?.whenComplete { _, throwable ->
                        if (throwable != null) {
                            Timber.e(throwable, "Failed to re-subscribe to MQTT topic: $topic")
                        } else {
                            Timber.d("Re-subscribed to MQTT topic: $topic")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to re-subscribe to MQTT topic: $topic")
            }
        }
    }

    fun updateSubscriptions(topics: Set<String>) {
        val topicsToAdd = topics - subscribedTopics
        val topicsToRemove = subscribedTopics - topics

        Timber.d("MQTT: Update subscriptions - current: $subscribedTopics, new: $topics")
        Timber.d("MQTT: Topics to add: $topicsToAdd, topics to remove: $topicsToRemove")

        topicsToAdd.forEach { subscribeToTopic(it) }
        topicsToRemove.forEach { unsubscribeFromTopic(it) }
    }

    fun teardown() {
        disconnectFromMqttBroker()
        adapterScope.cancel()
    }
}
