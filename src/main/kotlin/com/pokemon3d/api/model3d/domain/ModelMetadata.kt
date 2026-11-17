package com.pokemon3d.api.model3d.domain

data class ModelMetadata(
    val modelId: String,
    val pokedexNumber: Int,
    val nameKo: String,
    val nameEn: String,
    val form: ModelForm,
    val shiny: Boolean,
    val gender: String,
    val generation: Int,
    val asset: ModelAsset,
    val motions: List<ModelMotion>,
    val camera: ModelCamera,
)
