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
import com.mibotiquin.presentation.navigation.AppNavHost
import com.mibotiquin.presentation.ui.theme.MiBotiquinTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleImportIntent(intent)
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
        // App ya abierta (singleTask): fichero recibido por share/open → importar
        handleImportIntent(intent)
    }

    /**
     * Fichero entrante vía intent (Bluetooth, Quick Share, WhatsApp, Drive, gestor):
     * - ACTION_VIEW → intent.data
     * - ACTION_SEND → EXTRA_STREAM
     * La URI se entrega al contenedor y AppNavHost dispara la importación
     * (importAny valida el formato; un JSON ajeno → "Formato no válido")
     */
    private fun handleImportIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            else -> null
        }
        if (uri != null) {
            MiBotiquinApplication.container(this).incomingImportUri.value = uri.toString()
        }
    }
}
