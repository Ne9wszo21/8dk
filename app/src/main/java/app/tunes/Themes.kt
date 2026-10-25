package app.tunes

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import java.io.File

object Prefs {
    private lateinit var sp: android.content.SharedPreferences
    var theme by mutableStateOf("dynamic")
        private set
    var mode by mutableIntStateOf(0)
        private set
    var font by mutableStateOf("sys")
        private set
    var customFont by mutableStateOf<String?>(null)
        private set

    fun init(c: Context) {
        sp = c.getSharedPreferences("s", 0)
        theme = sp.getString("theme", "dynamic") ?: "dynamic"
        mode = sp.getInt("mode", 0)
        font = sp.getString("font", "sys") ?: "sys"
        customFont = sp.getString("cfont", null)
    }
    fun applyTheme(v: String) { theme = v; sp.edit().putString("theme", v).apply() }
    fun applyMode(v: Int) { mode = v; sp.edit().putInt("mode", v).apply() }
    fun applyFont(v: String) { font = v; sp.edit().putString("font", v).apply() }
    fun applyCustom(v: String?) { customFont = v; sp.edit().putString("cfont", v).apply() }
}

private fun c(h: Long) = Color(0xFF000000 or h)

class Pal(
    val id: String, val name: String, val dark: Boolean,
    val bg: Color, val sf: Color, val fg: Color,
    val p: Color, val s: Color, val t: Color, val e: Color
)

val palettes = listOf(
    Pal("ef-dark", "Everforest", true, c(0x2D353B), c(0x343F44), c(0xD3C6AA), c(0xA7C080), c(0x83C092), c(0xDBBC7F), c(0xE67E80)),
    Pal("ef-light", "Everforest Light", false, c(0xFDF6E3), c(0xF4F0D9), c(0x5C6A72), c(0x8DA101), c(0x35A77C), c(0xDFA000), c(0xF85552)),
    Pal("mocha", "Catppuccin Mocha", true, c(0x1E1E2E), c(0x313244), c(0xCDD6F4), c(0xCBA6F7), c(0x89B4FA), c(0xA6E3A1), c(0xF38BA8)),
    Pal("latte", "Catppuccin Latte", false, c(0xEFF1F5), c(0xCCD0DA), c(0x4C4F69), c(0x8839EF), c(0x1E66F5), c(0x40A02B), c(0xD20F39)),
    Pal("gruvbox", "Gruvbox", true, c(0x282828), c(0x3C3836), c(0xEBDBB2), c(0xFABD2F), c(0xB8BB26), c(0x8EC07C), c(0xFB4934)),
    Pal("nord", "Nord", true, c(0x2E3440), c(0x3B4252), c(0xECEFF4), c(0x88C0D0), c(0x81A1C1), c(0xA3BE8C), c(0xBF616A)),
    Pal("rosepine", "Rosé Pine", true, c(0x191724), c(0x1F1D2E), c(0xE0DEF4), c(0xC4A7E7), c(0x9CCFD8), c(0xF6C177), c(0xEB6F92)),
    Pal("tokyo", "Tokyo Night", true, c(0x1A1B26), c(0x24283B), c(0xC0CAF5), c(0x7AA2F7), c(0xBB9AF7), c(0x9ECE6A), c(0xF7768E)),
    Pal("amoled", "AMOLED", true, c(0x000000), c(0x121212), c(0xE6E6E6), c(0x8AB4F8), c(0x81C995), c(0xFDD663), c(0xF28B82))
)

fun Pal.scheme(): ColorScheme {
    fun m(a: Color, b: Color, f: Float) = lerp(a, b, f)
    fun on(x: Color) = if (x.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p, onPrimary = on(p), primaryContainer = m(sf, p, 0.35f), onPrimaryContainer = fg,
        secondary = s, onSecondary = on(s), secondaryContainer = m(sf, s, 0.35f), onSecondaryContainer = fg,
        tertiary = t, onTertiary = on(t), tertiaryContainer = m(sf, t, 0.35f), onTertiaryContainer = fg,
        error = e, onError = on(e), errorContainer = m(sf, e, 0.35f), onErrorContainer = fg,
        background = bg, onBackground = fg, surface = bg, onSurface = fg,
        surfaceVariant = sf, onSurfaceVariant = m(fg, bg, 0.25f),
        outline = m(bg, fg, 0.5f), outlineVariant = m(bg, fg, 0.2f), surfaceTint = p,
        inverseSurface = fg, inverseOnSurface = bg, inversePrimary = m(p, bg, 0.4f),
        surfaceDim = bg, surfaceBright = m(sf, fg, 0.08f),
        surfaceContainerLowest = m(bg, Color.Black, 0.2f), surfaceContainerLow = m(bg, sf, 0.5f),
        surfaceContainer = sf, surfaceContainerHigh = m(sf, fg, 0.06f), surfaceContainerHighest = m(sf, fg, 0.12f)
    )
}

val fontOptions = listOf(
    "sys" to "System default", "gs" to "Google Sans",
    "gsf" to "Google Sans Flex", "gsc" to "Google Sans Code"
)

fun hasFont(ctx: Context, id: String) =
    (ctx.assets.list("fonts") ?: emptyArray()).any { it.startsWith("$id-") }

