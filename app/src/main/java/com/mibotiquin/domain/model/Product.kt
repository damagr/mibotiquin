package com.mibotiquin.domain.model

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class Product(
    val id: Long,
    val barcode: String,
    val name: String,
    val category: Category,
    val quantity: Int,
    val expiryDate: Long,  // epoch millis — SIEMPRE día 1 del mes de caducidad
    val cabinetId: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    val expiryMonth: YearMonth
        get() = YearMonth.from(Instant.ofEpochMilli(expiryDate).atZone(ZoneId.systemDefault()))

    val monthsUntilExpiry: Int
        get() = java.time.temporal.ChronoUnit.MONTHS.between(
            YearMonth.now(ZoneId.systemDefault()),
            expiryMonth
        ).toInt()

    val expiryStatus: ExpiryStatus
        get() = when {
            quantity <= 0 -> ExpiryStatus.EMPTY
            monthsUntilExpiry < 0 -> ExpiryStatus.EXPIRED   // mes actual > mes caducidad
            monthsUntilExpiry <= 1 -> ExpiryStatus.CRITICAL // caduca este mes o el siguiente
            monthsUntilExpiry <= 3 -> ExpiryStatus.SOON     // ≤3 meses
            else -> ExpiryStatus.OK
        }
}

data class ProductUiModel(
    val product: Product,
    val expiryStatus: ExpiryStatus = product.expiryStatus,
    val monthsUntilExpiry: Int = product.monthsUntilExpiry
) {
    /** Formato MM/AAAA */
    val formattedExpiryMonth: String
        get() = "%02d/%04d".format(product.expiryMonth.monthValue, product.expiryMonth.year)

    val formattedMonthsUntilExpiry: String
        get() = when {
            monthsUntilExpiry < 0 -> "desde $formattedExpiryMonth"
            monthsUntilExpiry == 0 -> "este mes"
            monthsUntilExpiry == 1 -> "el próximo mes"
            else -> "en $monthsUntilExpiry meses"
        }
}

/** Resultado de guardar un producto: indica si se sumó cantidad a una entrada existente */
data class AddProductResult(
    val id: Long,
    val merged: Boolean,   // true = misma caja física (mismo código+botiquín+caducidad+familia) → cantidad sumada
    val newTotal: Int      // cantidad total de la entrada tras guardar
)
