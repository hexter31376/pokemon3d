package com.pokemon3d.api.model3d.infrastructure.persistence

import com.pokemon3d.api.model3d.domain.*
import jakarta.persistence.EntityManager
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Repository
@Transactional
class JpaModelUploadRepository(private val jdbc: JdbcTemplate, private val em: EntityManager) : ModelUploadRepository {
    override fun register(id: UUID, requestKey: String, version: String, fingerprint: String,
                          storageLocation: ModelStorageLocation, models: List<PreparedModel>): UUID {
        require(requestKey.isNotBlank() && requestKey.length <= 200 && version.isNotBlank() && version.length <= 200)
        require(storageLocation.bucket.isNotBlank()) { "Storage bucket is required" }
        require(models.isNotEmpty() && models.map { it.metadata.modelId }.distinct().size == models.size)
        // Serialize registration and activation so base_revision and idempotency are consistent.
        val revision = jdbc.queryForObject("SELECT revision FROM model_active_release WHERE singleton=1 FOR UPDATE", Long::class.java)!!
        findRequest(requestKey, fingerprint)?.let { return it }
        jdbc.update("""INSERT INTO model_release(id,request_key,dataset_version,fingerprint,expected_count,base_revision,
            storage_bucket,storage_prefix,state) VALUES (?,?,?,?,?,?,?,?, 'UPLOADING')""",
            id, requestKey, version, fingerprint, models.size, revision, storageLocation.bucket, storageLocation.prefix)
        models.forEach {
            jdbc.update("""INSERT INTO model_upload_job(id,release_id,model_id,metadata_json,source_path,state)
                VALUES (?,?,?,?,?,'PENDING')""", UUID.randomUUID(), id, it.metadata.modelId, MetadataCodec.encode(it.metadata), it.source.toString())
        }
        return id
    }

    override fun findRequest(requestKey: String, fingerprint: String): UUID? = jdbc.query(
        "SELECT id,fingerprint FROM model_release WHERE request_key=?", { rs, _ ->
            if (rs.getString(2) != fingerprint) throw UploadRejectedException("Request key already belongs to different metadata")
            rs.getObject(1, UUID::class.java)
        }, requestKey).singleOrNull()

    override fun claim(lease: Duration, maxAttempts: Int): UploadJob? {
        require(!lease.isNegative && !lease.isZero && maxAttempts > 0)
        jdbc.update("""UPDATE model_upload_job SET state='FAILED',lease_token=NULL,lease_until=NULL,last_error='LEASE_EXHAUSTED'
            WHERE state='RUNNING' AND lease_until<CURRENT_TIMESTAMP AND attempts>=?""", maxAttempts)
        jdbc.update("""UPDATE model_upload_job SET state='FAILED',last_error='RETRY_LIMIT_REACHED'
            WHERE state='PENDING' AND attempts>=?""", maxAttempts)
        jdbc.update("""UPDATE model_release SET state='FAILED' WHERE state='UPLOADING'
            AND EXISTS (SELECT 1 FROM model_upload_job j WHERE j.release_id=model_release.id AND j.state='FAILED')""")
        val job = jdbc.query("""SELECT j.*,
            (SELECT r.storage_bucket FROM model_release r WHERE r.id=j.release_id) AS storage_bucket,
            (SELECT r.storage_prefix FROM model_release r WHERE r.id=j.release_id) AS storage_prefix
            FROM model_upload_job j WHERE j.attempts<? AND
            ((j.state='PENDING' AND j.available_at<=CURRENT_TIMESTAMP) OR (j.state='RUNNING' AND j.lease_until<CURRENT_TIMESTAMP))
            ORDER BY j.available_at,j.id LIMIT 1 FOR UPDATE SKIP LOCKED""",
            { rs, _ -> UploadJob(rs.getObject("id", UUID::class.java), rs.getObject("release_id", UUID::class.java),
                UUID.randomUUID(), MetadataCodec.decode(rs.getString("metadata_json")), Path.of(rs.getString("source_path")),
                ModelStorageLocation(rs.getString("storage_bucket"), rs.getString("storage_prefix"))) }, maxAttempts).singleOrNull() ?: return null
        jdbc.update("""UPDATE model_upload_job SET state='RUNNING',attempts=attempts+1,lease_token=?,lease_until=? WHERE id=?""",
            job.token, Timestamp.from(databaseNow().plus(lease)), job.id)
        return job
    }

