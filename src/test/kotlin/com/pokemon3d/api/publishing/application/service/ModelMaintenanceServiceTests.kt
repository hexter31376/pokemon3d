package com.pokemon3d.api.publishing.application.service

import com.pokemon3d.api.assets.application.port.input.PruneModelAssets
import com.pokemon3d.api.assets.domain.ModelAssetRetention
import com.pokemon3d.api.assets.domain.ModelStorageLocation
import com.pokemon3d.api.assets.domain.PrunedModelAssets
import com.pokemon3d.api.publishing.application.port.output.ModelAssetRetentionStore
import com.pokemon3d.api.publishing.application.port.output.ModelUploadQueue
import com.pokemon3d.api.publishing.application.port.output.ModelUploadSpool
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class ModelMaintenanceServiceTests {
    private val location = ModelStorageLocation("bucket", "prefix")
    private val retention = mock(ModelAssetRetentionStore::class.java)
    private val uploads = mock(ModelUploadQueue::class.java)
    private val spool = mock(ModelUploadSpool::class.java)
    private val retained = ModelAssetRetention(setOf("keep.glb"), setOf("obsolete.glb"), emptySet())

    @Test
    fun `failed reservation does not release another process maintenance lock`() {
        `when`(retention.retainCurrentAssets(location)).thenThrow(IllegalStateException("already reserved"))
        val service = service { error("Must not delete") }
        assertFailsWith<IllegalStateException> { service.prune() }
        verify(retention, never()).finishPruning()
        verifyNoInteractions(uploads, spool)
    }

    @Test
    fun `remote deletion failure releases maintenance without removing local sources`() {
        `when`(retention.retainCurrentAssets(location)).thenReturn(retained)
        val service = service { throw IllegalStateException("storage unavailable") }
        assertFailsWith<IllegalStateException> { service.prune() }
        verify(retention).finishPruning()
        verifyNoInteractions(uploads, spool)
    }

    @Test
    fun `local cleanup failure still releases maintenance`() {
        val sources = listOf(Path.of("done.glb"))
        `when`(retention.retainCurrentAssets(location)).thenReturn(retained)
        `when`(uploads.completedSourcePaths()).thenReturn(sources)
        `when`(spool.remove(sources)).thenThrow(IllegalArgumentException("invalid spool path"))
        assertFailsWith<IllegalArgumentException> { service { PrunedModelAssets(1, 20) }.prune() }
        verify(retention).finishPruning()
    }

    @Test
    fun `cleanup uses the retained snapshot and returns both deletion counts`() {
        val sources = listOf(Path.of("done.glb"))
        `when`(retention.retainCurrentAssets(location)).thenReturn(retained)
        `when`(uploads.completedSourcePaths()).thenReturn(sources)
        `when`(spool.remove(sources)).thenReturn(1)
        val result = service {
            assertEquals(retained, it)
            PrunedModelAssets(2, 50)
        }.prune()
        assertEquals(PrunedModelAssets(2, 50), result.assets)
        assertEquals(1, result.removedSources)
        verify(retention).finishPruning()
    }

    private fun service(assets: PruneModelAssets) = ModelMaintenanceService(retention, assets, uploads, spool, location)
}
