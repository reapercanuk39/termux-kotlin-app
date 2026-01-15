package com.termux.app.ui.settings.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room entity for terminal profiles.
 * 
 * A profile contains a complete set of terminal settings that can be
 * switched as a unit. This enables use cases like:
 * - "Work" profile with SSH agent and dark theme
 * - "Dev" profile with Nerd font and custom PATH
 * - "Minimal" profile with no plugins for fast startup
 */
@Entity(tableName = "profiles")
@Serializable
data class ProfileEntity(
    @PrimaryKey 
    val id: String = UUID.randomUUID().toString(),
    
    val name: String,
    val description: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    
    // Appearance
    val fontFamily: String = TermuxSettings.Defaults.FONT_FAMILY,
    val fontSize: Int = TermuxSettings.Defaults.FONT_SIZE,
    val themeName: String = TermuxSettings.Defaults.THEME_NAME,
    val lineSpacing: Float = TermuxSettings.Defaults.LINE_SPACING,
    val ligaturesEnabled: Boolean = TermuxSettings.Defaults.LIGATURES_ENABLED,
    
    // Shell configuration
    val shell: String = "/data/data/com.termux/files/usr/bin/bash",
    @ColumnInfo(name = "startup_commands")
    val startupCommandsJson: String = "[]",
    @ColumnInfo(name = "environment_variables")
    val environmentVariablesJson: String = "{}",
    
    // Behavior
    val bellEnabled: Boolean = TermuxSettings.Defaults.BELL_ENABLED,
    val vibrationEnabled: Boolean = TermuxSettings.Defaults.VIBRATION_ENABLED,
    val keepScreenOn: Boolean = TermuxSettings.Defaults.KEEP_SCREEN_ON,
    
    // Keyboard
    val extraKeysStyle: String = "default",
    val softKeyboardEnabled: Boolean = TermuxSettings.Defaults.SOFT_KEYBOARD_ENABLED,
    
    // Plugins
    @ColumnInfo(name = "enabled_plugins")
    val enabledPluginsJson: String = "[]"
) {
    /**
     * Convert to domain Profile model.
     */
    fun toProfile(): Profile = Profile(
        id = id,
        name = name,
        description = description,
        isDefault = isDefault,
        fontFamily = fontFamily,
        fontSize = fontSize,
        themeName = themeName,
        lineSpacing = lineSpacing,
        ligaturesEnabled = ligaturesEnabled,
        bellEnabled = bellEnabled,
        vibrationEnabled = vibrationEnabled,
        keepScreenOn = keepScreenOn,
        softKeyboardEnabled = softKeyboardEnabled
    )
    
    companion object {
        /**
         * Create entity from domain Profile.
         */
        fun fromProfile(profile: Profile): ProfileEntity = ProfileEntity(
            id = profile.id,
            name = profile.name,
            description = profile.description,
            isDefault = profile.isDefault,
            fontFamily = profile.fontFamily,
            fontSize = profile.fontSize,
            themeName = profile.themeName,
            lineSpacing = profile.lineSpacing,
            ligaturesEnabled = profile.ligaturesEnabled,
            bellEnabled = profile.bellEnabled,
            vibrationEnabled = profile.vibrationEnabled,
            keepScreenOn = profile.keepScreenOn,
            softKeyboardEnabled = profile.softKeyboardEnabled
        )
    }
}

/**
 * DAO for profile database operations.
 */
@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>
    
    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfile(id: String): ProfileEntity?
    
    @Query("SELECT * FROM profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProfile(): ProfileEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)
    
    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)
    
    @Query("UPDATE profiles SET isDefault = 0")
    suspend fun clearDefaultProfile()
    
    @Query("UPDATE profiles SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultProfile(id: String)
    
    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun getProfileCount(): Int
}

/**
 * Room database for profiles.
 */
@Database(
    entities = [ProfileEntity::class],
    version = 1,
    exportSchema = true
)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
}

