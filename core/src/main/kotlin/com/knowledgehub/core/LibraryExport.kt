package com.knowledgehub.core

import android.util.JsonWriter
import java.io.Writer

internal fun writeLibraryExport(output: Writer, materials: List<Material>, notes: List<MaterialNote>) {
    val notesByMaterial = notes.groupBy { it.materialId }
    val json = JsonWriter(output)
    json.setIndent("  ")
    json.beginObject()
    json.name("format").value("knowledge-hub-library-v1")
    json.name("exportedAt").value(System.currentTimeMillis())
    json.name("materials").beginArray()
    for (material in materials) {
        json.beginObject()
        json.name("id").value(material.id)
        json.name("kind").value(material.kind)
        json.name("title").value(material.title)
        json.name("originalUrl").value(material.originalUrl)
        json.name("originalText").value(material.originalText)
        json.name("source").value(material.source)
        json.name("createdAt").value(material.createdAt)
        json.name("favorite").value(material.favorite)
        json.name("fileName").value(material.fileName)
        json.name("mimeType").value(material.mimeType)
        json.name("notes").beginArray()
        for (note in notesByMaterial[material.id].orEmpty()) {
            json.beginObject()
            json.name("id").value(note.id)
            json.name("body").value(note.body)
            json.name("revision").value(note.revision)
            json.name("createdAt").value(note.createdAt)
            json.name("updatedAt").value(note.updatedAt)
            json.endObject()
        }
        json.endArray()
        json.endObject()
    }
    json.endArray()
    json.endObject()
    json.flush()
}
