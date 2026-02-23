package com.opencode.sshterminal.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

@HiltAndroidApp
class SSHTerminalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        installBouncyCastleProvider()
    }

    private fun installBouncyCastleProvider() {
        val current = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        if (current == null || current.javaClass != BouncyCastleProvider::class.java) {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
    }
}
