package com.pokemon3d.api.publishing.application.port.input

fun interface ProcessModelUpload {
    fun runOnce(): Boolean
}
