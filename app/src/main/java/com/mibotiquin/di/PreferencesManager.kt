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

    /** Uso de cámara para escanear (configurable en setup y ajustes) */
    var useCamera: Boolean
        get() = prefs.getBoolean(KEY_USE_CAMERA, true)
        set(value) {
            prefs.edit().putBoolean(KEY_USE_CAMERA, value).apply()
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

    // ---- Avisos de caducidad ----

    /** Recordatorio: cada cuántos días avisar (1..365) */
    var notifCooldownDays: Int
        get() = prefs.getInt(KEY_NOTIF_COOLDOWN_DAYS, 7).coerceIn(1, 365)
        set(value) {
            prefs.edit().putInt(KEY_NOTIF_COOLDOWN_DAYS, value.coerceIn(1, 365)).apply()
        }

    /** Hora del día de los avisos (0..23), sin minutos */
    var notifHour: Int
        get() = prefs.getInt(KEY_NOTIF_HOUR, 9).coerceIn(0, 23)
        set(value) {
            prefs.edit().putInt(KEY_NOTIF_HOUR, value.coerceIn(0, 23)).apply()
        }

    /** Estado de aviso por producto: último estado notificado + fecha + silenciado */
    data class NotifState(val status: String, val timestamp: Long, val silenced: Boolean)

    fun notifState(productId: Long): NotifState? {
        val raw = prefs.getString("$KEY_NOTIF_STATE_PREFIX$productId", null) ?: return null
        val parts = raw.split("|")
        if (parts.size != 3) return null
        return NotifState(
            status = parts[0],
            timestamp = parts[1].toLongOrNull() ?: 0L,
            silenced = parts[2] == "1"
        )
    }

    fun setNotifState(productId: Long, status: String, timestamp: Long, silenced: Boolean) {
        prefs.edit()
            .putString("$KEY_NOTIF_STATE_PREFIX$productId", "$status|$timestamp|${if (silenced) 1 else 0}")
            .apply()
    }

    fun clearNotifState(productId: Long) {
        prefs.edit().remove("$KEY_NOTIF_STATE_PREFIX$productId").apply()
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
        private const val KEY_USE_CAMERA = "use_camera"
        private const val KEY_NOTIF_COOLDOWN_DAYS = "notif_cooldown_days"
        private const val KEY_NOTIF_HOUR = "notif_hour"
        private const val KEY_NOTIF_STATE_PREFIX = "notif_state_"
    }
}