package com.easy2642.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.easy2642.core.Signature
import com.easy2642.core.SignaturePoint

private fun DrawScope.drawStrokes(strokes: List<List<SignaturePoint>>, color: Color, widthPx: Float, dx: Float = 0f, dy: Float = 0f) {
    for (stroke in strokes) {
        if (stroke.isEmpty()) continue
        if (stroke.size == 1) {
            drawCircle(color, widthPx / 2, Offset(stroke[0].x + dx, stroke[0].y + dy))
            continue
        }
        val path = Path().apply {
            moveTo(stroke[0].x + dx, stroke[0].y + dy)
            for (p in stroke.drop(1)) lineTo(p.x + dx, p.y + dy)
        }
        drawPath(path, color, style = Stroke(widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Finger-drawing surface. Strokes are reported in the pad's pixel coordinates;
 * only their shape is used later, so the pad's size does not matter.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SignaturePad(strokes: List<List<SignaturePoint>>, onChange: (List<List<SignaturePoint>>) -> Unit, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val current = androidx.compose.runtime.rememberUpdatedState(strokes)
    val report = androidx.compose.runtime.rememberUpdatedState(onChange)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(180.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .semantics { contentDescription = "Signature area. Draw your signature here." }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    var stroke = listOf(SignaturePoint(down.position.x, down.position.y))
                    val before = current.value
                    report.value(before + listOf(stroke))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val pts = change.historical.map { SignaturePoint(it.position.x, it.position.y) } +
                            SignaturePoint(change.position.x, change.position.y)
                        stroke = stroke + pts
                        change.consume()
                        report.value(before + listOf(stroke))
                    }
                }
            },
    ) {
        // Baseline to sign on.
        drawLine(ink.copy(alpha = 0.25f), Offset(16f, size.height * 0.78f), Offset(size.width - 16f, size.height * 0.78f), 2f)
        drawStrokes(strokes, ink, 5f)
    }
}

/** Read-only rendering of a signature, scaled to fit. */
@Composable
fun SignaturePreview(signature: Signature, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .semantics { contentDescription = "Signature preview" },
    ) {
        val pad = 8f
        drawStrokes(signature.fitInto(size.width - 2 * pad, size.height - 2 * pad), ink, 4f, pad, pad)
    }
}
