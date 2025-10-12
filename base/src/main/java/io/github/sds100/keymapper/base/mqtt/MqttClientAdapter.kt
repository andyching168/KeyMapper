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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Track previous connection state to detect changes
    private var wasConnected = false

    // Topics that we want to subscribe to (desired state)
    private val desiredTopics = mutableSetOf<String>()
    
    // Topics that are actually subscribed (current state)
    private val subscribedTopics = mutableSetOf<String>()

    suspend fun start() {
        if (mqttClient?.state?.isConnected == true) {
            Timber.d("MQTT client already connected")
            return
        }

        connectToMqttBroker()
        
        // Start monitoring connection state
        startConnectionMonitor()
    }

    fun stop() {
        disconnectFromMqttBroker()
    }
    
    private fun startConnectionMonitor() {
        adapterScope.launch {
            var disconnectTime = 0L
            var connectingStartTime = 0L
            var isResetting = false // Flag to prevent duplicate reset triggers
            var lastLoggedState = "" // Track last logged state to avoid spam
            
            while (true) {
                delay(2000) // Check every 2 seconds
                
                val client = mqttClient
                val currentlyConnected = client?.state?.isConnected == true
                val isConnectingOrReconnecting = client?.state?.isConnectedOrReconnect == true && !currentlyConnected
                
                // Only log when state changes
                val currentState = "connected=$currentlyConnected,connecting=$isConnectingOrReconnecting,_isConnected=${_isConnected.value}"
                if (currentState != lastLoggedState) {
                    Timber.d("MQTT Monitor: wasConnected=$wasConnected, $currentState, resetting=$isResetting")
                    lastLoggedState = currentState
                }
                
                // Detect disconnection (whether reconnecting or not)
                if (wasConnected && !currentlyConnected) {
                    if (_isConnected.value) {
                        Timber.w("MQTT broker disconnected, reconnection in progress")
                        _isConnected.value = false
                        subscribedTopics.clear()
                    }
                    wasConnected = false
                    if (disconnectTime == 0L) {
                        disconnectTime = System.currentTimeMillis()
                    }
                }
                
                // Track how long we've been in connecting state
                if (isConnectingOrReconnecting && !isResetting) {
                    if (connectingStartTime == 0L) {
                        connectingStartTime = System.currentTimeMillis()
                    }
                    val connectingDuration = System.currentTimeMillis() - connectingStartTime
                    
                    // If stuck connecting for more than 15 seconds, force complete reset
                    if (connectingDuration > 15000) {
                        Timber.w("Stuck in connecting state for 15s, forcing complete client reset...")
                        isResetting = true
                        connectingStartTime = 0L
                        disconnectTime = 0L
                        
                        // Force complete reset and reconnect
                        adapterScope.launch {
                            try {
                                // Force disconnect and destroy client
                                try {
                                    mqttClient?.disconnect()
                                } catch (e: Exception) {
                                    Timber.w(e, "Error disconnecting stuck client, will recreate anyway")
                                }
                                
                                // Clear client reference
                                mqttClient = null
                                wasConnected = false
                                
                                Timber.i("Recreating MQTT client and reconnecting...")
                                delay(2000)
                                
                                // Recreate client and reconnect
                                connectToMqttBroker()
                                
                                // Allow future resets after 10 seconds
                                delay(10000)
                                isResetting = false
                            } catch (e: Exception) {
                                Timber.e(e, "Error during forced client reset")
                                isResetting = false
                            }
                        }
                    }
                } else if (!isConnectingOrReconnecting) {
                    connectingStartTime = 0L
                }
                
                // If disconnected and not auto-reconnecting, try manual reconnect every 5 seconds
                if (!wasConnected && !currentlyConnected && !isConnectingOrReconnecting && disconnectTime > 0) {
                    val timeSinceDisconnect = System.currentTimeMillis() - disconnectTime
                    if (timeSinceDisconnect >= 5000) {
                        Timber.i("Attempting manual reconnection to MQTT broker...")
                        attemptReconnect()
                        disconnectTime = System.currentTimeMillis() // Reset timer for next attempt
                    }
                }
                
                // Detect successful reconnection
                if (!wasConnected && currentlyConnected) {
                    Timber.i("MQTT broker (re)connected successfully")
                    _isConnected.value = true
                    subscribedTopics.clear()
                    
                    // Resubscribe to topics (message callback already set up on initial connection)
                    resubscribeToTopics()
                    
                    wasConnected = true
                    disconnectTime = 0L
                    connectingStartTime = 0L
                }
                
                // Update tracking state for connected clients
                if (currentlyConnected) {
                    wasConnected = true
                    disconnectTime = 0L
                    connectingStartTime = 0L
                }
            }
        }
    }
    
    private fun attemptReconnect() {
        try {
            val client = mqttClient
            if (client == null) {
                Timber.e("Cannot reconnect: MQTT client is null")
                return
            }
            
            val clientState = client.state
            
            // Check if already connected or connecting
            if (clientState.isConnected) {
                Timber.d("Already connected, updating state")
                wasConnected = true
                _isConnected.value = true
                subscribedTopics.clear()
                resubscribeToTopics()
                return
            }
            
            if (clientState.isConnectedOrReconnect) {
                Timber.d("Client is currently connecting/reconnecting, waiting...")
                return
            }
            
            Timber.d("Reconnecting to MQTT broker...")
            adapterScope.launch {
                val username = preferenceRepository.get(Keys.mqttUsername).firstOrNull()
                val password = preferenceRepository.get(Keys.mqttPassword).firstOrNull()
                
                val connectBuilder = client.connectWith()
                    .keepAlive(60)
                    .cleanSession(true)
                
                // Add authentication if provided
                if (!username.isNullOrEmpty()) {
                    val authBuilder = connectBuilder.simpleAuth()
                        .username(username)
                    
                    if (!password.isNullOrEmpty()) {
                        authBuilder.password(password.toByteArray(StandardCharsets.UTF_8))
                    }
                    
                    authBuilder.applySimpleAuth()
                }
                
                connectBuilder.send()
                    .whenComplete { _, throwable ->
                        if (throwable != null) {
                            Timber.e(throwable, "Failed to reconnect to MQTT broker")
                        } else {
                            Timber.i("Reconnected to MQTT broker successfully")
                            _isConnected.value = true
                            wasConnected = true
                            subscribedTopics.clear()
                            
                            // Resubscribe to topics (message callback already set up on initial connection)
                            resubscribeToTopics()
                        }
                    }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during manual reconnection")
        }
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
                    .initialDelay(5, java.util.concurrent.TimeUnit.SECONDS)
                    .maxDelay(5, java.util.concurrent.TimeUnit.SECONDS)
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
                        _isConnected.value = false
                    } else {
                        Timber.i("Connected to MQTT broker successfully")
                        _isConnected.value = true
                        
                        // Clear subscribed topics since we just connected
                        subscribedTopics.clear()
                        
                        // Subscribe to all desired topics
                        adapterScope.launch {
                            resubscribeToTopics()
                        }
                    }
                }

            // Set up message callback
            setupMessageCallback()
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to MQTT broker")
            _isConnected.value = false
        }
    }
    
    private fun setupMessageCallback() {
        try {
            mqttClient?.publishes(com.hivemq.client.mqtt.MqttGlobalPublishFilter.ALL) { publish ->
                handleMessage(publish)
            }
            Timber.d("MQTT message callback setup complete")
        } catch (e: Exception) {
            Timber.e(e, "Failed to setup message callback")
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
            _isConnected.value = false
            subscribedTopics.clear()
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
            // Always add to desired topics
            desiredTopics.add(topic)
            
            try {
                val client = mqttClient
                if (client == null) {
                    Timber.w("Cannot subscribe to topic $topic: MQTT client is null, will subscribe when connected")
                    return@launch
                }
                
                if (!client.state.isConnected) {
                    Timber.w("Cannot subscribe to topic $topic: MQTT client not connected, state=${client.state}, will subscribe when connected")
                    return@launch
                }
                
                // Already subscribed?
                if (subscribedTopics.contains(topic)) {
                    Timber.d("Already subscribed to MQTT topic: $topic")
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
                            Timber.i("Successfully subscribed to MQTT topic: $topic")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to subscribe to MQTT topic: $topic")
            }
        }
    }

    fun unsubscribeFromTopic(topic: String) {
        adapterScope.launch {
            // Remove from desired topics
            desiredTopics.remove(topic)
            
            try {
                val client = mqttClient
                if (client == null || !client.state.isConnected) {
                    // Just remove from our tracking
                    subscribedTopics.remove(topic)
                    Timber.d("Removed topic from desired subscriptions: $topic")
                    return@launch
                }
                
                client.unsubscribeWith()
                    .topicFilter(topic)
                    .send()
                    .whenComplete { _, throwable ->
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
        Timber.d("MQTT: Resubscribing to ${desiredTopics.size} topics: $desiredTopics")
        
        desiredTopics.forEach { topic ->
            try {
                val client = mqttClient
                if (client == null || !client.state.isConnected) {
                    Timber.w("Cannot resubscribe to $topic: client not ready")
                    return@forEach
                }
                
                Timber.d("Resubscribing to MQTT topic: $topic")
                client.subscribeWith()
                    .topicFilter(topic)
                    .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                    .send()
                    .whenComplete { _, throwable ->
                        if (throwable != null) {
                            Timber.e(throwable, "Failed to re-subscribe to MQTT topic: $topic")
                        } else {
                            subscribedTopics.add(topic)
                            Timber.i("Successfully re-subscribed to MQTT topic: $topic")
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to re-subscribe to MQTT topic: $topic")
            }
        }
    }

    fun updateSubscriptions(topics: Set<String>) {
        val currentDesired = desiredTopics.toSet()
        val topicsToAdd = topics - currentDesired
        val topicsToRemove = currentDesired - topics

        Timber.d("MQTT: Update subscriptions - current desired: $currentDesired, new: $topics")
        Timber.d("MQTT: Topics to add: $topicsToAdd, topics to remove: $topicsToRemove")

        topicsToAdd.forEach { subscribeToTopic(it) }
        topicsToRemove.forEach { unsubscribeFromTopic(it) }
    }

    fun teardown() {
        desiredTopics.clear()
        subscribedTopics.clear()
        disconnectFromMqttBroker()
        adapterScope.cancel()
    }
}
