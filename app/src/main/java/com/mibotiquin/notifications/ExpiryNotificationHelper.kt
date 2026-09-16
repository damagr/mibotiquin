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
    const val EXTRA_PRODUCT_ID = "extra_product_id"

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

        val openAppIntent = PendingIntent.getActivity(
            context,
            product.product.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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
            .addAction(0, "Mantener", openAppIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(product.product.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // Sin permiso POST_NOTIFICATIONS (API 33+): se pide al abrir Home
        }
    }
}
