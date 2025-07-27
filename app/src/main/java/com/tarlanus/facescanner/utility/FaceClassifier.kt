package com.tarlanus.facescanner.utility


import android.graphics.Bitmap
import android.graphics.RectF

/**
 * Generic interface for interacting with different recognition engines.
 */
interface FaceClassifier {

    fun register(name: String, recognition: Recognition)

    fun recognizeImage(bitmap: Bitmap, getExtra: Boolean): Recognition

    data class Recognition(
        val id: String? = null,
        var title: String? = null,
        var distance: Float? = null,
        var embedding: Array<FloatArray>? = null,
        var location: RectF? = null,
        var crop: Bitmap? = null
    ) {
        override fun toString(): String = buildString {
            id?.let { append("[$it] ") }
            title?.let { append("$it ") }
            distance?.let { append("(%.1f%%) ".format(it * 100.0f)) }
            location?.let { append("$it ") }
        }.trim()
    }
}
