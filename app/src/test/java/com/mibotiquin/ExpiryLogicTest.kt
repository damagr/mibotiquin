package com.mibotiquin

import com.mibotiquin.domain.model.Category
import com.mibotiquin.domain.model.ExpiryStatus
import com.mibotiquin.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

/**
 * Lógica de caducidad por MESES (los medicamentos no caducan un día concreto):
 * - EMPTY: sin stock
 * - EXPIRED: mes actual > mes de caducidad
 * - CRITICAL: caduca este mes o el siguiente (≤1 mes)
 * - SOON: ≤3 meses
 * - OK: >3 meses
 */
class ExpiryLogicTest {

    private fun product(quantity: Int = 5, monthsFromNow: Int): Product {
        val expiryMonth = YearMonth.now(ZoneId.systemDefault()).plusMonths(monthsFromNow.toLong())
        // expiryDate = día 1 del mes de caducidad (almacenamiento)
        val expiry = expiryMonth.atDay(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Product(
            id = 1,
            barcode = "000",
            name = "Test",
            category = Category.Medicamentos,
            quantity = quantity,
            expiryDate = expiry,
            cabinetId = "test-cabinet",
            createdAt = 0,
            updatedAt = 0
        )
    }

    @Test
    fun `sin stock es EMPTY independientemente de la fecha`() {
        assertEquals(ExpiryStatus.EMPTY, product(quantity = 0, monthsFromNow = 12).expiryStatus)
        assertEquals(ExpiryStatus.EMPTY, product(quantity = 0, monthsFromNow = -2).expiryStatus)
    }

    @Test
    fun `mes pasado es EXPIRED`() {
        assertEquals(ExpiryStatus.EXPIRED, product(monthsFromNow = -1).expiryStatus)
        assertEquals(ExpiryStatus.EXPIRED, product(monthsFromNow = -6).expiryStatus)
    }

    @Test
    fun `caduca este mes o el siguiente es CRITICAL`() {
        assertEquals(ExpiryStatus.CRITICAL, product(monthsFromNow = 0).expiryStatus)
        assertEquals(ExpiryStatus.CRITICAL, product(monthsFromNow = 1).expiryStatus)
    }

    @Test
    fun `entre 2 y 3 meses es SOON`() {
        assertEquals(ExpiryStatus.SOON, product(monthsFromNow = 2).expiryStatus)
        assertEquals(ExpiryStatus.SOON, product(monthsFromNow = 3).expiryStatus)
    }

    @Test
    fun `mas de 3 meses es OK`() {
        assertEquals(ExpiryStatus.OK, product(monthsFromNow = 4).expiryStatus)
        assertEquals(ExpiryStatus.OK, product(monthsFromNow = 12).expiryStatus)
    }

    @Test
    fun `monthsUntilExpiry es correcto`() {
        assertEquals(5, product(monthsFromNow = 5).monthsUntilExpiry)
        assertEquals(-3, product(monthsFromNow = -3).monthsUntilExpiry)
        assertEquals(0, product(monthsFromNow = 0).monthsUntilExpiry)
    }

    @Test
    fun `formato MMslashAAAA es correcto`() {
        val p = product(monthsFromNow = 3)
        val ui = com.mibotiquin.domain.model.ProductUiModel(p)
        val expected = "%02d/%04d".format(p.expiryMonth.monthValue, p.expiryMonth.year)
        assertEquals(expected, ui.formattedExpiryMonth)
    }
}
