package com.easy2642.core

import kotlinx.serialization.Serializable

@Serializable
data class SignaturePoint(val x: Float, val y: Float)

/**
 * A handwritten signature as vector strokes in the pad's own coordinates
 * (x to the right, y downwards). Only its shape matters: it is scaled to fit
 * block 12a when drawn onto the form.
 */
@Serializable
data class Signature(val strokes: List<List<SignaturePoint>> = emptyList()) {
    private val points: List<SignaturePoint> get() = strokes.flatten()

    /** A scribble needs some extent to count as a signature; a stray tap does not. */
    val isBlank: Boolean
        get() {
            val p = points
            if (p.size < MIN_POINTS) return true
            return (p.maxOf { it.x } - p.minOf { it.x }) < MIN_EXTENT && (p.maxOf { it.y } - p.minOf { it.y }) < MIN_EXTENT
        }

    data class Bounds(val left: Float, val top: Float, val width: Float, val height: Float)

    fun bounds(): Bounds {
        val p = points
        if (p.isEmpty()) return Bounds(0f, 0f, 1f, 1f)
        val left = p.minOf { it.x }
        val top = p.minOf { it.y }
        return Bounds(left, top, (p.maxOf { it.x } - left).coerceAtLeast(1f), (p.maxOf { it.y } - top).coerceAtLeast(1f))
    }

    /**
     * Strokes mapped into a box of [boxWidth] x [boxHeight] with the aspect ratio kept,
     * left-aligned and vertically centred. Output keeps y pointing down, origin at the box's top-left.
     */
    fun fitInto(boxWidth: Float, boxHeight: Float): List<List<SignaturePoint>> {
        val b = bounds()
        val scale = minOf(boxWidth / b.width, boxHeight / b.height)
        val offsetY = (boxHeight - b.height * scale) / 2f
        return strokes.filter { it.isNotEmpty() }.map { stroke ->
            stroke.map { SignaturePoint((it.x - b.left) * scale, (it.y - b.top) * scale + offsetY) }
        }
    }

    companion object {
        const val MIN_POINTS = 8
        const val MIN_EXTENT = 24f
    }
}

@Serializable
data class SavedSignature(val id: String, val signerName: String, val signature: Signature)

/** How a finished claim is written out. */
@Serializable
enum class OutputMode(val label: String) {
    COMBINED("One combined PDF"),
    SEPARATE("Separate files"),
}
