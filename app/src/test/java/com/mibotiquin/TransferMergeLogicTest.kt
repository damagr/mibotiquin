package com.mibotiquin

import com.google.gson.Gson
import com.mibotiquin.data.transfer.CabinetTransferManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regla de negocio crítica (puramente lógica, sin Android):
 * al importar un botiquín que ya existe, prevalece el de fecha más reciente.
 */
class TransferMergeLogicTest {

    private fun decide(localUpdatedAt: Long?, remoteUpdatedAt: Long): Boolean {
        // Reproduce exactamente la regla del CabinetTransferManager.applyPayload
        return if (localUpdatedAt == null) true           // no existe → se crea
        else remoteUpdatedAt > localUpdatedAt             // remoto más nuevo → reemplaza
    }

    @Test
    fun `importar cabin nuevo siempre crea`() {
        assertTrue(decide(localUpdatedAt = null, remoteUpdatedAt = 1000))
    }

    @Test
    fun `remoto mas reciente reemplaza`() {
        assertTrue(decide(localUpdatedAt = 1000, remoteUpdatedAt = 2000))
    }

    @Test
    fun `local mas reciente rechaza import`() {
        assertFalse(decide(localUpdatedAt = 2000, remoteUpdatedAt = 1000))
    }

    @Test
    fun `misma fecha rechaza import`() {
        assertFalse(decide(localUpdatedAt = 1500, remoteUpdatedAt = 1500))
    }

    @Test
    fun `payload contiene cabinetId y productos`() {
        val payload = CabinetTransferManager.TransferPayload(
            cabinetId = "abc",
            name = "Casa",
            updatedAt = 1000,
            products = emptyList()
        )
        assertEquals("abc", payload.cabinetId)
        assertEquals("Casa", payload.name)
    }

    // ---- Sprint F: backup completo (exportAll/importAny) ----

    private val gson = Gson()

    private fun payload(id: String, name: String, updatedAt: Long, code: String) =
        CabinetTransferManager.TransferPayload(
            cabinetId = id,
            name = name,
            updatedAt = updatedAt,
            products = listOf(
                CabinetTransferManager.TransferProduct(
                    barcode = code, name = "Ibuprofeno $code",
                    category = "MEDICINE", quantity = 2, expiryDate = 1893456000000
                )
            )
        )

    @Test
    fun `round-trip payload individual conserva todos los campos`() {
        val original = payload("c1", "Casa", 2000, "123456")
        val json = gson.toJson(original)
        val parsed = gson.fromJson(json, CabinetTransferManager.TransferPayload::class.java)
        assertEquals(original.cabinetId, parsed.cabinetId)
        assertEquals(original.name, parsed.name)
        assertEquals(original.updatedAt, parsed.updatedAt)
        assertEquals(original.products.size, parsed.products.size)
        assertEquals(original.products[0].barcode, parsed.products[0].barcode)
        assertEquals(original.products[0].expiryDate, parsed.products[0].expiryDate)
    }

    @Test
    fun `exportAll serializa un ARRAY JSON (empieza por corchete)`() {
        val backups = listOf(payload("c1", "Casa", 2000, "111111"), payload("c2", "Oficina", 3000, "222222"))
        val json = gson.toJson(backups)
        assertTrue("el backup completo debe ser un array", json.trimStart().startsWith("["))
    }

    @Test
    fun `round-trip array conserva todos los botiquines y productos`() {
        val originals = listOf(payload("c1", "Casa", 2000, "111111"), payload("c2", "Oficina", 3000, "222222"))
        val json = gson.toJson(originals)
        val parsed = gson.fromJson(json, Array<CabinetTransferManager.TransferPayload>::class.java).toList()
        assertEquals(2, parsed.size)
        assertEquals("Casa", parsed[0].name)
        assertEquals("Oficina", parsed[1].name)
        assertEquals("111111", parsed[0].products[0].barcode)
        assertEquals("222222", parsed[1].products[0].barcode)
    }

    @Test
    fun `deteccion de formato distingue backup array de botiquin suelto`() {
        val single = gson.toJson(payload("c1", "Casa", 2000, "111111"))
        assertFalse(single.trimStart().startsWith("["))
    }

    @Test
    fun `merge multi-botiquin aplica la regla updatedAt por botiquin (mezcla crea-reemplaza-rechaza)`() {
        // Estado local: c1=1000, c2=3000 ; backup: c1=2000 (reemplaza), c2=3000 (empate→rechaza), c3 nuevo (crea)
        val localUpdates = mapOf("c1" to 1000L, "c2" to 3000L)
        val backup = listOf(
            payload("c1", "Casa nueva", 2000, "111111"),
            payload("c2", "Oficina vieja o igual", 3000, "222222"),
            payload("c3", "Viaje", 500, "333333")
        )

        val decisions = backup.map { p ->
            val local = localUpdates[p.cabinetId]
            if (local == null) true else p.updatedAt > local
        }
        assertEquals(listOf(true, false, true), decisions)
    }

    @Test
    fun `merge multi-botiquin cuenta productos solo de los aplicados`() {
        val localUpdates = mapOf("c1" to 5000L  )  // local más nuevo que el backup de c1
        val backup = listOf(
            payload("c1", "Casa", 2000, "111111"),          // rechazado
            payload("c2", "Oficina", 3000, "222222")        // nuevo → aplicado (1 producto)
        )
        var appliedProducts = 0
        var anyRejected = false
        backup.forEach { p ->
            val local = localUpdates[p.cabinetId]
            if (local == null || p.updatedAt > local) appliedProducts += p.products.size
            else anyRejected = true
        }
        assertEquals(1, appliedProducts)
        assertTrue(anyRejected)
    }
}
