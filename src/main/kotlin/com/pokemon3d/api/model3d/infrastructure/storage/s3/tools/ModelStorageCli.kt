package com.pokemon3d.api.model3d.infrastructure.storage.s3.tools

import com.pokemon3d.api.model3d.domain.exception.ModelStorageException
import com.pokemon3d.api.model3d.infrastructure.storage.s3.*
import java.nio.file.Path
import kotlin.system.exitProcess

internal object ModelStorageCli {
    fun run(args: Array<String>, env: Map<String, String> = System.getenv()): String {
        require(args.firstOrNull() in setOf("upload", "verify")) { "Expected upload or verify" }
        val options = mutableMapOf<String, String>()
        var overwrite = false
        var index = 1
        while (index < args.size) {
            val name = args[index++]
            if (name == "--overwrite") {
                require(!overwrite) { "Duplicate --overwrite" }; overwrite = true
            } else {
                require(name in setOf("--data-root", "--manifest") && name !in options && index < args.size) { "Invalid option: $name" }
                options[name] = args[index++]
            }
        }
        require(args[0] == "upload" || !overwrite) { "--overwrite requires upload" }
        val root = Path.of(requireNotNull(options["--data-root"]) { "--data-root is required" })
        val manifest = Path.of(requireNotNull(options["--manifest"]) { "--manifest is required" })
        val properties = S3StorageProperties.fromEnvironment(env)
        val report = S3ClientFactory.create(properties).use { client ->
            ModelDatasetUploader(S3ObjectStore(client, properties.bucket), ObjectKeyResolver(properties.prefix))
                .execute(root, manifest, args[0] == "upload", overwrite)
        }
        return "${args[0]}: OK, uploaded=${report.uploaded}, skipped=${report.skipped}, verified=${report.verified}, failed=0"
    }
}

fun main(args: Array<String>) {
    try { println(ModelStorageCli.run(args)) } catch (ex: Exception) {
        val reason = (ex as? DatasetTransferException)?.cause as? ModelStorageException
        System.err.println("model storage: ${ex.message}${reason?.let { ", reason=${it.failure}" } ?: ""}")
        exitProcess(1)
    }
}
