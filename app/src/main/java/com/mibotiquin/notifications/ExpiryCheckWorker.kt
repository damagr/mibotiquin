package com.mibotiquin.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mibotiquin.MiBotiquinApplication
import com.mibotiquin.domain.model.ExpiryStatus
import kotlinx.coroutines.flow.first
import java.time.YearMonth
import java.time.ZoneId
import java.util.Calendar

/**
 * Comprueba productos SOON/CRITICAL/EXPIRED y avisa con cadencia mensual.
 * - Avisa SIEMPRE al EMPEORAR de estado (OK→SOON→CRITICAL→EXPIRED)
 * - Ancla de calendario: avisa el DÍA DEL MES configurado (a la hora elegida),
 *   si el último aviso fue en un mes anterior
 * - "Mantener" silencia hasta el próximo empeoramiento
 * - 1 producto → notificación individual · ≥2 → resumen → pantalla de gestión
 */
class ExpiryCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            runCheck(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "expiry_check_work"

        /**
         * Comprobación de caducidad — la usa el worker diario Y el chequeo inmediato
         * tras importar (los productos importados pueden requerir atención al momento).
         */
        suspend fun runCheck(context: Context) {
            val container = MiBotiquinApplication.container(context)
            val repository = container.productRepository
            val prefs = container.preferences

            val products = repository.getAllProducts().first()
                .filter {
                    it.expiryStatus == ExpiryStatus.SOON ||
                    it.expiryStatus == ExpiryStatus.CRITICAL ||
                    it.expiryStatus == ExpiryStatus.EXPIRED
                }

            val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
            val dayOfMonth = prefs.notifDayOfMonth
            val nowMonth = YearMonth.now()
            val now = System.currentTimeMillis()

            val toNotify = products.filter { product ->
                val prev = prefs.notifState(product.product.id)
                when {
                    prev == null -> true                                       // primera vez
                    prev.status != product.expiryStatus.name -> true           // empeoró (o cambió)
                    prev.silenced -> false                                     // silenciado sin cambio → no
                    else -> {
                        // Ancla mensual: hoy ≥ día configurado y el último aviso fue en un mes anterior
                        val lastYM = YearMonth.from(
                            java.time.Instant.ofEpochMilli(prev.timestamp)
                                .atZone(ZoneId.systemDefault())
                        )
                        nowMonth.isAfter(lastYM) && today >= dayOfMonth
                    }
                }
            }

            when {
                toNotify.size == 1 -> ExpiryNotificationHelper.notifyExpiry(context, toNotify[0])
                toNotify.size > 1 -> ExpiryNotificationHelper.notifySummary(context, toNotify)
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
        }
    }
}
