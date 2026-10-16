package fr.igntrail.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.igntrail.core.geo.GeoMath
import fr.igntrail.core.geo.ScaleBar
import fr.igntrail.ui.theme.LocalPanelColors
import fr.igntrail.ui.theme.TrailColors

private val ButtonSize = 52.dp

/** Bouton rond flottant, taille adaptée à un usage en extérieur (≥ 48 dp). */
@Composable
fun MapRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalPanelColors.current.content,
) {
    val panel = LocalPanelColors.current
    Surface(
        onClick = onClick,
        modifier = modifier.size(ButtonSize),
        shape = CircleShape,
        color = panel.container,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(26.dp))
        }
    }
}

/**
 * Boussole de la carte : la flèche rouge et le « N » montrent toujours où est le nord sur la
 * carte, qui peut être tournée à deux doigts. Un appui remet le nord en haut.
 * [bearing] : orientation de la carte en degrés (0 = nord en haut).
 */
@Composable
fun NorthIndicator(bearing: Double, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val panel = LocalPanelColors.current
    val angle = ((bearing % 360.0) + 360.0) % 360.0
    val northUp = angle < 0.5 || angle > 359.5
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(ButtonSize)
            .semantics {
                contentDescription = if (northUp) "Boussole : nord en haut" else "Boussole : remettre le nord en haut"
            },
        shape = CircleShape,
        color = panel.container,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.graphicsLayer { rotationZ = -bearing.toFloat() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Canvas(Modifier.size(width = 12.dp, height = 10.dp)) {
                val path = Path().apply {
                    moveTo(size.width / 2, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, TrailColors.NorthRed)
            }
            Text("N", color = panel.content, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
fun ZoomButtons(onZoomIn: () -> Unit, onZoomOut: () -> Unit, modifier: Modifier = Modifier) {
    val panel = LocalPanelColors.current
    Surface(
        modifier = modifier.width(ButtonSize),
        shape = RoundedCornerShape(ButtonSize / 2),
        color = panel.container,
        shadowElevation = 4.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onZoomIn, modifier = Modifier.size(ButtonSize)) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom avant", tint = panel.content)
            }
            HorizontalDivider(Modifier.width(28.dp), color = panel.divider)
            IconButton(onClick = onZoomOut, modifier = Modifier.size(ButtonSize)) {
                Icon(Icons.Filled.Remove, contentDescription = "Zoom arrière", tint = panel.content)
            }
        }
    }
}

/**
 * Échelle graphique « 0 — 250 — 500 m » comme sur la maquette. Chaque libellé est centré
 * sur sa graduation ; celui du milieu est omis si la barre est trop courte pour lui.
 */
@Composable
fun ScaleBarView(latitude: Double, zoom: Double, modifier: Modifier = Modifier, maxWidthDp: Float = 130f) {
    // MapLibre travaille en pixels logiques (dp) avec des tuiles de 512.
    val scale = ScaleBar.compute(GeoMath.metersPerPixel(latitude, zoom), maxWidthDp.toDouble()) ?: return
    val panel = LocalPanelColors.current
    val labels = listOf("0", ScaleBar.formatDistance(scale.meters / 2).substringBefore(" "), scale.label)
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = panel.container) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Layout(
                content = { labels.forEach { Text(it, color = panel.content, fontSize = 11.sp, maxLines = 1) } },
            ) { measurables, constraints ->
                val barPx = scale.widthPx.dp.roundToPx()
                val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
                val (zero, half, full) = placeables
                // Le libellé final peut déborder à droite de la barre (« 500 m »).
                val width = maxOf(barPx, barPx - full.width / 2 + full.width)
                val gap = 4.dp.roundToPx()
                val showHalf = barPx / 2 - half.width / 2 > zero.width + gap &&
                    barPx / 2 + half.width / 2 + gap < barPx - full.width / 2
                layout(width, placeables.maxOf { it.height }) {
                    zero.placeRelative(0, 0)
                    if (showHalf) half.placeRelative(barPx / 2 - half.width / 2, 0)
                    full.placeRelative((barPx - full.width / 2).coerceAtMost(width - full.width), 0)
                }
            }
            Spacer(Modifier.height(2.dp))
            Canvas(Modifier.width(scale.widthPx.dp).height(6.dp)) {
                val stroke = 2.dp.toPx()
                val y = size.height - stroke / 2
                drawLine(panel.content, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Square)
                listOf(0f, size.width / 2, size.width).forEach { x ->
                    drawLine(panel.content, Offset(x, 0f), Offset(x, size.height), stroke, StrokeCap.Square)
                }
            }
        }
    }
}

/** Petit bandeau d'information (état du GPS, position approximative…). */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier, accent: Color? = null) {
    val panel = LocalPanelColors.current
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = panel.container, shadowElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (accent != null) {
                Canvas(Modifier.size(8.dp)) { drawCircle(accent) }
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = panel.content, fontSize = 13.sp)
        }
    }
}
