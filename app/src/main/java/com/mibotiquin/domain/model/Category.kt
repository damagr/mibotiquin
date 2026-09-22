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
    // java.io.Serializable: permite usarla en rememberSaveable sin crash al serializar
    data class CustomCategory(
        val id: String,
        override val displayName: String,
        override val order: Int
    ) : Category, java.io.Serializable

    companion object {
        /** Lista base: solo Medicamentos predefinida */
        val predefined = listOf(Medicamentos)
    }
}
