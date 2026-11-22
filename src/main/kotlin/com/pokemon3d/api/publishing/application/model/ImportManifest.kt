package com.pokemon3d.api.publishing.application.model

import com.pokemon3d.api.catalog.domain.ModelMetadata

/** Validated import data and the fingerprint of the exact input bytes. */
data class ImportManifest(val datasetVersion: String, val models: List<ModelMetadata>, val fingerprint: String)
