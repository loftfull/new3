package com.knowledgehub.core

import android.net.Uri

sealed interface CaptureInput {
    data class Text(val value: String, val subject: String? = null) : CaptureInput
    data class File(val uri: Uri, val mimeType: String? = null) : CaptureInput
    data class Link(val value: String, val subject: String? = null) : CaptureInput
}
