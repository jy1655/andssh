package com.opencode.sshterminal.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.opencode.sshterminal.sftp.SftpChannelAdapter
import com.opencode.sshterminal.sftp.SshjSftpAdapter
import com.opencode.sshterminal.ssh.SshClient
import com.opencode.sshterminal.ssh.SshjClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "connections")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun provideSshClient(): SshClient {
        return SshjClient()
    }

    @Provides
    fun provideSftpAdapter(): SftpChannelAdapter {
        return SshjSftpAdapter()
    }
}
