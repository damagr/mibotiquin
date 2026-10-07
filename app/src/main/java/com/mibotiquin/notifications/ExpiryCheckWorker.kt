package com.mibotiquin.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mibotiquin.MiBotiquinApplication
import com.mibotiquin.domain.model.ExpiryStatus
import kotlinx.coroutines.flow.first

/**
 * Comprueba productos SOON/CRITICAL/EXPIRED y avisa con cooldown configurable.
 * - Avisa siempre al EMPEORAR de estado (OK→SOON→CRITICAL→EXPIRED)
 * - Si sigue igual: recuerda cada `notifCooldownDays` (salvo si está silenciado)
 * - "Mantener" silencia hasta el próximo empeoramiento
 * - 1 producto afectado → notificación individual · ≥2 → resumen → pantalla de gestión
 */
class ExpiryCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val container = MiBotiquinApplication.container(applicationContext)
            val repository = container.productRepository
            val prefs = container.preferences

            val products = repository.getAllProducts().first()
                .filter {
                    it.expiryStatus == ExpiryStatus.SOON ||
                    it.expiryStatus == ExpiryStatus.CRITICAL ||
                    it.expiryStatus == ExpiryStatus.EXPIRED
                }

            val now = System.currentTimeMillis()
            val cooldownMs = prefs.notifCooldownDays.toLong() * 24L * 60L * 60L * 1000L

            val toNotify = products.filter { product ->
                val prev = prefs.notifState(product.product.id)
                when {
                    prev == null -> true                                       // primera vez
                    prev.status != product.expiryStatus.name -> true           // empeoró (o cambió)
                    prev.silenced -> false                                     // silenciado sin cambio → no
                    else -> (now - prev.timestamp) > cooldownMs                // toca recordatorio
                }
            }

            when {
                toNotify.size == 1 -> ExpiryNotificationHelper.notifyExpiry(applicationContext, toNotify[0])
                toNotify.size > 1 -> ExpiryNotificationHelper.notifySummary(applicationContext, toNotify.size)
            }

            toNotify.forEach { product ->
                val prev = prefs.notifState(product.product.id)
                val sameStatus = prev?.status == product.expiryStatus.name
                prefs.setNotifState(
                    productId = product.product.id,
                    status = product.expiryStatus.name,
                    timestamp = now,
                    // Al empeorar se resetea el silencio; si sigue igual, se conserva
                    silenced = sameStatus && (prev?.silenced ?: false)
                )
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "expiry_check_work"
    }
}
