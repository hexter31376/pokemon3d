package com.pokemon3d.api.model3d.infrastructure.persistence

import com.pokemon3d.api.model3d.domain.*
import jakarta.persistence.EntityManager
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
@Transactional(readOnly = true)
class JpaModelMetadataRepository(private val em: EntityManager, private val jdbc: JdbcTemplate) : ModelMetadataRepository {
    override val datasetVersion: String
        get() = jdbc.query("SELECT r.dataset_version FROM model_active_release a JOIN model_release r ON r.id=a.release_id WHERE a.singleton=1",
            { rs, _ -> rs.getString(1) }).singleOrNull().orEmpty()

    override fun findVersionedById(modelId: String): VersionedModel? = jdbc.query(
        """SELECT r.dataset_version,m.metadata_json FROM model_active_release a
            JOIN model_release r ON r.id=a.release_id JOIN model_metadata m ON m.release_id=r.id
            WHERE a.singleton=1 AND m.model_id=?""",
        { rs, _ -> VersionedModel(rs.getString(1), MetadataCodec.decode(rs.getString(2))) }, modelId).singleOrNull()

    override fun findById(modelId: String) = findVersionedById(modelId)?.model
    override fun findAll(): List<ModelMetadata> = query(null)
    override fun findByPokedexNumber(pokedexNumber: Int): List<ModelMetadata> = query(pokedexNumber)

    private fun query(number: Int?): List<ModelMetadata> {
        val active = jdbc.query("SELECT release_id FROM model_active_release WHERE singleton=1", { rs, _ -> rs.getObject(1, UUID::class.java) }).singleOrNull() ?: return emptyList()
        // Releases are immutable; switching the active pointer cannot alter this result set.
        val query = em.createQuery("select m from ModelMetadataEntity m where m.releaseId=:release" +
            (if (number == null) "" else " and m.pokedexNumber=:number") + " order by m.modelId", ModelMetadataEntity::class.java)
            .setParameter("release", active)
        if (number != null) query.setParameter("number", number)
        return query.resultList.map { MetadataCodec.decode(it.metadataJson) }
    }
}
