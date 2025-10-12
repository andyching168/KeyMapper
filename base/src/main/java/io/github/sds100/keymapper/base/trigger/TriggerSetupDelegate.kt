package io.github.sds100.keymapper.base.trigger

import android.os.Build
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.sds100.keymapper.base.system.accessibility.ControlAccessibilityServiceUseCase
import io.github.sds100.keymapper.base.system.accessibility.FingerprintGestureType
import io.github.sds100.keymapper.base.utils.navigation.NavDestination
import io.github.sds100.keymapper.base.utils.navigation.NavigationProvider
import io.github.sds100.keymapper.base.utils.navigation.navigate
import io.github.sds100.keymapper.base.utils.ui.DialogProvider
import io.github.sds100.keymapper.base.utils.ui.ResourceProvider
import io.github.sds100.keymapper.base.utils.ui.ViewModelHelper
import io.github.sds100.keymapper.common.utils.Constants
import io.github.sds100.keymapper.common.utils.KMError
import io.github.sds100.keymapper.common.utils.KMResult
import io.github.sds100.keymapper.common.utils.onFailure
import io.github.sds100.keymapper.common.utils.onSuccess
import io.github.sds100.keymapper.data.Keys
import io.github.sds100.keymapper.data.repositories.PreferenceRepository
import io.github.sds100.keymapper.sysbridge.manager.SystemBridgeConnectionManager
import io.github.sds100.keymapper.sysbridge.manager.SystemBridgeConnectionState
import io.github.sds100.keymapper.system.accessibility.AccessibilityServiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

