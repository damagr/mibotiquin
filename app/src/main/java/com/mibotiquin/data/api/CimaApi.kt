package com.mibotiquin.data.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API REST de CIMA (AEMPS) — gratuita, sin API key.
 * Documentación: https://www.aemps.gob.es/apps/cima/docs/CIMA_REST_API.pdf
 */
interface CimaApi {
    @GET("cima/rest/medicamento")
    suspend fun getByCn(@Query("cn") cn: String): Response<CimaMedicamento>
}

data class CimaMedicamento(
    val nregistro: String,
    val nombre: String,
    val pactivos: String?,
    val labtitular: String?,
    val cpresc: String?,
    val formaFarmaceutica: FormaFarmaceutica?,
    val presentaciones: List<Presentacion>?
)

data class FormaFarmaceutica(
    val id: Int,
    val nombre: String
)

data class Presentacion(
    val cn: String,
    val nombre: String,
    val comercio: Boolean,
    val psum: Boolean
)
