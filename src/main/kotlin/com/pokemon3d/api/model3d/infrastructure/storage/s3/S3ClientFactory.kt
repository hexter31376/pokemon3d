package com.pokemon3d.api.model3d.infrastructure.storage.s3

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.retries.StandardRetryStrategy
import java.net.URI

object S3ClientFactory {
    fun create(properties: S3StorageProperties): S3Client {
        properties.validate()
        return S3Client.builder()
            .endpointOverride(URI(properties.endpoint))
            .region(Region.of(properties.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey, properties.secretKey),
                ),
            )
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(properties.pathStyle)
                    .build(),
            )
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .httpClientBuilder(
                UrlConnectionHttpClient.builder()
                    .connectionTimeout(properties.connectTimeout)
                    .socketTimeout(properties.attemptTimeout),
            )
            .overrideConfiguration { configuration ->
                configuration
                    .apiCallAttemptTimeout(properties.attemptTimeout)
                    .apiCallTimeout(properties.requestTimeout)
                    .retryStrategy(
                        StandardRetryStrategy.builder()
                            .maxAttempts(3)
                            .build(),
                    )
            }
            .build()
    }
}
