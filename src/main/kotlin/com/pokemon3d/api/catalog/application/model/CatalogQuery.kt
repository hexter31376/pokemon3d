package com.pokemon3d.api.catalog.application.model

import com.pokemon3d.api.catalog.domain.ModelForm
import com.pokemon3d.api.catalog.domain.ModelMotion

data class ModelSearchCriteria(
    val pokedexNumber: Int? = null,
    val name: String? = null,
    val form: String? = null,
    val shiny: Boolean? = null,
    val gender: String? = null,
    val generation: Int? = null,
    val page: Int = 0,
    val size: Int = DEFAULT_PAGE_SIZE,
    val sort: String = "pokedexNumber",
    val direction: SortDirection = SortDirection.ASC,
) {
    internal fun validate() {
        require(pokedexNumber == null || pokedexNumber > 0) { "pokedexNumber must be positive" }
        require(generation == null || generation > 0) { "generation must be positive" }
        require(page >= 0) { "page must not be negative" }
        require(size in 1..MAX_PAGE_SIZE) { "size must be between 1 and $MAX_PAGE_SIZE" }
        require(sort in SUPPORTED_SORT_FIELDS) { "unsupported sort field: $sort" }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
        val SUPPORTED_SORT_FIELDS = setOf("pokedexNumber", "modelId", "nameKo", "generation")
    }
}

enum class SortDirection {
    ASC,
    DESC,
}

data class PageResult<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class ModelCatalogEntry(
    val modelId: String,
    val pokedexNumber: Int,
    val nameKo: String,
    val nameEn: String,
    val form: ModelForm,
    val shiny: Boolean,
    val gender: String,
    val generation: Int,
    val animationCount: Int,
)

data class PokemonCatalogEntry(
    val pokedexNumber: Int,
    val nameKo: String,
    val nameEn: String,
    val generation: Int,
    val modelCount: Int,
    val modelIds: List<String>,
    val forms: List<ModelForm>,
    val hasShiny: Boolean,
)

data class PokemonDetail(val pokemon: PokemonCatalogEntry, val models: List<ModelCatalogEntry>)

data class ModelAnimations(val modelId: String, val animations: List<ModelMotion>)

data class ModelFilterOptions(
    val forms: List<ModelForm>,
    val genders: List<String>,
    val generations: List<Int>,
    val shinyValues: List<Boolean>,
)
