package com.example.sarisaristore.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private data class SignatureStroke(
    val points: List<Offset>,
)

@Composable
fun SignatureCaptureDialog(
    onDismiss: () -> Unit,
    onSave: (Bitmap) -> Unit,
) {
    val strokes = remember { mutableStateListOf<SignatureStroke>() }
    val currentStrokePoints = remember { mutableStateListOf<Offset>() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val strokeWidthPx = with(LocalDensity.current) { 3.dp.toPx() }

    AppDialogSurface(onDismissRequest = onDismiss) {
        Text(
            text = "Signature",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Ask the customer to sign inside the box, then save it to the transaction.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .onSizeChanged { canvasSize = it },
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                currentStrokePoints.clear()
                                currentStrokePoints.add(down.position)

                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    val position = change.position
                                    if (currentStrokePoints.lastOrNull() != position) {
                                        currentStrokePoints.add(position)
                                    }
                                    change.consume()
                                } while (event.changes.any { it.pressed })

                                if (currentStrokePoints.isNotEmpty()) {
                                    strokes.add(SignatureStroke(currentStrokePoints.toList()))
                                    currentStrokePoints.clear()
                                }
                            }
                        },
                ) {
                    drawSignature(strokes = strokes, strokeWidthPx = strokeWidthPx)
                    if (currentStrokePoints.isNotEmpty()) {
                        drawSignature(
                            strokes = listOf(SignatureStroke(currentStrokePoints)),
                            strokeWidthPx = strokeWidthPx,
                        )
                    }
                }
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useStackedActions = maxWidth < 360.dp

            if (useStackedActions) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) {
                            SignatureActionLabel("Cancel")
                        }
                        OutlinedButton(
                            onClick = {
                                strokes.clear()
                                currentStrokePoints.clear()
                            },
                            enabled = strokes.isNotEmpty() || currentStrokePoints.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) {
                            SignatureActionLabel("Clear")
                        }
                    }
                    Button(
                        onClick = {
                            val bitmap = renderSignatureBitmap(
                                strokes = strokes,
                                canvasSize = canvasSize,
                                strokeWidthPx = strokeWidthPx,
                            )
                            onSave(bitmap)
                        },
                        enabled = strokes.isNotEmpty() && canvasSize.width > 0 && canvasSize.height > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        SignatureActionLabel("Save")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        SignatureActionLabel("Cancel")
                    }
                    OutlinedButton(
                        onClick = {
                            strokes.clear()
                            currentStrokePoints.clear()
                        },
                        enabled = strokes.isNotEmpty() || currentStrokePoints.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        SignatureActionLabel("Clear")
                    }
                    Button(
                        onClick = {
                            val bitmap = renderSignatureBitmap(
                                strokes = strokes,
                                canvasSize = canvasSize,
                                strokeWidthPx = strokeWidthPx,
                            )
                            onSave(bitmap)
                        },
                        enabled = strokes.isNotEmpty() && canvasSize.width > 0 && canvasSize.height > 0,
                        modifier = Modifier
                            .weight(1.15f)
                            .heightIn(min = 48.dp),
                    ) {
                        SignatureActionLabel("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun SignatureActionLabel(
    text: String,
) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSignature(
    strokes: List<SignatureStroke>,
    strokeWidthPx: Float,
) {
    strokes.forEach { stroke ->
        val points = stroke.points
        if (points.isEmpty()) {
            return@forEach
        }
        if (points.size == 1) {
            drawCircle(
                color = Color.Black,
                radius = strokeWidthPx / 2f,
                center = points.first(),
            )
            return@forEach
        }
        for (index in 0 until points.lastIndex) {
            drawLine(
                color = Color.Black,
                start = points[index],
                end = points[index + 1],
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun renderSignatureBitmap(
    strokes: List<SignatureStroke>,
    canvasSize: IntSize,
    strokeWidthPx: Float,
): Bitmap {
    val bitmap = Bitmap.createBitmap(
        canvasSize.width.coerceAtLeast(1),
        canvasSize.height.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(AndroidColor.WHITE)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    strokes.forEach { stroke ->
        val points = stroke.points
        if (points.isEmpty()) {
            return@forEach
        }
        if (points.size == 1) {
            canvas.drawCircle(points.first().x, points.first().y, strokeWidthPx / 2f, paint)
            return@forEach
        }
        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        }
    }

    return bitmap
}
