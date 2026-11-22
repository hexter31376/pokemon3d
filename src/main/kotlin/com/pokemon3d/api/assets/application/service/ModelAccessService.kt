package com.pokemon3d.api.assets.application.service

import com.pokemon3d.api.assets.application.port.input.ConfigureModelAccess
import com.pokemon3d.api.assets.application.port.output.ModelAccessPolicy

class ModelAccessService(private val policy: ModelAccessPolicy) : ConfigureModelAccess {
    override fun configure() {
        policy.configureBucketPolicy()
        policy.configureCors()
    }
}
