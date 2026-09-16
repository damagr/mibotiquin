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
        scheduleExpiryChecks()
    }

    private fun scheduleExpiryChecks() {
        val request = PeriodicWorkRequestBuilder<ExpiryCheckWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ExpiryCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        fun container(context: Context): DiContainer =
            (context.applicationContext as MiBotiquinApplication).diContainer
    }
}

class DiContainer(context: Context) {

    private val database = AppDatabase.getInstance(context)
    val preferences = PreferencesManager(context)
    val productRepository by lazy {
        ProductRepositoryImpl(database.productDao(), database.cabinetDao())
    }
    val transferManager by lazy { CabinetTransferManager(context, productRepository) }

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
                context = context
            ) as T

            ScannerViewModel::class.java -> ScannerViewModel(cimaApi, preferences) as T

            else -> throw IllegalArgumentException("ViewModel desconocido: ${modelClass.name}")
        }
    }
}