package com.pokemon3d.api.assets.application.port.output

interface ModelAccessPolicy {
    fun configureBucketPolicy()
    fun configureCors()
}
