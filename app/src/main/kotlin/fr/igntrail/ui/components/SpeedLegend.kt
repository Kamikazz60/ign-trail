package fr.igntrail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.igntrail.core.stats.SpeedThresholds
import fr.igntrail.map.TrackLayer
import fr.igntrail.ui.format.Format
import fr.igntrail.ui.theme.LocalPanelColors

private fun hex(c: String) = Color(android.graphics.Color.parseColor(c))

/** Légende du tracé coloré d'une sortie : rouge = plus lent, jaune = sa moyenne, vert = plus rapide. */
@Composable
fun SpeedLegend(t: SpeedThresholds, modifier: Modifier = Modifier) {
    val panel = LocalPanelColors.current
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = panel.container) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Box(
                Modifier.fillMaxWidth().height(8.dp)
                    .background(Brush.horizontalGradient(listOf(hex(TrackLayer.SLOW), hex(TrackLayer.USUAL), hex(TrackLayer.FAST))), RoundedCornerShape(4.dp)),
            )
            Row(Modifier.fillMaxWidth()) {
                Text("≤ ${Format.speedKmh(t.slowMps)}", color = panel.content, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("moy. ${Format.speedKmh(t.usualMps)}", color = panel.content, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1.2f))
                Text("≥ ${Format.speedKmh(t.fastMps)} km/h", color = panel.content, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
            }
            Text("Couleurs par rapport à la moyenne de cette sortie", color = panel.secondary, fontSize = 10.sp)
        }
    }
}
