package com.opencode.sshterminal.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.opencode.sshterminal.R

@Suppress("LongParameterList")
@Composable
fun LockScreen(
    isFirstSetup: Boolean,
    error: String?,
    isBiometricEnabled: Boolean,
    canUseBiometric: Boolean,
    onUnlock: (String) -> Unit,
    onSetupPassword: (String) -> Unit,
    onSkipSetup: () -> Unit,
    onUseBiometric: () -> Unit,
    onClearError: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (isFirstSetup) {
            SetupContent(
                error = error,
                onSetupPassword = onSetupPassword,
                onSkipSetup = onSkipSetup,
                onClearError = onClearError,
            )
        } else {
            UnlockContent(
                error = error,
                shouldAutoTriggerBiometric = isBiometricEnabled,
                canUseBiometric = canUseBiometric,
                onUnlock = onUnlock,
                onUseBiometric = onUseBiometric,
                onClearError = onClearError,
            )
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun UnlockContent(
    error: String?,
    shouldAutoTriggerBiometric: Boolean,
    canUseBiometric: Boolean,
    onUnlock: (String) -> Unit,
    onUseBiometric: () -> Unit,
    onClearError: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    val canUnlock = password.isNotBlank()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, shouldAutoTriggerBiometric) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && shouldAutoTriggerBiometric) {
                    onUseBiometric()
                }
            }
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)
        if (shouldAutoTriggerBiometric && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            onUseBiometric()
        }
        onDispose { lifecycle.removeObserver(observer) }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.lock_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                onClearError()
            },
            label = { Text(stringResource(R.string.lock_password_label)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!error.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = mapErrorMessage(error = error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            enabled = canUnlock,
            onClick = {
                onUnlock(password)
                password = ""
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.lock_unlock_button))
        }
        if (canUseBiometric) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = onUseBiometric,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.lock_use_biometric))
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun SetupContent(
    error: String?,
    onSetupPassword: (String) -> Unit,
    onSkipSetup: () -> Unit,
    onClearError: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val canSubmit = password.isNotBlank() && confirmPassword.isNotBlank()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.lock_setup_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.lock_setup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                localError = null
                onClearError()
            },
            label = { Text(stringResource(R.string.lock_password_label)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                localError = null
                onClearError()
            },
            label = { Text(stringResource(R.string.lock_confirm_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            modifier = Modifier.fillMaxWidth(),
        )
        val message = localError ?: error
        if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = mapErrorMessage(error = message),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            enabled = canSubmit,
            onClick = {
                if (password != confirmPassword) {
                    localError = LockViewModel.ERROR_PASSWORDS_MISMATCH
                } else {
                    onSetupPassword(password)
                    password = ""
                    confirmPassword = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.lock_set_password))
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onSkipSetup,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.lock_skip_setup))
        }
    }
}

@Composable
private fun mapErrorMessage(error: String): String {
    return when (error) {
        LockViewModel.ERROR_WRONG_PASSWORD -> stringResource(R.string.lock_wrong_password)
        LockViewModel.ERROR_PASSWORDS_MISMATCH -> stringResource(R.string.lock_passwords_mismatch)
        LockViewModel.ERROR_BIOMETRIC_KEY_UNAVAILABLE -> stringResource(R.string.lock_biometric_key_unavailable)
        else -> error
    }
}
