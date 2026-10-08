package com.mibotiquin

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mibotiquin.data.api.GitHubApi
import com.mibotiquin.data.local.database.AppDatabase
import com.mibotiquin.data.repository.ProductRepositoryImpl
import com.mibotiquin.data.transfer.CabinetTransferManager
import com.mibotiquin.data.update.UpdateChecker
import com.mibotiquin.di.PreferencesManager
import com.mibotiquin.domain.usecase.*
import com.mibotiquin.notifications.ExpiryCheckWorker
import com.mibotiquin.notifications.ExpiryNotificationHelper
import com.mibotiquin.presentation.ui.screen.home.HomeViewModel
import com.mibotiquin.presentation.ui.screen.scanner.ScannerViewModel
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MiBotiquinApplication : Application() {

    val diContainer by lazy { DiContainer(this) }

    override fun onCreate() {
        super.onCreate()
        ExpiryNotificationHelper.createChannel(this)
        scheduleExpiryChecks(ExistingPeriodicWorkPolicy.KEEP)
        scheduleAutoBackup()
    }

    /** Backup automático semanal (solo actúa si el usuario ha elegido carpeta) */
    private fun scheduleAutoBackup() {
        val request = PeriodicWorkRequestBuilder<com.mibotiquin.notifications.AutoBackupWorker>(
            7, TimeUnit.DAYS
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            com.mibotiquin.notifications.AutoBackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Programa la comprobación diaria de caducidad alineada a la hora elegida por el usuario
     * (sin minutos). La primera ejecución se difiere hasta la próxima `notifHour:00`.
     */
    private fun scheduleExpiryChecks(policy: ExistingPeriodicWorkPolicy) {
        val hour = diContainer.preferences.notifHour
        val now = java.util.Calendar.getInstance()
        val next = (now.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (before(now)) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val initialDelay = next.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ExpiryCheckWorker.WORK_NAME,
            policy,
            request
        )
    }

    companion object {
        fun container(context: Context): DiContainer =
            (context.applicationContext as MiBotiquinApplication).diContainer

        /** Reprograma la comprobación con la hora actual (tras cambiarla en Ajustes/Setup) */
        fun rescheduleExpiryChecks(context: Context) {
            (context.applicationContext as MiBotiquinApplication)
                .scheduleExpiryChecks(ExistingPeriodicWorkPolicy.REPLACE)
        }
    }
}

class DiContainer(context: Context) {

    private val database = AppDatabase.getInstance(context)
    val preferences = PreferencesManager(context)
    val productRepository by lazy {
        ProductRepositoryImpl(database.productDao(), database.cabinetDao(), database.customCategoryDao())
    }
    val transferManager by lazy { CabinetTransferManager(context, productRepository, preferences) }
    val localTransfer by lazy { com.mibotiquin.data.transfer.LocalTransfer(context, transferManager) }

    /** URI de un fichero entrante (VIEW/SEND json) para importar al llegar */
    val incomingImportUri = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** Abrir la pantalla de productos por caducar (desde la notificación resumen) */
    val openExpiringScreen = kotlinx.coroutines.flow.MutableStateFlow(false)

    // GitHub API + UpdateChecker
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(okhttp3.logging.HttpLoggingInterceptor().apply { level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(okHttpClient)
        .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
        .build()

    val gitHubApi by lazy { retrofit.create(com.mibotiquin.data.api.GitHubApi::class.java) }
    val updateChecker by lazy { UpdateChecker(context.cacheDir) }

    // CIMA API (AEMPS) — medicamentos españoles
    private val cimaRetrofit = Retrofit.Builder()
        .baseUrl("https://cima.aemps.es/")
        .client(okHttpClient)
        .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
        .build()

    val cimaApi by lazy { cimaRetrofit.create(com.mibotiquin.data.api.CimaApi::class.java) }

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
            HomeViewModel::class.java -> HomeViewModel(
                getCabinetsUseCase = GetCabinetsUseCase(productRepository),
                createCabinetUseCase = CreateCabinetUseCase(productRepository),
                deleteCabinetUseCase = DeleteCabinetUseCase(productRepository),
                getProductsUseCase = GetProductsUseCase(productRepository),
                searchProductsUseCase = SearchProductsUseCase(productRepository),
                updateProductQuantityUseCase = UpdateProductQuantityUseCase(productRepository),
                updateProductExpiryDateUseCase = UpdateProductExpiryDateUseCase(productRepository),
                deleteProductUseCase = DeleteProductUseCase(productRepository),
                addProductUseCase = AddProductUseCase(productRepository),
                getProductByBarcodeUseCase = GetProductByBarcodeUseCase(productRepository),
                transferManager = transferManager,
                preferences = preferences,
                updateChecker = updateChecker,
                getProspectoUrlUseCase = GetProspectoUrlUseCase(cimaApi),
                context = context,
                productRepository = productRepository
            ) as T

            ScannerViewModel::class.java -> ScannerViewModel(cimaApi, preferences) as T

            com.mibotiquin.presentation.ui.screen.settings.LocalTransferViewModel::class.java ->
                com.mibotiquin.presentation.ui.screen.settings.LocalTransferViewModel(
                    localTransfer, transferManager
                ) as T

            else -> throw IllegalArgumentException("ViewModel desconocido: ${modelClass.name}")
        }
    }
}