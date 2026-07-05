package com.uzairansar.hermex.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uzairansar.hermex.data.preferences.AppThemeMode
import com.uzairansar.hermex.data.preferences.LocalSettingsRepository
import com.uzairansar.hermex.data.repository.AuthRepository
import com.uzairansar.hermex.data.repository.AuthState
import com.uzairansar.hermex.data.repository.PanelsRepository

@Composable
fun SettingsRoute(
    authRepository: AuthRepository,
    localSettingsRepository: LocalSettingsRepository,
    panelsRepository: PanelsRepository?,
    authState: AuthState,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(authRepository, localSettingsRepository, panelsRepository) as T
        }
    })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loggedIn = authState as? AuthState.LoggedIn

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onBack) { Text("Back") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Server")
                Text(loggedIn?.server?.toString() ?: "Not configured", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("Server URLs, custom headers, and cookies are stored in Android encrypted storage.")
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Appearance")
                Text(state.themeMode.label, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        AssistChip(
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(if (state.themeMode == mode) "Current: ${mode.label}" else mode.label) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Default Model")
                Text(state.defaultModel ?: "Server default", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                when {
                    panelsRepository == null -> Text("Connect to a server to manage the default model.")
                    state.isLoadingModels -> CircularProgressIndicator()
                    state.models.isEmpty() -> Text("No models returned by the server.")
                    else -> Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.models.forEach { model ->
                            val id = model.id ?: model.name
                            AssistChip(
                                onClick = { viewModel.saveDefaultModel(model) },
                                enabled = !state.isSavingDefaultModel && id != null,
                                label = {
                                    Text(
                                        if (id == state.defaultModel) {
                                            "Current: ${model.label ?: id}"
                                        } else {
                                            model.label ?: id ?: "Model"
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        state.notice?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.tertiary)
        }
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.signOut(onSignedOut) },
            enabled = !state.isSigningOut && loggedIn != null,
        ) {
            Text(if (state.isSigningOut) "Signing out..." else "Sign out")
        }
    }
}
