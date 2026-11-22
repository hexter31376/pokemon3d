package com.pokemon3d.api.assets.adapter.input.cli

import com.pokemon3d.api.assets.configuration.PublicModelAccessConfiguration
import kotlin.system.exitProcess

/** Explicitly configures anonymous GET access for published GLBs on the configured bucket. */
fun main(args: Array<String>) {
    try {
        require(args.isEmpty()) { "This command does not accept arguments" }
        PublicModelAccessConfiguration.withAccess { it.configure() }
        println("Public model GET and browser CORS access configured for GLB release objects.")
    } catch (ex: Exception) {
        System.err.println("Could not configure public model access: ${ex.message ?: ex.javaClass.simpleName}")
        exitProcess(1)
    }
}
