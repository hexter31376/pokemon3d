package com.pokemon3d.api.assets.adapter.output.s3

import com.pokemon3d.api.assets.application.port.output.ModelAccessPolicy
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CORSConfiguration
import software.amazon.awssdk.services.s3.model.CORSRule
import software.amazon.awssdk.services.s3.model.S3Exception
import tools.jackson.databind.json.JsonMapper

/** Adds the narrow anonymous read rule used by browser-facing model URLs. */
class PublicModelAccessConfigurer(
    private val client: S3Client,
    private val properties: S3StorageProperties,
    private val json: JsonMapper = JsonMapper.builder()
        .build(),
) : ModelAccessPolicy {
    private companion object {
        const val POLICY_SID = "Pokemon3dPublicModelRead"
        const val CORS_RULE_ID = "Pokemon3dPublicModelCors"
    }

    override fun configureBucketPolicy() {
        properties.validate()
        val current = try {
            client.getBucketPolicy { it.bucket(properties.bucket) }
                .policy()
        } catch (ex: S3Exception) {
            if (ex.statusCode() == 404 || ex.awsErrorDetails()?.errorCode() == "NoSuchBucketPolicy") null else throw ex
        }
        client.putBucketPolicy {
            it.bucket(properties.bucket)
                .policy(mergePolicy(current))
        }
    }

    private fun mergePolicy(current: String?): String {
        val policy = if (current == null) linkedMapOf<String, Any?>("Version" to "2012-10-17") else {
            json.readValue(current, Map::class.java)
                .entries
                .associateTo(linkedMapOf()) { it.key.toString() to it.value }
        }
        require(policy["Version"] == "2012-10-17") { "Unsupported S3 bucket policy version" }
        val statements = when (val value = policy["Statement"]) {
            null -> emptyList()
            is List<*> -> value.map { it as? Map<*, *> ?: error("Invalid statement in S3 bucket policy") }
            is Map<*, *> -> listOf(value)
            else -> error("Invalid Statement in S3 bucket policy")
        }
            .filterNot { it["Sid"] == POLICY_SID }

        val modelObjectPatterns = listOf(
            "objects/sha256/*.glb",
            "*/objects/sha256/*.glb",
            "releases/*/models/*/*.glb",
            "*/releases/*/models/*/*.glb",
        )
            .map { "arn:aws:s3:::${properties.bucket}/$it" }
        val publicModelRead = linkedMapOf(
            "Sid" to POLICY_SID,
            "Effect" to "Allow",
            "Principal" to "*",
            "Action" to "s3:GetObject",
            "Resource" to modelObjectPatterns,
        )
        policy["Statement"] = statements + publicModelRead
        return json.writeValueAsString(policy)
    }

    override fun configureCors() {
        properties.validate()
        val currentRules = try {
            client.getBucketCors { it.bucket(properties.bucket) }
                .corsRules()
        } catch (ex: S3Exception) {
            if (ex.statusCode() == 404 || ex.awsErrorDetails()?.errorCode() == "NoSuchCORSConfiguration") emptyList() else throw ex
        }
        val modelReadRule = CORSRule.builder()
            .id(CORS_RULE_ID)
            .allowedOrigins("*")
            .allowedMethods("GET", "HEAD")
            .allowedHeaders("*")
            .exposeHeaders("Content-Length", "Content-Type", "ETag", "Cache-Control")
            .maxAgeSeconds(3600)
            .build()
        val rules = currentRules.filterNot { it.id() == CORS_RULE_ID } + modelReadRule
        client.putBucketCors {
            it.bucket(properties.bucket)
                .corsConfiguration(
                    CORSConfiguration.builder()
                        .corsRules(rules)
                        .build(),
                )
        }
    }
}
