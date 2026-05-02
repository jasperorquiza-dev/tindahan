package com.example.sarisaristore.ui.feature.unlock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.sarisaristore.data.repository.SettingsRepository
import com.example.sarisaristore.util.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UnlockUiState(
    val pin: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isUnlocked: Boolean = false,
)

class UnlockViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    fun addDigit(value: String) {
        if (_uiState.value.pin.length >= 4 || _uiState.value.isLoading) {
            return
        }
        val updatedPin = (_uiState.value.pin + value).take(4)
        _uiState.update { it.copy(pin = updatedPin, errorMessage = null) }
        if (updatedPin.length == 4) {
            unlock()
        }
    }

    fun removeLastDigit() {
        if (_uiState.value.isLoading) {
            return
        }
        _uiState.update {
            it.copy(
                pin = it.pin.dropLast(1),
                errorMessage = null,
            )
        }
    }

    fun clearPin() {
        _uiState.update { it.copy(pin = "", errorMessage = null) }
    }

    fun unlock() {
        val pin = _uiState.value.pin
        if (pin.length != 4) {
            _uiState.update { it.copy(errorMessage = "Enter your 4-digit passcode.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val isValid = settingsRepository.verifyPasscode(pin)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isUnlocked = isValid,
                    errorMessage = if (isValid) null else "Incorrect passcode.",
                    pin = if (isValid) it.pin else "",
                )
            }
        }
    }

    companion object {
        fun factory(settingsRepository: SettingsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    UnlockViewModel(settingsRepository)
                }
            }
    }
}

@Composable
fun UnlockRoute(
    onUnlocked: () -> Unit,
) {
    val container = androidx.compose.ui.platform.LocalContext.current.appContainer()
    val viewModel: UnlockViewModel = viewModel(
        factory = UnlockViewModel.factory(container.settingsRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) {
            onUnlocked()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Unlock Store",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Enter the 4-digit owner passcode.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    color = if (index < uiState.pin.length) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
                uiState.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                DigitPad(
                    onDigitPressed = viewModel::addDigit,
                    onBackspace = viewModel::removeLastDigit,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = viewModel::clearPin,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isLoading,
                    ) {
                        Text("Clear")
                    }
                    Button(
                        onClick = viewModel::unlock,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isLoading,
                    ) {
                        Text(if (uiState.isLoading) "Checking..." else "Unlock")
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitPad(
    onDigitPressed: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { digit ->
                    DigitButton(
                        label = digit,
                        onClick = { onDigitPressed(digit) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.size(72.dp))
            DigitButton(label = "0", onClick = { onDigitPressed("0") })
            IconButton(
                onClick = onBackspace,
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            ) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun DigitButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
