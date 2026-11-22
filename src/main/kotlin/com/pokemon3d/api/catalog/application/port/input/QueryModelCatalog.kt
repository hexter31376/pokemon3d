package com.pokemon3d.api.catalog.application.port.input

import com.pokemon3d.api.catalog.application.model.ModelAnimations
import com.pokemon3d.api.catalog.application.model.ModelCatalogEntry
import com.pokemon3d.api.catalog.application.model.ModelFilterOptions
import com.pokemon3d.api.catalog.application.model.ModelSearchCriteria
import com.pokemon3d.api.catalog.application.model.PageResult
import com.pokemon3d.api.catalog.application.model.PokemonCatalogEntry
import com.pokemon3d.api.catalog.application.model.PokemonDetail

interface QueryModelCatalog {
    fun searchModels(criteria: ModelSearchCriteria): PageResult<ModelCatalogEntry>
    fun searchPokemon(criteria: ModelSearchCriteria): PageResult<PokemonCatalogEntry>
    fun findPokemon(pokedexNumber: Int): PokemonDetail
    fun findAnimations(modelId: String): ModelAnimations
    fun filters(): ModelFilterOptions
}
