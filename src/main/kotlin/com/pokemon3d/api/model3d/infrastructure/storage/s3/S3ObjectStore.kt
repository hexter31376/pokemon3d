package com.pokemon3d.api.model3d.infrastructure.storage.s3

import com.pokemon3d.api.model3d.domain.exception.ModelStorageException
import com.pokemon3d.api.model3d.domain.exception.StorageFailure
import com.pokemon3d.api.model3d.domain.StoredModelAsset
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.nio.file.Path
import java.io.InputStream
import java.security.MessageDigest

internal interface DatasetObjectStore {
    fun head(key: String): StoredModelAsset?
    fun put(key: String, file: Path, contentType: String, sha256: String)
    fun contentDigest(key: String): ContentDigest
}

internal data class ContentDigest(val sizeBytes: Long, val sha256: String)

internal fun digest(input: InputStream): ContentDigest {
    val hash = MessageDigest.getInstance("SHA-256")
    val bytes = ByteArray(1024 * 1024)
    var length = 0L
    while (true) {
        val n = input.read(bytes)
        if (n < 0) break
        hash.update(bytes, 0, n)
        length += n
    }
    return ContentDigest(length, java.util.HexFormat.of().formatHex(hash.digest()))
}

internal class S3ObjectStore(private val client: S3Client, private val bucket: String) : DatasetObjectStore {
    override fun head(key: String): StoredModelAsset? {
        try {
            val response = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
            return StoredModelAsset(response.contentLength(), response.contentType(), response.metadata()["sha256"])
        } catch (ex: S3Exception) {
            if (ex.statusCode() == 404 && ex.awsErrorDetails()?.errorCode() != "NoSuchBucket") {
                // HEAD may return a bodyless 404 for either a missing bucket or a missing key.
                try { client.headBucket(HeadBucketRequest.builder().bucket(bucket).build()) }
                catch (bucketError: S3Exception) {
                    if (bucketError.statusCode() == 404) throw ModelStorageException(StorageFailure.BUCKET_NOT_FOUND, key, bucketError)
                    throw translate(bucketError, key)
                } catch (bucketError: SdkException) { throw translate(bucketError, key) }
                return null
            }
            throw translate(ex, key)
        } catch (ex: SdkException) { throw translate(ex, key) }
    }

    override fun put(key: String, file: Path, contentType: String, sha256: String) {
        try {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType)
                .metadata(mapOf("sha256" to sha256)).build(), RequestBody.fromFile(file))
        } catch (ex: SdkException) { throw translate(ex, key) }
    }

    override fun contentDigest(key: String): ContentDigest {
        try {
            return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build()).use(::digest)
        } catch (ex: SdkException) { throw translate(ex, key) }
        catch (ex: java.io.IOException) { throw ModelStorageException(StorageFailure.UNAVAILABLE, key, ex) }
    }

    private fun translate(ex: SdkException, key: String): ModelStorageException {
        val reason = if (ex is S3Exception) when {
            ex.awsErrorDetails()?.errorCode() == "NoSuchBucket" -> StorageFailure.BUCKET_NOT_FOUND
            ex.statusCode() in setOf(401, 403) -> StorageFailure.ACCESS_DENIED
            ex.statusCode() == 404 -> StorageFailure.OBJECT_NOT_FOUND
            else -> StorageFailure.UNAVAILABLE
        } else StorageFailure.UNAVAILABLE
        return ModelStorageException(reason, key, ex)
    }
}
