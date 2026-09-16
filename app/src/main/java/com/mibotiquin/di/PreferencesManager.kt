package com.mibotiquin.di

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mibotiquin_prefs", Context.MODE_PRIVATE)

    var activeCabinetId: String?
        get() = prefs.getString(KEY_ACTIVE_CABINET, null)
        set(value) {
            prefs.edit().putString(KEY_ACTIVE_CABINET, value).apply()
        }

    var isFirstRun: Boolean
        get() = prefs.getBoolean(KEY_FIRST_RUN, true)
        set(value) {
            prefs.edit().putBoolean(KEY_FIRST_RUN, value).apply()
        }

    // Carpeta de backups elegida por el usuario (SAF URI persistida)
    var backupFolderUri: String?
        get() = prefs.getString(KEY_BACKUP_FOLDER_URI, null)
        set(value) {
            prefs.edit().putString(KEY_BACKUP_FOLDER_URI, value).apply()
        }

    var backupFolderDisplayName: String?
        get() = prefs.getString(KEY_BACKUP_FOLDER_NAME, null)
        set(value) {
            prefs.edit().putString(KEY_BACKUP_FOLDER_NAME, value).apply()
        }

    fun clearBackupFolder() {
        prefs.edit()
            .remove(KEY_BACKUP_FOLDER_URI)
            .remove(KEY_BACKUP_FOLDER_NAME)
            .apply()
    }

    fun grantBackupFolderPermission(context: Context) {
        backupFolderUri?.let { uriString ->
            try {
                val uri = Uri.parse(uriString)
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
                // URI inválida o permiso revocado, limpiamos
                clearBackupFolder()
            }
        }
    }

    companion object {
        private const val KEY_ACTIVE_CABINET = "active_cabinet_id"
        private const val KEY_FIRST_RUN = "is_first_run"
        private const val KEY_BACKUP_FOLDER_URI = "backup_folder_uri"
        private const val KEY_BACKUP_FOLDER_NAME = "backup_folder_display_name"
    }
}