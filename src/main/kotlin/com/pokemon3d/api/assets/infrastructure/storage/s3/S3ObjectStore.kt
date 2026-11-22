package com.pokemon3d.api.assets.infrastructure.storage.s3

import com.pokemon3d.api.assets.application.ContentDigest
import com.pokemon3d.api.assets.application.digest
import com.pokemon3d.api.assets.domain.StoredModelAsset
import com.pokemon3d.api.assets.domain.exception.ModelStorageException
import com.pokemon3d.api.assets.domain.exception.StorageFailure
import java.nio.file.Path
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*


internal interface DatasetObjectStore {
    fun head(key: String): StoredModelAsset?
    fun put(key: String, file: Path, contentType: String, sha256: String)
    fun contentDigest(key: String): ContentDigest
}

internal class S3ObjectStore(private val client: S3Client, private val bucket: String) : DatasetObjectStore {
    private companion object {
        const val HTTP_NOT_FOUND = 404
        val ACCESS_DENIED_STATUSES = setOf(401, 403)
    }

    override fun head(key: String): StoredModelAsset? {
        try {
            val response = client.headObject(
                HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build(),
            )
            return StoredModelAsset(response.contentLength(), response.contentType(), response.metadata()["sha256"])
        } catch (ex: S3Exception) {
            if (!isMissingObject(ex)) throw translate(ex, key)
            confirmBucketExists(key)
            return null
        } catch (ex: SdkException) {
            throw translate(ex, key)
        }
    }

    private fun isMissingObject(exception: S3Exception): Boolean =
        exception.statusCode() == HTTP_NOT_FOUND && exception.awsErrorDetails()?.errorCode() != "NoSuchBucket"

    private fun confirmBucketExists(key: String) {
        try {
            client.headBucket(
                HeadBucketRequest.builder()
                    .bucket(bucket)
                    .build(),
            )
        } catch (exception: S3Exception) {
            if (exception.statusCode() == HTTP_NOT_FOUND) {
                throw ModelStorageException(StorageFailure.BUCKET_NOT_FOUND, key, exception)
            }
            throw translate(exception, key)
        } catch (exception: SdkException) {
            throw translate(exception, key)
        }
    }

    override fun put(key: String, file: Path, contentType: String, sha256: String) {
        try {
            client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .metadata(mapOf("sha256" to sha256))
                    .build(),
                RequestBody.fromFile(file),
            )
        } catch (ex: SdkException) { throw translate(ex, key) }
    }

    override fun contentDigest(key: String): ContentDigest {
        try {
            return client.getObject(
                GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build(),
            )
                .use(::digest)
        } catch (ex: SdkException) { throw translate(ex, key) }
        catch (ex: java.io.IOException) { throw ModelStorageException(StorageFailure.UNAVAILABLE, key, ex) }
    }

    private fun translate(ex: SdkException, key: String): ModelStorageException {
        val reason = if (ex is S3Exception) when {
            ex.awsErrorDetails()?.errorCode() == "NoSuchBucket" -> StorageFailure.BUCKET_NOT_FOUND
            ex.statusCode() in ACCESS_DENIED_STATUSES -> StorageFailure.ACCESS_DENIED
            ex.statusCode() == HTTP_NOT_FOUND -> StorageFailure.OBJECT_NOT_FOUND
            else -> StorageFailure.UNAVAILABLE
        } else StorageFailure.UNAVAILABLE
        return ModelStorageException(reason, key, ex)
    }
}
