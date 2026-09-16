package com.mibotiquin

import com.mibotiquin.data.scan.CnExtractor
import com.mibotiquin.data.scan.Gs1Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScanParsingTest {

    // ---- CnExtractor ----

    @Test
    fun `ean13 español 847000 extrae CN de 6 dígitos`() {
        // 847000 + 123456 + dígito control
        assertEquals("123456", CnExtractor.fromEan13("8470001234567"))
    }

    @Test
    fun `ean13 no español devuelve null`() {
        assertNull(CnExtractor.fromEan13("7501234567893")) // prefijo México
    }

    @Test
    fun `gtin 14 dígitos extrae CN`() {
        assertEquals("123456", CnExtractor.fromGtin("08470001234560"))
    }

    @Test
    fun `manual acepta 123456`() {
        assertEquals("123456", CnExtractor.fromManual("123456"))
    }

    @Test
    fun `manual acepta formato con dígito control 123456punto7`() {
        assertEquals("123456", CnExtractor.fromManual("123456.7"))
    }

    @Test
    fun `manual rechaza menos de 6 dígitos`() {
        assertNull(CnExtractor.fromManual("12345"))
        assertNull(CnExtractor.fromManual("abc123"))
    }

    // ---- Gs1Parser ----

    @Test
    fun `parse GS1 con separadores GS extrae AI 17 y AI 10`() {
        // 01 GTIN + 17 caducidad 280430 + 10 lote (GS = 0x1D entre AI 17 y 10 en real, aquí tras 17)
        val raw = "0108470001234560\u001D17280430\u001D10AB123"
        val parsed = Gs1Parser.parse(raw)
        assertEquals("08470001234560", parsed.gtin)
        assertNotNull(parsed.expiryDate)
        assertEquals(2028, parsed.expiryDate!!.year)
        assertEquals(4, parsed.expiryDate!!.monthValue)
        assertEquals("AB123", parsed.batchNumber)
    }

    @Test
    fun `parse GS1 formato con paréntesis`() {
        val parsed = Gs1Parser.parse("(01)08470001234560(17)280430(10)AB123")
        assertEquals("08470001234560", parsed.gtin)
        assertEquals(2028, parsed.expiryDate!!.year)
        assertEquals("AB123", parsed.batchNumber)
    }

    @Test
    fun `caducidad día 00 se interpreta como último día del mes`() {
        val parsed = Gs1Parser.parse("(17)280400")
        assertEquals(30, parsed.expiryDate!!.dayOfMonth) // abril tiene 30 días
    }

    @Test
    fun `parse solo caducidad`() {
        val parsed = Gs1Parser.parse("(17)280630")
        assertNull(parsed.gtin)
        assertNull(parsed.batchNumber)
        assertEquals(2028, parsed.expiryDate!!.year)
        assertEquals(6, parsed.expiryDate!!.monthValue)
    }
}