    override fun complete(job: UploadJob): Boolean {
        if (!owns(job)) return false
        jdbc.queryForObject("SELECT state FROM model_release WHERE id=? FOR UPDATE", String::class.java, job.releaseId)
        em.persist(ModelMetadataEntity(job.id, job.releaseId, job.model.modelId, job.model.pokedexNumber, MetadataCodec.encode(job.model)))
        em.flush()
        jdbc.update("UPDATE model_upload_job SET state='SUCCEEDED',lease_token=NULL,lease_until=NULL,last_error=NULL WHERE id=?", job.id)
        jdbc.update("""UPDATE model_release SET state='READY' WHERE id=? AND expected_count=
            (SELECT COUNT(*) FROM model_upload_job WHERE release_id=? AND state='SUCCEEDED')""", job.releaseId, job.releaseId)
        return true
    }

    override fun fail(job: UploadJob, reason: String, maxAttempts: Int, retryDelay: Duration) {
        if (!owns(job)) return
        val attempts = jdbc.queryForObject("SELECT attempts FROM model_upload_job WHERE id=?", Int::class.java, job.id)!!
        val state = if (attempts >= maxAttempts) "FAILED" else "PENDING"
        jdbc.update("""UPDATE model_upload_job SET state=?,last_error=?,lease_token=NULL,lease_until=NULL,available_at=? WHERE id=?""",
            state, reason.take(200), Timestamp.from(databaseNow().plus(retryDelay)), job.id)
        if (state == "FAILED") jdbc.update("UPDATE model_release SET state='FAILED' WHERE id=?", job.releaseId)
    }

    private fun owns(job: UploadJob): Boolean = jdbc.query(
        "SELECT lease_token,state,lease_until FROM model_upload_job WHERE id=? FOR UPDATE", { rs, _ ->
            rs.getObject(1, UUID::class.java) == job.token && rs.getString(2) == "RUNNING" && rs.getTimestamp(3).toInstant().isAfter(databaseNow())
        }, job.id).singleOrNull() == true

    private fun databaseNow(): Instant = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", java.time.OffsetDateTime::class.java)!!.toInstant()

    override fun status(releaseId: UUID): ReleaseStatus = jdbc.query("""SELECT r.*,
        (SELECT COUNT(*) FROM model_upload_job j WHERE j.release_id=r.id AND j.state='SUCCEEDED') AS succeeded,
        (SELECT COUNT(*) FROM model_upload_job j WHERE j.release_id=r.id AND j.state='FAILED') AS failed,
        (SELECT release_id FROM model_active_release WHERE singleton=1) AS active_id
        FROM model_release r WHERE r.id=?""", { rs, _ ->
        ReleaseStatus(releaseId, rs.getString("dataset_version"), rs.getString("state"), rs.getInt("expected_count"),
            rs.getInt("succeeded"), rs.getInt("failed"), rs.getObject("active_id", UUID::class.java) == releaseId)
    }, releaseId).singleOrNull()?.let { status ->
        status.copy(errors = jdbc.query("SELECT DISTINCT last_error FROM model_upload_job WHERE release_id=? AND last_error IS NOT NULL",
            { rs, _ -> rs.getString(1) }, releaseId))
    } ?: throw IllegalArgumentException("Unknown release: $releaseId")

    override fun activate(releaseId: UUID) {
        val active = jdbc.query("SELECT release_id,revision FROM model_active_release WHERE singleton=1 FOR UPDATE",
            { rs, _ -> rs.getObject(1, UUID::class.java) to rs.getLong(2) }).single()
        if (active.first == releaseId) return
        val release = jdbc.query("SELECT state,base_revision,expected_count FROM model_release WHERE id=? FOR UPDATE",
            { rs, _ -> Triple(rs.getString(1), rs.getLong(2), rs.getInt(3)) }, releaseId).singleOrNull()
            ?: throw IllegalArgumentException("Unknown release: $releaseId")
        if (release.first != "READY") throw UploadRejectedException("Release is not READY")
        if (release.second != active.second) throw UploadRejectedException("Active release changed since submission; import a new release")
        check(jdbc.queryForObject("SELECT COUNT(*) FROM model_metadata WHERE release_id=?", Int::class.java, releaseId) == release.third)
        jdbc.update("UPDATE model_active_release SET release_id=?,revision=revision+1 WHERE singleton=1", releaseId)
    }

    override fun retry(releaseId: UUID) {
        check(!status(releaseId).active) { "Cannot modify the active release" }
        jdbc.update("""UPDATE model_upload_job SET state='PENDING',attempts=0,last_error=NULL,available_at=CURRENT_TIMESTAMP
            WHERE release_id=? AND state='FAILED'""", releaseId)
        jdbc.update("UPDATE model_release SET state='UPLOADING' WHERE id=? AND state='FAILED'", releaseId)
    }
}
