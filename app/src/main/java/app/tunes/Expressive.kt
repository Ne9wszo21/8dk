package app.tunes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as rotateDraw
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val TAU = (2 * PI).toFloat()

private fun DrawScope.wave(x0: Float, x1: Float, cy: Float, amp: Float, wl: Float, ph: Float, c: Color, sw: Float) {
    if (x1 <= x0) return
    if (amp < 0.5f) { drawLine(c, Offset(x0, cy), Offset(x1, cy), sw, StrokeCap.Round); return }
    val p = Path()
    p.moveTo(x0, cy + amp * sin(x0 / wl * TAU - ph))
    var x = x0
    while (x < x1) { x = min(x + 3f, x1); p.lineTo(x, cy + amp * sin(x / wl * TAU - ph)) }
    drawPath(p, c, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Wavy linear progress. progress == null -> indeterminate. */
@Composable
fun WavyLine(progress: Float?, modifier: Modifier = Modifier, amp: Dp = 3.dp, wavelength: Dp = 30.dp, stroke: Dp = 4.dp) {
    val act = MaterialTheme.colorScheme.primary
    val trk = MaterialTheme.colorScheme.secondaryContainer
    val tr = rememberInfiniteTransition(label = "w")
    val ph by tr.animateFloat(0f, TAU, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "ph")
    val ind by tr.animateFloat(
        0.15f, 0.9f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ind"
    )
    Canvas(modifier.height(amp * 2 + stroke + 4.dp)) {
        val f = (progress ?: ind).coerceIn(0f, 1f)
        val sw = stroke.toPx(); val a = amp.toPx(); val wl = wavelength.toPx(); val g = 5.dp.toPx()
        val x0 = sw / 2; val x1 = size.width - sw / 2; val cy = size.height / 2
        val ae = x0 + f * (x1 - x0)
        wave(x0, ae, cy, a, wl, ph, act, sw)
        wave(ae + g + sw, x1, cy, 0f, wl, 0f, trk, sw)
        drawCircle(act, 2.dp.toPx(), Offset(x1 - sw, cy))
    }
}

/** M3E-style slider: gap around a bar handle, optionally wavy active track. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WavySlider(
    value: Float, onChange: (Float) -> Unit, onDone: () -> Unit,
    modifier: Modifier = Modifier, wavy: Boolean = true, playing: Boolean = true
) {
    val act = MaterialTheme.colorScheme.primary
    val trk = MaterialTheme.colorScheme.secondaryContainer
    val amp by animateDpAsState(if (wavy && playing) 3.dp else 0.dp, label = "amp")
    val tr = rememberInfiniteTransition(label = "sw")
    val ph by tr.animateFloat(0f, TAU, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "ph")
    Slider(
        value = value, onValueChange = onChange, onValueChangeFinished = onDone, modifier = modifier,
        thumb = { Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(act)) },
        track = {
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                val sw = 5.dp.toPx(); val a = amp.toPx(); val wl = 30.dp.toPx()
                val tw = 4.dp.toPx(); val gap = 8.dp.toPx()
                val cx = value.coerceIn(0f, 1f) * (size.width - tw) + tw / 2
                val cy = size.height / 2
                wave(sw / 2, cx - gap, cy, a, wl, ph, act, sw)
                wave(cx + gap, size.width - sw / 2, cy, 0f, wl, 0f, trk, sw)
                drawCircle(act, 2.dp.toPx(), Offset(size.width - sw, cy))
            }
        }
    )
}

/** Morphing cookie loading indicator. */
@Composable
fun LoadingBlob(modifier: Modifier = Modifier.size(48.dp), contained: Boolean = false) {
    val bg = MaterialTheme.colorScheme.primaryContainer
    val fg = MaterialTheme.colorScheme.primary
    val tr = rememberInfiniteTransition(label = "lb")
    val rot by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "r")
    val a by tr.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a"
    )
    Canvas(modifier) {
        val rad = size.minDimension / 2
        if (contained) drawCircle(bg, rad)
        val base = if (contained) rad * 0.62f else rad * 0.9f
        val p = Path()
        val n = 120
        for (i in 0..n) {
            val th = i.toFloat() / n * TAU
            val r = base * (0.82f + 0.14f * ((1 - a) * cos(6 * th) + a * cos(9 * th)))
            val x = center.x + r * cos(th)
            val y = center.y + r * sin(th)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        rotateDraw(rot, center) { drawPath(p, fg) }
    }
}

/** Connected button group: selected button goes fully round, neighbours keep tight inner corners. */
@Composable
fun ButtonGroupRow(
    options: List<String>, selected: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, icons: List<ImageVector>? = null
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            val sc by animateDpAsState(if (sel || i == 0) 24.dp else 6.dp, label = "sc")
            val ec by animateDpAsState(if (sel || i == options.lastIndex) 24.dp else 6.dp, label = "ec")
            Surface(
                onClick = { onSelect(i) },
                shape = RoundedCornerShape(topStart = sc, bottomStart = sc, topEnd = ec, bottomEnd = ec),
                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Row(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterVertically) {
                    icons?.getOrNull(i)?.let { Icon(it, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

class TbItem(val icon: ImageVector, val desc: String, val selected: Boolean, val onClick: () -> Unit)

/** Floating toolbar pill; selected item expands into a filled pill. */
@Composable
fun FloatingToolbar(items: List<TbItem>, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp, shadowElevation = 6.dp, modifier = modifier
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { it ->
                val bgc by animateColorAsState(if (it.selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "tb")
                val w by animateDpAsState(if (it.selected) 72.dp else 48.dp, label = "tw")
                Box(
                    Modifier.height(48.dp).width(w).clip(RoundedCornerShape(50)).background(bgc).clickable(onClick = it.onClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(it.icon, it.desc, tint = if (it.selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

class FabItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** FAB that opens into a stack of labelled pills; the + turns into an x. */
@Composable
fun FabMenu(items: List<FabItem>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 135f else 0f, label = "fr")
    val corner by animateDpAsState(if (open) 32.dp else 20.dp, label = "fc")
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(
            open,
            enter = fadeIn() + slideInVertically { it / 2 } + scaleIn(),
            exit = fadeOut() + slideOutVertically { it / 2 } + scaleOut()
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { f ->
                    ExtendedFloatingActionButton(
                        onClick = { open = false; f.onClick() },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) { Icon(f.icon, null); Spacer(Modifier.width(8.dp)); Text(f.label) }
                }
            }
        }
        FloatingActionButton(
            onClick = { open = !open }, shape = RoundedCornerShape(corner), modifier = Modifier.size(64.dp),
            containerColor = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (open) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
        ) { Icon(Icons.Filled.Add, null, Modifier.rotate(rot)) }
    }
}