fun fontFamilyFor(ctx: Context, id: String, custom: String?): FontFamily {
    if (id == "custom" && custom != null) {
        val f = File(ctx.filesDir, custom)
        if (f.exists()) return runCatching { FontFamily(Font(f)) }.getOrDefault(FontFamily.Default)
        return FontFamily.Default
    }
    if (id == "sys" || id == "custom") return FontFamily.Default
    val have = (ctx.assets.list("fonts") ?: emptyArray()).toSet()
    val fs = listOf(400 to FontWeight.Normal, 500 to FontWeight.Medium, 700 to FontWeight.Bold).mapNotNull { (w, fw) ->
        "$id-$w.ttf".takeIf { it in have }?.let { Font("fonts/$it", ctx.assets, fw) }
    }
    return if (fs.isEmpty()) FontFamily.Default else FontFamily(fs)
}

fun Typography.withFont(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f)
)

@Composable
fun Theme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val sys = isSystemInDarkTheme()
    val pal = palettes.firstOrNull { it.id == Prefs.theme }
    val dark = when (Prefs.mode) { 1 -> false; 2 -> true; else -> sys }
    val cs = pal?.scheme() ?: if (Build.VERSION.SDK_INT >= 31)
        (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx))
    else (if (dark) darkColorScheme() else lightColorScheme())
    val isDark = pal?.dark ?: dark
    val ff = remember(Prefs.font, Prefs.customFont) { fontFamilyFor(ctx, Prefs.font, Prefs.customFont) }
    val ty = remember(ff) { Typography().withFont(ff) }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val w = (view.context as Activity).window
        val ic = WindowCompat.getInsetsController(w, view)
        ic.isAppearanceLightStatusBars = !isDark
        ic.isAppearanceLightNavigationBars = !isDark
    }
    MaterialTheme(
        colorScheme = cs, typography = ty,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp)
        ),
        content = content
    )
}

fun importFont(ctx: Context, uri: Uri) {
    val name = "font-${System.currentTimeMillis()}.ttf"
    val out = File(ctx.filesDir, name)
    val ok = runCatching {
        ctx.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { i.copyTo(it) } }
        val h = out.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
        val tag = String(h, Charsets.ISO_8859_1)
        h.contentEquals(byteArrayOf(0, 1, 0, 0)) || tag == "OTTO" || tag == "true" || tag == "ttcf"
    }.getOrDefault(false)
    if (!ok) {
        out.delete()
        Toast.makeText(ctx, "Not a valid .ttf/.otf file", Toast.LENGTH_SHORT).show()
        return
    }
    Prefs.customFont?.let { File(ctx.filesDir, it).delete() }
    Prefs.applyCustom(name)
    Prefs.applyFont("custom")
}

class Opt(val id: String, val name: String, val bg: Color, val fg: Color, val cols: List<Color>)

@Composable
fun ThemeCard(o: Opt, m: Modifier) {
    val sel = Prefs.theme == o.id
    Surface(
        onClick = { Prefs.applyTheme(o.id) }, modifier = m, shape = RoundedCornerShape(24.dp), color = o.bg,
        border = BorderStroke(
            if (sel) 3.dp else 1.dp,
            if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                o.cols.forEach { Box(Modifier.size(22.dp).clip(CircleShape).background(it)) }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.name, Modifier.weight(1f), color = o.fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                if (sel) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = o.fg)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val sysDark = isSystemInDarkTheme()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        u?.let { importFont(ctx, it) }
    }
    val opts = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            val d = if (sysDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            add(Opt("dynamic", "Material You", d.surface, d.onSurface, listOf(d.primary, d.secondary, d.tertiary)))
        }
        palettes.forEach { add(Opt(it.id, it.name, it.bg, it.fg, listOf(it.p, it.s, it.t))) }
    }
    val fonts = fontOptions.filter { it.first == "sys" || hasFont(ctx, it.first) } +
        (if (Prefs.customFont != null) listOf("custom" to "Imported font") else emptyList())

    LazyColumn(
        Modifier.statusBarsPadding(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { Text("Theme", style = MaterialTheme.typography.titleLarge) }
        opts.chunked(2).forEach { row ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { ThemeCard(it, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (Prefs.theme == "dynamic") item {
            ButtonGroupRow(listOf("System", "Light", "Dark"), Prefs.mode, { Prefs.applyMode(it) }, Modifier.fillMaxWidth())
        }
        item { Text("Font", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge) }
        fonts.forEach { (id, name) ->
            item {
                val sel = Prefs.font == id
                val ff = remember(id, Prefs.customFont) { fontFamilyFor(ctx, id, Prefs.customFont) }
                Surface(
                    onClick = { Prefs.applyFont(id) }, shape = RoundedCornerShape(20.dp),
                    color = if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.labelLarge)
                            Text("Aa The quick brown fox", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = ff))
                        }
                        RadioButton(sel, null)
                    }
                }
            }
        }
        if (!hasFont(ctx, "gs") && !hasFont(ctx, "gsf")) item {
            Text(
                "Google Sans files weren't downloaded at build time. Re-run the workflow, or import a font file below.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            FilledTonalButton({ picker.launch(arrayOf("*/*")) }) { Text("Import font file (.ttf / .otf)") }
        }
    }
}
