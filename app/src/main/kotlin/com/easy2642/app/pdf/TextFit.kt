package com.easy2642.app.pdf

/** Chooses a font size so a value fits its form box, shrinking from the form's native 8 pt. */
object TextFit {
    const val DEFAULT_SIZE = 8f
    const val MIN_SIZE = 5f
    private const val PADDING = 4f
    private const val LINE_FACTOR = 1.2f

    data class Fit(val fontSize: Float, val fits: Boolean, val lines: Int)

    /** @param widthEm width of a string in em units (multiply by font size for points). */
    fun fit(text: String, boxWidth: Float, boxHeight: Float, multiline: Boolean, widthEm: (String) -> Float): Fit {
        val usable = (boxWidth - PADDING).coerceAtLeast(1f)
        var size = DEFAULT_SIZE
        while (true) {
            val lines = if (multiline) wrapCount(text, usable / size, widthEm) else 1
            val fits = if (multiline) {
                lines * size * LINE_FACTOR + 2f <= boxHeight
            } else {
                widthEm(text) * size <= usable
            }
            if (fits || size <= MIN_SIZE) return Fit(size, fits, lines)
            size -= 0.5f
        }
    }

    /** Greedy word wrap; counts the lines needed at a given line width in em. */
    fun wrapCount(text: String, lineWidthEm: Float, widthEm: (String) -> Float): Int {
        if (text.isEmpty()) return 1
        var lines = 0
        for (paragraph in text.split('\n')) {
            var current = 0f
            lines++
            val space = widthEm(" ")
            for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
                var w = widthEm(word)
                if (current > 0f && current + space + w > lineWidthEm) {
                    lines++
                    current = 0f
                }
                // A single word longer than the line is broken across lines.
                while (w > lineWidthEm) {
                    lines++
                    w -= lineWidthEm
                }
                current += (if (current > 0f) space else 0f) + w
            }
        }
        return lines
    }
}
