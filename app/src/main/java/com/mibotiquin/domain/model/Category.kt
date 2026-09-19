package com.mibotiquin.domain.model

sealed interface Category {
    val displayName: String
    val order: Int

    /** Predefinida: "Medicamentos" — inmutable, siempre primera */
    object Medicamentos : Category {
        override val displayName = "Medicamentos"
        override val order = 0
    }

    /** Creada por el usuario */
    data class CustomCategory(
        val id: String,
        override val displayName: String,
        override val order: Int
    ) : Category

    companion object {
        /** Lista base: solo Medicamentos predefinida */
        val predefined = listOf(Medicamentos)
    }
}