/**
 * Repository for managing profiles.
 * 
 * Provides high-level operations for profile CRUD and activation.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val settingsDataStore: SettingsDataStore
) {
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    /**
     * Get all profiles as a Flow.
     */
    fun getAllProfiles(): Flow<List<Profile>> {
        return profileDao.getAllProfiles().map { entities ->
            entities.map { it.toProfile() }
        }
    }
    
    /**
     * Get a single profile by ID.
     */
    suspend fun getProfile(id: String): Profile? {
        return profileDao.getProfile(id)?.toProfile()
    }
    
    /**
     * Get the default profile.
     */
    suspend fun getDefaultProfile(): Profile? {
        return profileDao.getDefaultProfile()?.toProfile()
    }
    
    /**
     * Create a new profile.
     * 
     * @param name Display name for the profile
     * @param description Optional description
     * @param copyFrom Optional profile to copy settings from
     * @return The created profile
     */
    suspend fun createProfile(
        name: String,
        description: String? = null,
        copyFrom: Profile? = null
    ): Profile {
        val profile = if (copyFrom != null) {
            copyFrom.copy(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description,
                isDefault = false
            )
        } else {
            Profile(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description
            )
        }
        
        profileDao.insertProfile(ProfileEntity.fromProfile(profile))
        return profile
    }
    
    /**
     * Update an existing profile.
     */
    suspend fun updateProfile(profile: Profile) {
        val entity = ProfileEntity.fromProfile(profile).copy(
            updatedAt = System.currentTimeMillis()
        )
        profileDao.insertProfile(entity)
    }
    
    /**
     * Delete a profile.
     */
    suspend fun deleteProfile(profile: Profile) {
        profileDao.deleteProfile(ProfileEntity.fromProfile(profile))
    }
    
    /**
     * Activate a profile, applying its settings to DataStore.
     */
    suspend fun activateProfile(profileId: String) {
        val profile = getProfile(profileId) ?: return
        settingsDataStore.applyProfile(profile)
    }
    
    /**
     * Set a profile as the default.
     */
    suspend fun setDefaultProfile(profileId: String) {
        profileDao.clearDefaultProfile()
        profileDao.setDefaultProfile(profileId)
    }
    
    /**
     * Export a profile to JSON string.
     */
    suspend fun exportProfile(profile: Profile): String {
        val entity = profileDao.getProfile(profile.id) ?: ProfileEntity.fromProfile(profile)
        return json.encodeToString(entity)
    }
    
    /**
     * Import a profile from JSON string.
     */
    suspend fun importProfile(jsonString: String): Profile {
        val imported = json.decodeFromString<ProfileEntity>(jsonString)
        val newProfile = imported.copy(
            id = UUID.randomUUID().toString(),
            name = "${imported.name} (Imported)",
            isDefault = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        profileDao.insertProfile(newProfile)
        return newProfile.toProfile()
    }
    
    /**
     * Create default profiles if none exist.
     */
    suspend fun ensureDefaultProfiles() {
        if (profileDao.getProfileCount() == 0) {
            // Create default profile
            createProfile("Default", "Default terminal settings")
            
            // Create a "Dev" profile example
            val devProfile = Profile(
                id = UUID.randomUUID().toString(),
                name = "Developer",
                description = "Development environment with larger font",
                fontSize = 16,
                themeName = "molten_blue",
                ligaturesEnabled = true
            )
            profileDao.insertProfile(ProfileEntity.fromProfile(devProfile))
            
            // Set first profile as default
            profileDao.getDefaultProfile() ?: run {
                profileDao.getAllProfiles().collect { profiles ->
                    profiles.firstOrNull()?.let {
                        profileDao.setDefaultProfile(it.id)
                    }
                }
            }
        }
    }
    
    private fun <T, R> Flow<T>.map(transform: (T) -> R): Flow<R> = kotlinx.coroutines.flow.map(this, transform)
}

/**
 * Simple in-memory theme repository.
 * 
 * In a full implementation, this would persist custom themes to a database.
 */
@Singleton
class ThemeRepositoryImpl @Inject constructor() : ThemeRepository {
    private val customThemes = mutableListOf<Theme>()
    
    override fun getAllThemes(): Flow<List<Theme>> = kotlinx.coroutines.flow.flow {
        emit(Theme.BUILT_IN_THEMES + customThemes)
    }
    
    override suspend fun getTheme(id: String): Theme? {
        return Theme.BUILT_IN_THEMES.find { it.id == id }
            ?: customThemes.find { it.id == id }
    }
    
    override suspend fun saveTheme(theme: Theme) {
        customThemes.removeAll { it.id == theme.id }
        customThemes.add(theme)
    }
    
    override suspend fun deleteTheme(id: String) {
        customThemes.removeAll { it.id == id }
    }
    
    override suspend fun importTheme(json: String): Theme {
        val imported = kotlinx.serialization.json.Json.decodeFromString<Theme>(json)
        val newTheme = imported.copy(
            id = UUID.randomUUID().toString(),
            name = "${imported.name} (Imported)",
            isBuiltIn = false
        )
        saveTheme(newTheme)
        return newTheme
    }
    
    override suspend fun exportTheme(theme: Theme): String {
        return kotlinx.serialization.json.Json.encodeToString(theme)
    }
}
