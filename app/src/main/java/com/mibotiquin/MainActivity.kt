package com.mibotiquin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mibotiquin.notifications.ExpiryNotificationHelper
import com.mibotiquin.presentation.navigation.AppNavHost
import com.mibotiquin.presentation.ui.theme.MiBotiquinTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            MiBotiquinTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(viewModelFactory = MiBotiquinApplication.container(this).viewModelFactory)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // App ya abierta (singleTask): fichero recibido o notificación pulsada
        handleIntent(intent)
    }

    /**
     * Intents entrantes:
     * - Fichero para importar (Bluetooth, Quick Share, WhatsApp, Drive, gestor):
     *   ACTION_VIEW → intent.data · ACTION_SEND → EXTRA_STREAM
     * - Notificación de caducidad (resumen) → abrir pantalla de productos por caducar
     */
    private fun handleIntent(intent: Intent?) {
        val container = MiBotiquinApplication.container(this)

        // Notificación → pantalla de gestión
        if (intent?.getBooleanExtra(ExpiryNotificationHelper.EXTRA_OPEN_EXPIRING, false) == true) {
            container.openExpiringScreen.value = true
        }

        // Fichero entrante → importar
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            else -> null
        }
        if (uri != null) {
            container.incomingImportUri.value = uri.toString()
        }
    }
}
