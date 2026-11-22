package com.pokemon3d.api.assets.domain

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ModelAssetRetentionTests {
    @Test
    fun `protected paths win over obsolete and retired release membership`() {
        val path = "releases/old/model.glb"
        val retention = ModelAssetRetention(setOf(path), setOf(path), setOf("old"))
        assertFalse(retention.canDelete(path))
    }

    @Test
    fun `only obsolete GLBs and retired release GLBs are deletion candidates`() {
        val retention = ModelAssetRetention(setOf("active.glb"), setOf("obsolete.glb", "manifest.json"), setOf("old"))
        assertTrue(retention.canDelete("obsolete.glb"))
        assertTrue(retention.canDelete("releases/old/models/model.glb"))
        assertFalse(retention.canDelete("manifest.json"))
        assertFalse(retention.canDelete("releases/current/models/model.glb"))
        assertFalse(retention.canDelete("unrelated.glb"))
    }
}
