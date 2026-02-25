package com.opencode.sshterminal.ui.lock

import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockViewModelLogicTest {
    @Test
    fun `canOfferBiometricUnlock returns true only when all conditions are met`() {
        val canOffer =
            canOfferBiometricUnlock(
                biometricEnabled = true,
                biometricAvailable = true,
                hasBiometricKey = true,
                isLocked = true,
                isFirstSetup = false,
            )

        assertTrue(canOffer)
    }

    @Test
    fun `canOfferBiometricUnlock returns false when any condition is not met`() {
        assertFalse(
            canOfferBiometricUnlock(
                biometricEnabled = false,
                biometricAvailable = true,
                hasBiometricKey = true,
                isLocked = true,
                isFirstSetup = false,
            ),
        )
        assertFalse(
            canOfferBiometricUnlock(
                biometricEnabled = true,
                biometricAvailable = false,
                hasBiometricKey = true,
                isLocked = true,
                isFirstSetup = false,
            ),
        )
        assertFalse(
            canOfferBiometricUnlock(
                biometricEnabled = true,
                biometricAvailable = true,
                hasBiometricKey = false,
                isLocked = true,
                isFirstSetup = false,
            ),
        )
        assertFalse(
            canOfferBiometricUnlock(
                biometricEnabled = true,
                biometricAvailable = true,
                hasBiometricKey = true,
                isLocked = false,
                isFirstSetup = false,
            ),
        )
        assertFalse(
            canOfferBiometricUnlock(
                biometricEnabled = true,
                biometricAvailable = true,
                hasBiometricKey = true,
                isLocked = true,
                isFirstSetup = true,
            ),
        )
    }

    @Test
    fun `shouldDisplayBiometricError hides cancel and negative button events`() {
        assertFalse(shouldDisplayBiometricError(BiometricPrompt.ERROR_NEGATIVE_BUTTON))
        assertFalse(shouldDisplayBiometricError(BiometricPrompt.ERROR_USER_CANCELED))
    }

    @Test
    fun `shouldDisplayBiometricError shows other error events`() {
        assertTrue(shouldDisplayBiometricError(BiometricPrompt.ERROR_TIMEOUT))
    }
}
