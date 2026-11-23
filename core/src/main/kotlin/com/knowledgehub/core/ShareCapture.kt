package com.knowledgehub.core

import android.content.Intent
import android.net.Uri
import android.os.Parcelable

object ShareCapture {
    @Suppress("DEPRECATION")
    fun fromIntent(intent: Intent): List<CaptureInput> {
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) {
            return emptyList()
        }
        val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
        val sources = linkedSetOf<Uri>()
        val texts = linkedSetOf<String>()
        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let(texts::add)
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)
                ?.filterIsInstance<CharSequence>()?.mapTo(texts) { it.toString() }
            intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
                ?.filterIsInstance<Uri>()?.let(sources::addAll)
        } else {
            (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let(sources::add)
        }
        intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                val item = clip.getItemAt(index)
                item.text?.toString()?.let(texts::add)
                item.uri?.let(sources::add)
            }
        }
        return buildList {
            texts.filter(String::isNotBlank).mapTo(this) { CaptureInput.Text(it, subject) }
            sources.mapTo(this) { uri ->
                if (LinkIdentity.parse(uri.toString()) != null) {
                    CaptureInput.Link(uri.toString(), subject)
                } else {
                    CaptureInput.File(uri, intent.type)
                }
            }
        }
    }
}
