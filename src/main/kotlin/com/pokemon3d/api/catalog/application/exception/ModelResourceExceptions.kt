package com.pokemon3d.api.catalog.application.exception

sealed class ModelResourceNotFoundException(message: String) : RuntimeException(message)

class ModelNotFoundException(val modelId: String) : ModelResourceNotFoundException("Model not found: $modelId")

class ModelAssetNotFoundException(val modelId: String) : ModelResourceNotFoundException("Asset not found: $modelId")

class PokemonNotFoundException(val pokedexNumber: Int) :
    ModelResourceNotFoundException("Pokemon not found: $pokedexNumber")
