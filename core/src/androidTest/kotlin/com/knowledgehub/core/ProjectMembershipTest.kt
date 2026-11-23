package com.knowledgehub.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectMembershipTest {
    @Test fun projectPersistsAndRemovingMembershipPreservesSourceMaterial() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "project-${UUID.randomUUID()}.db"
        try {
            val (project, material) = CaptureRepository.create(context, databaseName).use { repository ->
                val project = repository.createProject("Анализ источников")
                val material = repository.save(CaptureInput.Link("https://example.org/reference"))
                repository.setCollectionMembership(project.id, material.id, true)
                project to material
            }
            CaptureRepository.create(context, databaseName).use { repository ->
                assertEquals(CollectionKind.PROJECT, repository.collections.first().single().kind)
                assertEquals(listOf(material.id), repository.collectionMaterials(project.id).first().map { it.id })
                repository.setCollectionMembership(project.id, material.id, false)
                assertEquals(emptyList<Material>(), repository.collectionMaterials(project.id).first())
                assertEquals(material.originalUrl, repository.observe(material.id).first()?.originalUrl)
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
        Unit
    }
}
