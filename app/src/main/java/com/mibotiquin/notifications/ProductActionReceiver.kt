package com.mibotiquin.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mibotiquin.MiBotiquinApplication
import com.mibotiquin.domain.usecase.DeleteProductUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProductActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val productId = intent.getLongExtra(ExpiryNotificationHelper.EXTRA_PRODUCT_ID, -1L)
        if (productId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = MiBotiquinApplication.container(context)
                when (intent.action) {
                    ExpiryNotificationHelper.ACTION_DELETE_PRODUCT -> {
                        DeleteProductUseCase(container.productRepository)(productId)
                        // Limpieza del estado de aviso del producto borrado
                        container.preferences.clearNotifState(productId)
                        ExpiryNotificationHelper.cancelProduct(context, productId)
                    }
                    ExpiryNotificationHelper.ACTION_KEEP_PRODUCT -> {
                        // "Mantener" → silencia hasta el próximo empeoramiento de estado
                        val status = intent.getStringExtra("extra_status") ?: return@launch
                        container.preferences.setNotifState(
                            productId, status, System.currentTimeMillis(), silenced = true
                        )
                        ExpiryNotificationHelper.cancelProduct(context, productId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
