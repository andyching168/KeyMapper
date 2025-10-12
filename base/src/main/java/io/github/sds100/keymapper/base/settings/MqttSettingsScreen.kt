package io.github.sds100.keymapper.base.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sds100.keymapper.base.R
import io.github.sds100.keymapper.base.compose.KeyMapperTheme

@Composable
fun MqttSettingsScreen(modifier: Modifier = Modifier, viewModel: SettingsViewModel) {
    val state by viewModel.mqttSettingsState.collectAsStateWithLifecycle()

    MqttSettingsScreen(
        modifier,
        onBackClick = viewModel::onBackClick,
    ) {
        Content(
            state = state,
            callback = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MqttSettingsScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier.displayCutoutPadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_mqtt_title)) },
            )
        },
        bottomBar = {
            BottomAppBar {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_go_back),
                    )
                }
            }
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val startPadding = innerPadding.calculateStartPadding(layoutDirection)
        val endPadding = innerPadding.calculateEndPadding(layoutDirection)

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding(),
                    start = startPadding,
                    end = endPadding,
                ),
        ) {
            content()
        }
    }
}

@Composable
private fun Content(
    modifier: Modifier = Modifier,
    state: MqttSettingsState,
    callback: MqttSettingsCallback = object : MqttSettingsCallback {},
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Broker Address
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.brokerUrl,
            onValueChange = { callback.onBrokerUrlChanged(it) },
            label = { Text(stringResource(R.string.trigger_setup_mqtt_broker_address)) },
            placeholder = { Text("broker.example.com") },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Broker Port
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.brokerPort,
            onValueChange = { callback.onBrokerPortChanged(it) },
            label = { Text(stringResource(R.string.trigger_setup_mqtt_broker_port)) },
            placeholder = { Text("1883") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Username
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.username,
            onValueChange = { callback.onUsernameChanged(it) },
            label = { 
                Text(stringResource(R.string.trigger_setup_mqtt_username) + " " + 
                     stringResource(R.string.trigger_setup_mqtt_optional)) 
            },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Password
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.password,
            onValueChange = { callback.onPasswordChanged(it) },
            label = { 
                Text(stringResource(R.string.trigger_setup_mqtt_password) + " " + 
                     stringResource(R.string.trigger_setup_mqtt_optional)) 
            },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

interface MqttSettingsCallback {
    fun onBrokerUrlChanged(url: String) = run { }
    fun onBrokerPortChanged(port: String) = run { }
    fun onUsernameChanged(username: String) = run { }
    fun onPasswordChanged(password: String) = run { }
}

@Preview
@Composable
private fun Preview() {
    KeyMapperTheme {
        MqttSettingsScreen(modifier = Modifier.fillMaxSize(), onBackClick = {}) {
            Content(
                state = MqttSettingsState(),
            )
        }
    }
}
