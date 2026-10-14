package fr.igntrail.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.igntrail.core.planning.ElevationProfile
import fr.igntrail.ui.format.Format
import fr.igntrail.ui.theme.LocalPanelColors

/**
 * Profil altimétrique altitude / distance (§11). Un appui ou un glissement affiche
 * l'altitude et la distance au point touché ; [onSelect] reçoit la distance (m) ou null.
 */
@Composable
fun ElevationChart(
    profile: ElevationProfile,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF7B3FE4),
    onSelect: (Double?) -> Unit = {},
) {
    val panel = LocalPanelColors.current
    var selected by remember(profile) { mutableStateOf<Double?>(null) }
    val total = profile.distancesM.lastOrNull() ?: return
    val minE = profile.min ?: return
    val maxE = profile.max ?: return
    // Échelle verticale d'au moins 50 m : un parcours plat ne paraît pas accidenté.
    val span = maxOf(maxE - minE, 50.0)
    val bottom = minE - (span - (maxE - minE)) / 2

    fun pick(x: Float, width: Float) {
        val d = (x / width).coerceIn(0f, 1f) * total
        selected = d
        onSelect(d)
    }

    Column(modifier) {
        val info = selected?.let { d -> "${Format.distance(d)} · ${Format.meters(profile.elevationAt(d))}" }
            ?: "Min ${Format.meters(minE)} · Max ${Format.meters(maxE)}"
        Text(info, color = panel.secondary, fontSize = 12.sp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(90.dp)
                .pointerInput(profile) {
                    detectTapGestures(onTap = { pick(it.x, size.width.toFloat()) })
                }
                .pointerInput(profile) {
                    detectDragGestures(
                        onDragStart = { pick(it.x, size.width.toFloat()) },
                        onDrag = { change, _ -> pick(change.position.x, size.width.toFloat()) },
                        onDragEnd = { },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                fun x(d: Double) = (d / total * size.width).toFloat()
                fun y(e: Double) = (size.height - (e - bottom) / span * size.height).toFloat()
                val line = Path()
                profile.distancesM.forEachIndexed { i, d ->
                    val px = x(d)
                    val py = y(profile.elevationsM[i])
                    if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.05f))))
                drawPath(line, color, style = Stroke(width = 2.dp.toPx()))
                selected?.let { d ->
                    val px = x(d)
                    drawLine(panel.content, Offset(px, 0f), Offset(px, size.height), strokeWidth = 1.dp.toPx())
                    profile.elevationAt(d)?.let { e -> drawCircle(color, 4.dp.toPx(), Offset(px, y(e))) }
                }
            }
        }
    }
}
