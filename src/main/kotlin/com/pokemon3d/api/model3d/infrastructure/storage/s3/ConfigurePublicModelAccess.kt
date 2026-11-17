package com.pokemon3d.api.model3d.infrastructure.storage.s3

import kotlin.system.exitProcess

/** Explicitly configures anonymous GET access for published GLBs on the configured bucket. */
fun main(args: Array<String>) {
    try {
        require(args.isEmpty()) { "This command does not accept arguments" }
        val properties = S3StorageProperties.fromEnvironment()
        S3ClientFactory.create(properties).use { client ->
            PublicModelAccessConfigurer(client, properties).configure()
        }
        println("Public model GET and browser CORS access configured for GLB release objects.")
    } catch (ex: Exception) {
        System.err.println("Could not configure public model access: ${ex.message ?: ex.javaClass.simpleName}")
        exitProcess(1)
    }
}