@OptIn(ExperimentalCoroutinesApi::class)
@ViewModelScoped
class TriggerSetupDelegateImpl @Inject constructor(
    @Named("viewmodel")
    val viewModelScope: CoroutineScope,
    val controlAccessibilityServiceUseCase: ControlAccessibilityServiceUseCase,
    val recordTriggerController: RecordTriggerController,
    val systemBridgeConnectionManager: SystemBridgeConnectionManager,
    val configTriggerUseCase: ConfigTriggerUseCase,
    val setupInputMethodUseCase: SetupInputMethodUseCase,
    val preferenceRepository: PreferenceRepository,
    resourceProvider: ResourceProvider,
    dialogProvider: DialogProvider,
    navigationProvider: NavigationProvider,
) : TriggerSetupDelegate,
    ResourceProvider by resourceProvider,
    DialogProvider by dialogProvider,
    NavigationProvider by navigationProvider {

    private val currentSetupShortcut: MutableStateFlow<TriggerSetupShortcut?> =
        MutableStateFlow(null)

    private val isScreenOffChecked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val selectedFingerprintGestureType: MutableStateFlow<FingerprintGestureType> =
        MutableStateFlow(FingerprintGestureType.SWIPE_DOWN)

    private val selectedGamePadType: MutableStateFlow<TriggerSetupState.Gamepad.Type> =
        MutableStateFlow(TriggerSetupState.Gamepad.Type.DPAD)

    // MQTT state
    private val mqttBrokerAddress: MutableStateFlow<String> = MutableStateFlow("")
    private val mqttBrokerPort: MutableStateFlow<Int> = MutableStateFlow(1883)
    private val mqttUsername: MutableStateFlow<String> = MutableStateFlow("")
    private val mqttPassword: MutableStateFlow<String> = MutableStateFlow("")
    private val mqttTopic: MutableStateFlow<String> = MutableStateFlow("")
    private val mqttMessagePattern: MutableStateFlow<String> = MutableStateFlow("")
    private val mqttMatchType: MutableStateFlow<MqttMatchType> = MutableStateFlow(MqttMatchType.EXACT)

    private val proModeStatus: Flow<ProModeStatus> =
        if (Build.VERSION.SDK_INT >= Constants.SYSTEM_BRIDGE_MIN_API) {
            systemBridgeConnectionManager.connectionState.map { state ->
                when (state) {
                    is SystemBridgeConnectionState.Connected -> ProModeStatus.ENABLED
                    is SystemBridgeConnectionState.Disconnected -> ProModeStatus.DISABLED
                }
            }
        } else {
            flowOf(ProModeStatus.UNSUPPORTED)
        }

    override val triggerSetupState: StateFlow<TriggerSetupState?> =
        currentSetupShortcut.flatMapLatest { shortcut ->
            if (shortcut == null) {
                flowOf(null)
            } else {
                when (shortcut) {
                    TriggerSetupShortcut.VOLUME -> buildSetupVolumeTriggerFlow()
                    TriggerSetupShortcut.POWER -> buildSetupPowerTriggerFlow()
                    TriggerSetupShortcut.FINGERPRINT_GESTURE -> buildSetupFingerprintGestureFlow()
                    TriggerSetupShortcut.KEYBOARD -> buildSetupKeyboardTriggerFlow()
                    TriggerSetupShortcut.MOUSE -> buildSetupMouseTriggerFlow()
                    TriggerSetupShortcut.GAMEPAD -> buildSetupGamepadTriggerFlow()
                    TriggerSetupShortcut.OTHER -> buildSetupOtherTriggerFlow()
                    TriggerSetupShortcut.NOT_DETECTED -> buildSetupNotDetectedFlow()
                    TriggerSetupShortcut.MQTT -> buildSetupMqttTriggerFlow()

                    else -> throw UnsupportedOperationException("Unhandled shortcut: $shortcut")
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    override fun showTriggerSetup(shortcut: TriggerSetupShortcut) {
        currentSetupShortcut.value = shortcut
        
        // Load saved MQTT broker settings when showing MQTT setup
        if (shortcut == TriggerSetupShortcut.MQTT) {
            viewModelScope.launch {
                // Load broker address
                preferenceRepository.get(Keys.mqttBrokerUrl).firstOrNull()?.let { savedBrokerUrl ->
                    if (savedBrokerUrl.isNotBlank()) {
                        mqttBrokerAddress.value = savedBrokerUrl
                    }
                }
                
                // Load broker port
                preferenceRepository.get(Keys.mqttBrokerPort).firstOrNull()?.let { savedPort ->
                    savedPort.toIntOrNull()?.let { port ->
                        mqttBrokerPort.value = port
                    }
                }
                
                // Load username
                preferenceRepository.get(Keys.mqttUsername).firstOrNull()?.let { savedUsername ->
                    if (savedUsername.isNotBlank()) {
                        mqttUsername.value = savedUsername
                    }
                }
                
                // Load password
                preferenceRepository.get(Keys.mqttPassword).firstOrNull()?.let { savedPassword ->
                    if (savedPassword.isNotBlank()) {
                        mqttPassword.value = savedPassword
                    }
                }
                
                Timber.d("MQTT: Loaded saved broker settings - broker=${mqttBrokerAddress.value}:${mqttBrokerPort.value}, username=${mqttUsername.value}")
            }
        }
    }

    private fun buildSetupVolumeTriggerFlow(): Flow<TriggerSetupState> {
        return combine(
            controlAccessibilityServiceUseCase.serviceState,
            isScreenOffChecked,
            recordTriggerController.state,
            proModeStatus,
        ) { serviceState, isScreenOffChecked, recordTriggerState, proModeStatus ->
            val areRequirementsMet = if (isScreenOffChecked) {
                serviceState == AccessibilityServiceState.ENABLED && proModeStatus == ProModeStatus.ENABLED
            } else {
                serviceState == AccessibilityServiceState.ENABLED
            }

            TriggerSetupState.Volume(
                isAccessibilityServiceEnabled = serviceState == AccessibilityServiceState.ENABLED,
                isScreenOffChecked = isScreenOffChecked,
                proModeStatus = proModeStatus,
                areRequirementsMet = areRequirementsMet,
                recordTriggerState = recordTriggerState,
            )
        }
    }

    private fun buildSetupGamepadTriggerFlow(): Flow<TriggerSetupState> {
        return selectedGamePadType.flatMapLatest { selectedGamepadType ->
            when (selectedGamepadType) {
                TriggerSetupState.Gamepad.Type.DPAD -> {
                    combine(
                        controlAccessibilityServiceUseCase.serviceState,
                        setupInputMethodUseCase.isEnabled,
                        setupInputMethodUseCase.isChosen,
                        recordTriggerController.state,
                    ) { serviceState, isImeEnabled, isImeChosen, recordTriggerState ->
                        val areRequirementsMet =
                            serviceState == AccessibilityServiceState.ENABLED && isImeEnabled && isImeChosen

                        TriggerSetupState.Gamepad.Dpad(
                            isAccessibilityServiceEnabled = serviceState == AccessibilityServiceState.ENABLED,
                            isImeEnabled = isImeEnabled,
                            isImeChosen = isImeChosen,
                            areRequirementsMet = areRequirementsMet,
                            recordTriggerState = recordTriggerState,
                            enablingRequiresUserInput = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
                        )
                    }
                }

                TriggerSetupState.Gamepad.Type.SIMPLE_BUTTONS -> {
                    combine(
                        controlAccessibilityServiceUseCase.serviceState,
                        isScreenOffChecked,
                        recordTriggerController.state,
                        proModeStatus,
                    ) { serviceState, isScreenOffChecked, recordTriggerState, proModeStatus ->
                        val areRequirementsMet = if (isScreenOffChecked) {
                            serviceState == AccessibilityServiceState.ENABLED && proModeStatus == ProModeStatus.ENABLED
                        } else {
                            serviceState == AccessibilityServiceState.ENABLED
                        }

                        TriggerSetupState.Gamepad.SimpleButtons(
                            isAccessibilityServiceEnabled = serviceState == AccessibilityServiceState.ENABLED,
                            isScreenOffChecked = isScreenOffChecked,
                            proModeStatus = proModeStatus,
                            areRequirementsMet = areRequirementsMet,
                            recordTriggerState = recordTriggerState,
                        )
                    }
                }
            }
        }
    }

    private fun buildSetupOtherTriggerFlow(): Flow<TriggerSetupState> {
        return combine(
            controlAccessibilityServiceUseCase.serviceState,
            isScreenOffChecked,
            recordTriggerController.state,
            proModeStatus,
        ) { serviceState, isScreenOffChecked, recordTriggerState, proModeStatus ->
            val areRequirementsMet = if (isScreenOffChecked) {
                serviceState == AccessibilityServiceState.ENABLED && proModeStatus == ProModeStatus.ENABLED
            } else {
                serviceState == AccessibilityServiceState.ENABLED
            }

            TriggerSetupState.Other(
                isAccessibilityServiceEnabled = serviceState == AccessibilityServiceState.ENABLED,
                isScreenOffChecked = isScreenOffChecked,
                proModeStatus = proModeStatus,
                areRequirementsMet = areRequirementsMet,
                recordTriggerState = recordTriggerState,
            )
        }
    }

    private fun buildSetupNotDetectedFlow(): Flow<TriggerSetupState> {
        return combine(
            controlAccessibilityServiceUseCase.serviceState,
            recordTriggerController.state,
            proModeStatus,
        ) { serviceState, recordTriggerState, proModeStatus ->
            val areRequirementsMet =
                serviceState == AccessibilityServiceState.ENABLED && proModeStatus == ProModeStatus.ENABLED

            TriggerSetupState.NotDetected(
                isAccessibilityServiceEnabled = serviceState == AccessibilityServiceState.ENABLED,
                proModeStatus = proModeStatus,
                areRequirementsMet = areRequirementsMet,
                recordTriggerState = recordTriggerState,
            )
        }
    }

    private fun buildSetupKeyboardTriggerFlow(): Flow<TriggerSetupState> {
        return combine(
            controlAccessibilityServiceUseCase.serviceState,
            isScreenOffChecked,
            recordTriggerController.state,
            proModeStatus,
        ) { serviceState, isScreenOffChecked, recordTriggerState, proModeStatus ->
            val areRequirementsMet = if (isScreenOffChecked) {
                serviceState == AccessibilityServiceState.ENABLED && proModeStatus == ProModeStatus.ENABLED
            } else {
                serviceState == AccessibilityServiceState.ENABLED
            }

            TriggerSetupState.Keyboard(
                isAccessibilityServiceEnabled = serviceState == AccessibilityServiceState.ENABLED,
                isScreenOffChecked = isScreenOffChecked,
                proModeStatus = proModeStatus,
                areRequirementsMet = areRequirementsMet,
                recordTriggerState = recordTriggerState,
            )
        }
    }

    private fun buildSetupFingerprintGestureFlow(): Flow<TriggerSetupState> {
        return combine(
            controlAccessibilityServiceUseCase.serviceState,
            selectedFingerprintGestureType,
        ) { serviceState, gestureType ->
            val areRequirementsMet = serviceState == AccessibilityServiceState.ENABLED

            TriggerSetupState.FingerprintGesture(
                isAccessibilityServiceEnabled = serviceState == AccessibilityServiceState.ENABLED,
                areRequirementsMet = areRequirementsMet,
                selectedType = gestureType,
            )
        }
    }

    private fun buildSetupMqttTriggerFlow(): Flow<TriggerSetupState> {
        return mqttBrokerAddress.flatMapLatest { brokerAddress ->
            mqttBrokerPort.flatMapLatest { brokerPort ->
                mqttUsername.flatMapLatest { username ->
                    mqttPassword.flatMapLatest { password ->
                        mqttTopic.flatMapLatest { topic ->
                            mqttMessagePattern.flatMapLatest { messagePattern ->
                                mqttMatchType.map { matchType ->
                                    val areRequirementsMet = brokerAddress.isNotBlank() && topic.isNotBlank()

                                    TriggerSetupState.Mqtt(
                                        brokerAddress = brokerAddress,
                                        brokerPort = brokerPort,
                                        username = username,
                                        password = password,
                                        topic = topic,
                                        messagePattern = messagePattern,
                                        matchType = matchType,
                                        areRequirementsMet = areRequirementsMet,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun buildSetupPowerTriggerFlow(): Flow<TriggerSetupState> {
        return combine(
            controlAccessibilityServiceUseCase.serviceState,
            recordTriggerController.state,
            proModeStatus,
        ) { serviceState, recordTriggerState, proModeStatus ->
            val areRequirementsMet =
                serviceState == AccessibilityServiceState.ENABLED && proModeStatus == ProModeStatus.ENABLED

            val remapStatus = if (Build.VERSION.SDK_INT >= Constants.SYSTEM_BRIDGE_MIN_API) {
                if (areRequirementsMet) {
                    RemapStatus.SUPPORTED
                } else {
                    RemapStatus.UNCERTAIN
                }
            } else {
                RemapStatus.UNSUPPORTED
            }

            TriggerSetupState.Power(
                isAccessibilityServiceEnabled = serviceState == AccessibilityServiceState.ENABLED,
                proModeStatus = proModeStatus,
                areRequirementsMet = areRequirementsMet,
                recordTriggerState = recordTriggerState,
                remapStatus = remapStatus,
            )
        }
    }

    private fun buildSetupMouseTriggerFlow(): Flow<TriggerSetupState> {
        return combine(
            controlAccessibilityServiceUseCase.serviceState,
            recordTriggerController.state,
            proModeStatus,
        ) { serviceState, recordTriggerState, proModeStatus ->
            val areRequirementsMet =
                serviceState == AccessibilityServiceState.ENABLED && proModeStatus == ProModeStatus.ENABLED

            val remapStatus = if (Build.VERSION.SDK_INT >= Constants.SYSTEM_BRIDGE_MIN_API) {
                if (areRequirementsMet) {
                    RemapStatus.SUPPORTED
                } else {
                    RemapStatus.UNCERTAIN
                }
            } else {
                RemapStatus.UNSUPPORTED
            }

            TriggerSetupState.Mouse(
                isAccessibilityServiceEnabled = serviceState == AccessibilityServiceState.ENABLED,
                proModeStatus = proModeStatus,
                areRequirementsMet = areRequirementsMet,
                recordTriggerState = recordTriggerState,
                remapStatus = remapStatus,
            )
        }
    }

    override fun onEnableAccessibilityServiceClick() {
        viewModelScope.launch {
            val state = controlAccessibilityServiceUseCase.serviceState.first()

            if (state == AccessibilityServiceState.DISABLED) {
                ViewModelHelper.handleAccessibilityServiceStoppedDialog(
                    resourceProvider = this@TriggerSetupDelegateImpl,
                    dialogProvider = this@TriggerSetupDelegateImpl,
                    startService = controlAccessibilityServiceUseCase::startService,
                )
            } else if (state == AccessibilityServiceState.CRASHED) {
                ViewModelHelper.handleAccessibilityServiceCrashedDialog(
                    resourceProvider = this@TriggerSetupDelegateImpl,
                    dialogProvider = this@TriggerSetupDelegateImpl,
                    restartService = controlAccessibilityServiceUseCase::restartService,
                )
            }
        }
    }

    override fun onEnableProModeClick() {
        viewModelScope.launch {
            navigate("trigger_setup_enable_pro_mode", NavDestination.ProMode)
        }
    }

    override fun onScreenOffTriggerSetupCheckedChange(isChecked: Boolean) {
        isScreenOffChecked.value = isChecked
    }

    override fun onDismissTriggerSetup() {
        // Reset MQTT topic and message pattern when closing the setup
        // (Keep broker settings for reuse)
        if (currentSetupShortcut.value == TriggerSetupShortcut.MQTT) {
            mqttTopic.value = ""
            mqttMessagePattern.value = ""
            mqttMatchType.value = MqttMatchType.ANY
        }
        
        currentSetupShortcut.value = null
    }

    override fun onTriggerSetupRecordClick() {
        val setupState = triggerSetupState.value ?: return

        val enableEvdevRecording = when (setupState) {
            is TriggerSetupState.Volume -> setupState.isScreenOffChecked
            is TriggerSetupState.Keyboard -> setupState.isScreenOffChecked
            is TriggerSetupState.Power -> true
            is TriggerSetupState.FingerprintGesture -> false
            is TriggerSetupState.Mouse -> true
            is TriggerSetupState.Other -> setupState.isScreenOffChecked
            is TriggerSetupState.Gamepad.Dpad -> false
            is TriggerSetupState.Gamepad.SimpleButtons -> setupState.isScreenOffChecked
            is TriggerSetupState.Mqtt -> false
            // Always enable pro mode recording to increase the chances of detecting
            // the key
            is TriggerSetupState.NotDetected -> true
        }

        viewModelScope.launch {
            val recordTriggerState = recordTriggerController.state.firstOrNull() ?: return@launch

            val result: KMResult<*> = when (recordTriggerState) {
                is RecordTriggerState.CountingDown -> {
                    recordTriggerController.stopRecording()
                }

                is RecordTriggerState.Completed,
                RecordTriggerState.Idle,
                -> recordTriggerController.startRecording(
                    enableEvdevRecording,
                )
            }

            result.onSuccess {
                currentSetupShortcut.value = null
            }

            // Show dialog if the accessibility service is disabled or crashed
            handleServiceEventResult(result)
        }
    }

    override fun onFingerprintGestureTypeSelected(type: FingerprintGestureType) {
        selectedFingerprintGestureType.value = type
    }

    override fun onAddFingerprintGestureClick() {
        configTriggerUseCase.addFingerprintGesture(selectedFingerprintGestureType.value)
        currentSetupShortcut.value = null
    }

    override fun onGamepadButtonTypeSelected(type: TriggerSetupState.Gamepad.Type) {
        selectedGamePadType.value = type
    }

    override fun onEnableImeClick() {
        viewModelScope.launch {
            setupInputMethodUseCase.enableInputMethod()
        }
    }

    override fun onChooseImeClick() {
        viewModelScope.launch {
            setupInputMethodUseCase.chooseInputMethod().onFailure {
                Timber.e("Failed to choose input method when setting up trigger. Error: $it")
            }
        }
    }

    override fun onMqttBrokerAddressChanged(address: String) {
        mqttBrokerAddress.value = address
    }

    override fun onMqttBrokerPortChanged(port: Int) {
        mqttBrokerPort.value = port
    }

    override fun onMqttUsernameChanged(username: String) {
        mqttUsername.value = username
    }

    override fun onMqttPasswordChanged(password: String) {
        mqttPassword.value = password
    }

    override fun onMqttTopicChanged(topic: String) {
        mqttTopic.value = topic
    }

    override fun onMqttMessagePatternChanged(pattern: String) {
        mqttMessagePattern.value = pattern
    }

    override fun onMqttMatchTypeChanged(matchType: MqttMatchType) {
        mqttMatchType.value = matchType
    }

    override fun onAddMqttTriggerClick() {
        val state = triggerSetupState.value as? TriggerSetupState.Mqtt ?: return
        
        // Save MQTT broker settings to preferences
        viewModelScope.launch {
            preferenceRepository.set(Keys.mqttBrokerUrl, state.brokerAddress)
            preferenceRepository.set(Keys.mqttBrokerPort, state.brokerPort.toString())
            
            if (state.username.isNotBlank()) {
                preferenceRepository.set(Keys.mqttUsername, state.username)
            }
            
            if (state.password.isNotBlank()) {
                preferenceRepository.set(Keys.mqttPassword, state.password)
            }
            
            Timber.d("MQTT: Saved broker settings - broker=${state.brokerAddress}:${state.brokerPort}, username=${state.username}")
        }
        
        configTriggerUseCase.addMqttTriggerKey(
            topic = state.topic,
            messagePattern = state.messagePattern,
            matchType = state.matchType,
        )
        currentSetupShortcut.value = null
    }

    private suspend fun handleServiceEventResult(result: KMResult<*>) {
        if (result is KMError.AccessibilityServiceDisabled) {
            ViewModelHelper.handleAccessibilityServiceStoppedDialog(
                resourceProvider = this,
                dialogProvider = this,
                startService = controlAccessibilityServiceUseCase::startService,
            )
        }

        if (result is KMError.AccessibilityServiceCrashed) {
            ViewModelHelper.handleAccessibilityServiceCrashedDialog(
                resourceProvider = this,
                dialogProvider = this,
                restartService = controlAccessibilityServiceUseCase::restartService,
            )
        }
    }
}

interface TriggerSetupDelegate {
    val triggerSetupState: StateFlow<TriggerSetupState?>
    fun showTriggerSetup(shortcut: TriggerSetupShortcut)
    fun onDismissTriggerSetup()
    fun onEnableAccessibilityServiceClick()
    fun onEnableProModeClick()
    fun onScreenOffTriggerSetupCheckedChange(isChecked: Boolean)
    fun onTriggerSetupRecordClick()
    fun onFingerprintGestureTypeSelected(type: FingerprintGestureType)
    fun onAddFingerprintGestureClick()
    fun onGamepadButtonTypeSelected(type: TriggerSetupState.Gamepad.Type)
    fun onEnableImeClick()
    fun onChooseImeClick()
    fun onMqttBrokerAddressChanged(address: String)
    fun onMqttBrokerPortChanged(port: Int)
    fun onMqttUsernameChanged(username: String)
    fun onMqttPasswordChanged(password: String)
    fun onMqttTopicChanged(topic: String)
    fun onMqttMessagePatternChanged(pattern: String)
    fun onMqttMatchTypeChanged(matchType: MqttMatchType)
    fun onAddMqttTriggerClick()
}
