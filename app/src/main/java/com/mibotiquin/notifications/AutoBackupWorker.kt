package com.mibotiquin.notifications

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mibotiquin.MiBotiquinApplication

/**
 * Backup automático semanal: exporta TODOS los botiquines a la carpeta elegida.
 * Si no hay carpeta configurada, no hace nada (Ajustes avisa de ello).
 */
class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val container = MiBotiquinApplication.container(applicationContext)
            val folderUri = container.preferences.backupFolderUri
                ?: return Result.success() // sin carpeta → nada que hacer

            val ok = container.transferManager.exportAllToFolder(
                Uri.parse(folderUri),
                "mibotiquin_auto_backup.json"
            )
            if (ok) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "auto_backup_work"
    }
}
