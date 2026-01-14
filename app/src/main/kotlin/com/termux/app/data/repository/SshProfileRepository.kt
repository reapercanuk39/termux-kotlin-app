package com.termux.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.termux.app.data.model.SshAuthMethod
import com.termux.app.data.model.SshProfile
import com.termux.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing SSH connection profiles.
 */
@Singleton
class SshProfileRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val json = Json { ignoreUnknownKeys = true }

    private object PreferencesKeys {
        val SSH_PROFILES = stringPreferencesKey("ssh_profiles")
    }

    /**
     * Get all SSH profiles
     */
    val profiles: Flow<List<SshProfile>> = dataStore.data.map { preferences ->
        val jsonString = preferences[PreferencesKeys.SSH_PROFILES] ?: "[]"
        try {
            json.decodeFromString<List<SshProfileDto>>(jsonString).map { it.toProfile() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Add or update an SSH profile
     */
    suspend fun saveProfile(profile: SshProfile) {
        withContext(ioDispatcher) {
            dataStore.edit { preferences ->
                val currentProfiles = getCurrentProfiles(preferences)
                val updatedProfiles = currentProfiles
                    .filter { it.id != profile.id }
                    .plus(profile)
                    .sortedBy { it.name }

                preferences[PreferencesKeys.SSH_PROFILES] =
                    json.encodeToString(updatedProfiles.map { it.toDto() })
            }
        }
    }

    /**
     * Delete an SSH profile
     */
    suspend fun deleteProfile(profileId: String) {
        withContext(ioDispatcher) {
            dataStore.edit { preferences ->
                val currentProfiles = getCurrentProfiles(preferences)
                val updatedProfiles = currentProfiles.filter { it.id != profileId }

                preferences[PreferencesKeys.SSH_PROFILES] =
                    json.encodeToString(updatedProfiles.map { it.toDto() })
            }
        }
    }

    /**
     * Get a specific profile by ID
     */
    suspend fun getProfile(profileId: String): SshProfile? {
        return withContext(ioDispatcher) {
            dataStore.data.map { preferences ->
                getCurrentProfiles(preferences).find { it.id == profileId }
            }.let { flow ->
                var result: SshProfile? = null
                flow.collect { result = it }
                result
            }
        }
    }

    /**
     * Update last connected timestamp
     */
    suspend fun updateLastConnected(profileId: String) {
        withContext(ioDispatcher) {
            dataStore.edit { preferences ->
                val currentProfiles = getCurrentProfiles(preferences)
                val updatedProfiles = currentProfiles.map { profile ->
                    if (profile.id == profileId) {
                        profile.copy(lastConnected = System.currentTimeMillis())
                    } else {
                        profile
                    }
                }

                preferences[PreferencesKeys.SSH_PROFILES] =
                    json.encodeToString(updatedProfiles.map { it.toDto() })
            }
        }
    }

    /**
     * Generate SSH command for a profile
     */
    fun generateSshCommand(profile: SshProfile): String {
        return buildString {
            append("ssh ")

            // Add port if not default
            if (profile.port != 22) {
                append("-p ${profile.port} ")
            }

            // Add key file if using public key auth
            if (profile.authMethod is SshAuthMethod.PublicKey && profile.privateKeyPath != null) {
                append("-i ${profile.privateKeyPath} ")
            }

            // Add user@host
            append("${profile.username}@${profile.host}")
        }
    }

    /**
     * Import profiles from JSON string
     */
    suspend fun importProfiles(jsonString: String): Int {
        return withContext(ioDispatcher) {
            try {
                val importedProfiles = json.decodeFromString<List<SshProfileDto>>(jsonString)
                    .map { it.toProfile().copy(id = UUID.randomUUID().toString()) }

                dataStore.edit { preferences ->
                    val currentProfiles = getCurrentProfiles(preferences)
                    val allProfiles = (currentProfiles + importedProfiles).distinctBy {
                        "${it.host}:${it.port}:${it.username}"
                    }

                    preferences[PreferencesKeys.SSH_PROFILES] =
                        json.encodeToString(allProfiles.map { it.toDto() })
                }

                importedProfiles.size
            } catch (e: Exception) {
                0
            }
        }
    }

    /**
     * Export profiles to JSON string
     */
    suspend fun exportProfiles(): String {
        return withContext(ioDispatcher) {
            dataStore.data.map { preferences ->
                val profiles = getCurrentProfiles(preferences)
                json.encodeToString(profiles.map { it.toDto() })
            }.let { flow ->
                var result = "[]"
                flow.collect { result = it }
                result
            }
        }
    }

    private fun getCurrentProfiles(preferences: Preferences): List<SshProfile> {
        val jsonString = preferences[PreferencesKeys.SSH_PROFILES] ?: "[]"
        return try {
            json.decodeFromString<List<SshProfileDto>>(jsonString).map { it.toProfile() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // DTO for serialization
    @Serializable
    private data class SshProfileDto(
        val id: String,
        val name: String,
        val host: String,
        val port: Int,
        val username: String,
        val authMethod: String,
        val privateKeyPath: String?,
        val lastConnected: Long?
    )

    private fun SshProfile.toDto() = SshProfileDto(
        id = id,
        name = name,
        host = host,
        port = port,
        username = username,
        authMethod = when (authMethod) {
            is SshAuthMethod.Password -> "password"
            is SshAuthMethod.PublicKey -> "publickey"
            is SshAuthMethod.Agent -> "agent"
        },
        privateKeyPath = privateKeyPath,
        lastConnected = lastConnected
    )

    private fun SshProfileDto.toProfile() = SshProfile(
        id = id,
        name = name,
        host = host,
        port = port,
        username = username,
        authMethod = when (authMethod) {
            "publickey" -> SshAuthMethod.PublicKey
            "agent" -> SshAuthMethod.Agent
            else -> SshAuthMethod.Password
        },
        privateKeyPath = privateKeyPath,
        lastConnected = lastConnected
    )
}
