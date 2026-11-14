package com.example.robottirabasura.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.robottirabasura.domain.model.Cell
import com.example.robottirabasura.domain.model.Position
import com.example.robottirabasura.domain.model.SiteMap
import com.example.robottirabasura.ui.theme.RobotThemeExtras

/**
 * Top view of the site: tables, safe point, the two QR targets, the planned route and the robot (with its heading).
 * [description] is read by TalkBack, since the drawing itself says nothing to a screen reader.
 */
@Composable
fun SiteMapCanvas(
    site: SiteMap,
    description: String,
    modifier: Modifier = Modifier,
    robot: Position? = null,
    headingDegrees: Int = 90,
    route: List<Cell> = emptyList(),
    bags: Cell? = null,
    bin: Cell? = null,
    obstacles: Set<Cell> = emptySet(),
) {
    val colors = MaterialTheme.colorScheme
    val status = RobotThemeExtras.status
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(site.width.toFloat() / site.height)
            .semantics { contentDescription = description },
    ) {
        val cell = minOf(size.width / site.width, size.height / site.height)
        fun origin(c: Cell) = Offset(c.x * cell, c.y * cell)
        fun center(c: Cell) = Offset((c.x + 0.5f) * cell, (c.y + 0.5f) * cell)

        drawRect(colors.surfaceVariant, size = Size(site.width * cell, site.height * cell))
        site.blocked.forEach { drawRect(colors.outline, origin(it), Size(cell, cell)) }
        obstacles.forEach { drawRect(status.error, origin(it), Size(cell, cell)); drawCross(it, cell, status.onError) }

        drawRoundRect(colors.tertiary, origin(site.safePoint), Size(cell, cell), CornerRadius(cell / 4))

        if (route.size > 1) {
            val path = Path().apply {
                moveTo(center(route.first()).x, center(route.first()).y)
                route.drop(1).forEach { lineTo(center(it).x, center(it).y) }
            }
            drawPath(
                path, colors.primary,
                style = Stroke(width = cell / 5, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell / 2, cell / 3))),
            )
        }
        bags?.let { drawMarker(center(it), cell, Color(0xFF8D6E63)) }
        bin?.let { drawMarker(center(it), cell, Color(0xFF455A64)) }

        robot?.let { p ->
            val c = Offset((p.xMeters / site.cellSizeMeters).toFloat() * cell, (p.yMeters / site.cellSizeMeters).toFloat() * cell)
            drawCircle(colors.primary, radius = cell * 0.45f, center = c)
            rotate(headingDegrees.toFloat(), pivot = c) {
                val tip = Path().apply {
                    moveTo(c.x, c.y - cell * 0.4f)
                    lineTo(c.x - cell * 0.2f, c.y)
                    lineTo(c.x + cell * 0.2f, c.y)
                    close()
                }
                drawPath(tip, colors.onPrimary)
            }
        }
    }
}

private fun DrawScope.drawMarker(center: Offset, cell: Float, color: Color) {
    drawRoundRect(
        color, Offset(center.x - cell * 0.4f, center.y - cell * 0.4f), Size(cell * 0.8f, cell * 0.8f), CornerRadius(cell / 6),
    )
    drawRect(Color.White, Offset(center.x - cell * 0.15f, center.y - cell * 0.15f), Size(cell * 0.3f, cell * 0.3f))
}

private fun DrawScope.drawCross(c: Cell, cell: Float, color: Color) {
    val o = Offset(c.x * cell, c.y * cell)
    drawLine(color, o + Offset(cell * 0.2f, cell * 0.2f), o + Offset(cell * 0.8f, cell * 0.8f), strokeWidth = cell / 8)
    drawLine(color, o + Offset(cell * 0.8f, cell * 0.2f), o + Offset(cell * 0.2f, cell * 0.8f), strokeWidth = cell / 8)
}
