package com.pokemon3d.api.catalog.application

import com.pokemon3d.api.catalog.domain.ModelForm
import com.pokemon3d.api.catalog.domain.ModelMetadata
import com.pokemon3d.api.catalog.domain.ModelMetadataRepository
import com.pokemon3d.api.catalog.domain.ModelMotion

class PokemonQueryService(private val metadata: ModelMetadataRepository) {
    fun searchModels(criteria: ModelSearchCriteria): PageResult<ModelCatalogEntry> {
        val models = matchingModels(criteria)
            .sortedWith(modelComparator(criteria))
            .map(::toCatalogEntry)

        return models.paginate(criteria.page, criteria.size)
    }

    fun searchPokemon(criteria: ModelSearchCriteria): PageResult<PokemonCatalogEntry> {
        val pokemon = matchingModels(criteria)
            .groupBy { it.pokedexNumber }
            .values
            .map(::toPokemonEntry)
            .sortedWith(pokemonComparator(criteria))

        return pokemon.paginate(criteria.page, criteria.size)
    }

    fun findPokemon(pokedexNumber: Int): PokemonDetail {
        require(pokedexNumber > 0) { "pokedexNumber must be positive" }
        val models = metadata.findByPokedexNumber(pokedexNumber)
            .sortedBy { it.modelId }
        if (models.isEmpty()) throw PokemonNotFoundException(pokedexNumber)

        return PokemonDetail(toPokemonEntry(models), models.map(::toCatalogEntry))
    }

    fun findAnimations(modelId: String): ModelAnimations {
        val model = metadata.findById(modelId) ?: throw ModelNotFoundException(modelId)
        return ModelAnimations(model.modelId, model.motions)
    }

    fun filters(): ModelFilterOptions {
        val models = metadata.findAll()
        return ModelFilterOptions(
            forms = models.map { it.form }
                .distinctBy { it.code }
                .sortedBy { it.code },
            genders = models.map { it.gender }
                .distinct()
                .sorted(),
            generations = models.map { it.generation }
                .distinct()
                .sorted(),
            shinyValues = models.map { it.shiny }
                .distinct()
                .sorted(),
        )
    }

    private fun matchingModels(criteria: ModelSearchCriteria): List<ModelMetadata> {
        criteria.validate()
        val models = criteria.pokedexNumber
            ?.let(metadata::findByPokedexNumber)
            ?: metadata.findAll()

        return models.filter { it.matches(criteria) }
    }

    private fun modelComparator(criteria: ModelSearchCriteria): Comparator<ModelMetadata> {
        val primary = when (criteria.sort) {
            "modelId" -> compareBy<ModelMetadata> { it.modelId.lowercase() }
            "nameKo" -> compareBy { it.nameKo.lowercase() }
            "generation" -> compareBy { it.generation }
            else -> compareBy { it.pokedexNumber }
        }
        val ordered = if (criteria.direction == SortDirection.DESC) primary.reversed() else primary
        return ordered.thenBy { it.pokedexNumber }
            .thenBy { it.modelId }
    }

    private fun pokemonComparator(criteria: ModelSearchCriteria): Comparator<PokemonCatalogEntry> {
        val primary = when (criteria.sort) {
            "nameKo" -> compareBy<PokemonCatalogEntry> { it.nameKo.lowercase() }
            "generation" -> compareBy { it.generation }
            "modelId" -> compareBy { it.modelIds.firstOrNull().orEmpty() }
            else -> compareBy { it.pokedexNumber }
        }
        val ordered = if (criteria.direction == SortDirection.DESC) primary.reversed() else primary
        return ordered.thenBy { it.pokedexNumber }
    }

    private fun toCatalogEntry(model: ModelMetadata) = ModelCatalogEntry(
        modelId = model.modelId,
        pokedexNumber = model.pokedexNumber,
        nameKo = model.nameKo,
        nameEn = model.nameEn,
        form = model.form,
        shiny = model.shiny,
        gender = model.gender,
        generation = model.generation,
        animationCount = model.motions.size,
    )

    private fun toPokemonEntry(models: List<ModelMetadata>): PokemonCatalogEntry {
        val representative = models.minBy { it.modelId }
        return PokemonCatalogEntry(
            pokedexNumber = representative.pokedexNumber,
            nameKo = representative.nameKo,
            nameEn = representative.nameEn,
            generation = models.minOf { it.generation },
            modelCount = models.size,
            modelIds = models.map { it.modelId }.sorted(),
            forms = models.map { it.form }
                .distinctBy { it.code }
                .sortedBy { it.code },
            hasShiny = models.any { it.shiny },
        )
    }
}

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

private fun ModelMetadata.matches(criteria: ModelSearchCriteria): Boolean {
    val normalizedName = criteria.name?.trim()?.lowercase()
    val matchesName = normalizedName.isNullOrEmpty() ||
        nameKo.lowercase().contains(normalizedName) ||
        nameEn.lowercase().contains(normalizedName)
    val normalizedForm = criteria.form?.trim()?.lowercase()
    val matchesForm = normalizedForm.isNullOrEmpty() ||
        form.code.lowercase() == normalizedForm ||
        form.kind.lowercase() == normalizedForm ||
        form.label.lowercase().contains(normalizedForm)

    return matchesName &&
        matchesForm &&
        (criteria.shiny == null || shiny == criteria.shiny) &&
        (criteria.gender == null || gender.equals(criteria.gender.trim(), ignoreCase = true)) &&
        (criteria.generation == null || generation == criteria.generation)
}

private fun <T> List<T>.paginate(page: Int, size: Int): PageResult<T> {
    val start = (page.toLong() * size).coerceAtMost(this.size.toLong()).toInt()
    val end = (start.toLong() + size).coerceAtMost(this.size.toLong()).toInt()
    return PageResult(
        items = subList(start, end),
        page = page,
        size = size,
        totalElements = this.size.toLong(),
        totalPages = if (isEmpty()) 0 else (this.size + size - 1) / size,
    )
}
