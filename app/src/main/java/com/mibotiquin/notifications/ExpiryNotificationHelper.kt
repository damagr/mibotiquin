package com.mibotiquin.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mibotiquin.MainActivity
import com.mibotiquin.domain.model.ExpiryStatus
import com.mibotiquin.domain.model.ProductUiModel

object ExpiryNotificationHelper {

    const val CHANNEL_ID = "expiry_alerts"
    const val ACTION_DELETE_PRODUCT = "com.mibotiquin.ACTION_DELETE_PRODUCT"
    const val ACTION_KEEP_PRODUCT = "com.mibotiquin.ACTION_KEEP_PRODUCT"
    const val EXTRA_PRODUCT_ID = "extra_product_id"
    const val EXTRA_OPEN_EXPIRING = "extra_open_expiring"

    /** ID fijo de la notificación resumen */
    private const val SUMMARY_NOTIFICATION_ID = 999_001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alertas de caducidad",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificaciones cuando productos estén por caducar o caducados"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Notificación individual (un solo producto afectado) */
    fun notifyExpiry(context: Context, product: ProductUiModel) {
        val status = product.expiryStatus
        if (status == ExpiryStatus.OK || status == ExpiryStatus.EMPTY) return

        val title = when (status) {
            ExpiryStatus.SOON -> "${product.product.name} caduca pronto"
            ExpiryStatus.CRITICAL -> "¡${product.product.name} caduca ${product.formattedMonthsUntilExpiry}!"
            ExpiryStatus.EXPIRED -> "${product.product.name} ha caducado"
            else -> return
        }

        val content = when (status) {
            ExpiryStatus.SOON -> "Caduca ${product.formattedExpiryMonth} (${product.formattedMonthsUntilExpiry})"
            ExpiryStatus.CRITICAL -> "Caduca ${product.formattedExpiryMonth}. ¿Mantener o eliminar?"
            ExpiryStatus.EXPIRED -> "Caducó ${product.formattedExpiryMonth}. ¿Mantener o eliminar?"
            else -> ""
        }

        // Toque en el cuerpo → abrir la pantalla de gestión
        val openAppIntent = PendingIntent.getActivity(
            context,
            product.product.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_OPEN_EXPIRING, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = PendingIntent.getBroadcast(
            context,
            product.product.id.hashCode() + 1,
            Intent(context, ProductActionReceiver::class.java).apply {
                action = ACTION_DELETE_PRODUCT
                putExtra(EXTRA_PRODUCT_ID, product.product.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Mantener" solo silencia (no abre la app)
        val keepIntent = PendingIntent.getBroadcast(
            context,
            product.product.id.hashCode() + 2,
            Intent(context, ProductActionReceiver::class.java).apply {
                action = ACTION_KEEP_PRODUCT
                putExtra(EXTRA_PRODUCT_ID, product.product.id)
                putExtra("extra_status", product.expiryStatus.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(
                if (status == ExpiryStatus.EXPIRED) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .addAction(0, "Eliminar", deleteIntent)
            .addAction(0, "Mantener", keepIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(product.product.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // Sin permiso POST_NOTIFICATIONS (API 33+): se pide al abrir Home
        }
    }

    /** Notificación resumen (varios productos afectados) → abre la pantalla de gestión */
    fun notifySummary(context: Context, count: Int) {
        val openIntent = PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_OPEN_EXPIRING, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("$count productos requieren atención")
            .setContentText("Toca para revisar los productos por caducar")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    /** Cancela la notificación individual de un producto */
    fun cancelProduct(context: Context, productId: Long) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(productId.hashCode())
        }
    }

    /** Cancela la notificación resumen */
    fun cancelSummary(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(SUMMARY_NOTIFICATION_ID)
        }
    }
}
