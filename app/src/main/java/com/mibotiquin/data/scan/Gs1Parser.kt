package com.mibotiquin.data.scan

import java.time.LocalDate
import java.time.YearMonth

/**
 * Parser GS1 para DataMatrix de envases de medicamentos (España).
 * - AI 01: GTIN (14 dígitos)
 * - AI 17: caducidad (6 dígitos AAMMDD)
 * - AI 10: lote (variable, hasta el siguiente GS o fin)
 * Separador de grupo (GS) = ASCII 29 (0x1D). También soporta formato con paréntesis.
 */
object Gs1Parser {

    private const val GS = '\u001D'

    data class Gs1Parsed(
        val gtin: String?,
        val expiryDate: LocalDate?,
        val batchNumber: String?
    )

    fun parse(raw: String): Gs1Parsed {
        // Normalizar: quitar paréntesis "(01)" → "01" (los GS reales 0x1D se respetan)
        val normalized = raw
            .replace(Regex("\\((\\d+)\\)"), "$1")
        return extractAIs(normalized)
    }

    private fun extractAIs(raw: String): Gs1Parsed {
        var gtin: String? = null
        var expiry: LocalDate? = null
        var batch: String? = null

        var i = 0
        while (i < raw.length) {
            val remaining = raw.substring(i)
            when {
                // AI 01: GTIN fijo de 14 dígitos
                remaining.startsWith("01") && remaining.drop(2).take(14).all { it.isDigit() } -> {
                    gtin = remaining.drop(2).take(14)
                    i += 2 + 14
                }
                // AI 17: caducidad AAMMDD (6 dígitos)
                remaining.startsWith("17") && remaining.drop(2).take(6).all { it.isDigit() } -> {
                    expiry = parseExpiry(remaining.drop(2).take(6))
                    i += 2 + 6
                }
                // AI 10: lote variable — hasta el siguiente GS o fin
                remaining.startsWith("10") -> {
                    val value = remaining.drop(2).takeWhile { it != GS }
                    batch = value.ifBlank { null }
                    i += 2 + value.length
                }
                else -> i += 1 // saltar carácter desconocido (GS, FNC1, etc.)
            }
            // Saltar separador de grupo si está justo tras un AI de longitud fija
            if (i < raw.length && raw[i] == GS) i += 1
        }
        return Gs1Parsed(gtin, expiry, batch)
    }

    /** AAMMDD → LocalDate. Día "00" en algunos envases = último día del mes. */
    private fun parseExpiry(aammdd: String): LocalDate? {
        return try {
            val yy = aammdd.substring(0, 2).toInt()
            val mm = aammdd.substring(2, 4).toInt()
            val dd = aammdd.substring(4, 6).toInt()
            val year = if (yy < 50) 2000 + yy else 1900 + yy
            val month = YearMonth.of(year, mm)
            val day = if (dd == 0) month.lengthOfMonth() else dd
            LocalDate.of(year, mm, day)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Extracción del CN (Código Nacional) desde códigos de barras del envase:
 * - EAN-13 español: "847000" + 6 dígitos CN + dígito control → CN = substring(6, 12)
 * - GTIN (DataMatrix AI 01): "0847000" + 6 dígitos CN + … → CN = substring(7, 13)
 * El formato impreso XXXXXX.X tiene el dígito de control tras el punto: lo ignoramos (6 dígitos).
 */
object CnExtractor {

    private const val EAN_SPAIN_PREFIX = "847000"
    private const val GTIN_SPAIN_PREFIX = "0847000"

    /** Devuelve el CN (6 dígitos) o null si no es un envase español reconocible. */
    fun fromEan13(ean13: String): String? {
        if (ean13.length != 13) return null
        if (!ean13.startsWith(EAN_SPAIN_PREFIX)) return null
        val cn = ean13.substring(6, 12)
        return cn.takeIf { it.all { ch -> ch.isDigit() } }
    }

    fun fromGtin(gtin: String): String? {
        if (gtin.length != 14) return null
        if (!gtin.startsWith(GTIN_SPAIN_PREFIX)) return null
        val cn = gtin.substring(7, 13)
        return cn.takeIf { it.all { ch -> ch.isDigit() } }
    }

    /** CN introducido a mano: acepta "123456", "123456.7", "1234567" → devuelve 6 dígitos o null. */
    fun fromManual(input: String): String? {
        val digits = input.replace(".", "").trim()
        if (digits.length < 6 || !digits.take(6).all { it.isDigit() }) return null
        return digits.take(6)
    }
}
