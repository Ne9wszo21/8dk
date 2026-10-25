package app.tunes

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        enableEdgeToEdge()
        Prefs.init(this)
        setContent { Theme { Root(viewModel()) } }
    }
}

val moods = listOf(
    "Energize" to "high energy songs", "Relax" to "relaxing chill songs",
    "Focus" to "focus lofi study music", "Party" to "party hits songs",
    "Feel good" to "feel good songs", "Workout" to "gym workout songs",
    "Romance" to "romantic love songs", "Sad" to "sad emotional songs"
)

object Mood { var cur by mutableStateOf<String?>(null) }

fun pickMood(vm: Vm, n: String, q: String) {
    if (Mood.cur == n) { Mood.cur = null; return }
    Mood.cur = n; vm.q = q; vm.search()
}

fun fmt(ms: Long) = String.format("%d:%02d", ms / 60000, ms / 1000 % 60)

@Composable
fun Art(url: String?, size: Dp, r: Dp = 14.dp) = AsyncImage(
    model = url, contentDescription = null, contentScale = ContentScale.Crop,
    modifier = Modifier.size(size).clip(RoundedCornerShape(r))
)

@Composable
fun rememberArtColor(url: String?, fallback: Color): Color {
    val ctx = LocalContext.current
    var c by remember(url) { mutableStateOf(fallback) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        runCatching {
            val r = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).size(128).build())
            val bmp = (r as? SuccessResult)?.drawable?.toBitmap() ?: return@runCatching
            val argb = withContext(Dispatchers.Default) {
                val p = Palette.from(bmp).generate()
                p.getVibrantColor(p.getDominantColor(fallback.toArgb()))
            }
            c = Color(argb)
        }
    }
    return animateColorAsState(c, label = "art").value
}

@Composable
fun Root(vm: Vm) {
    var full by remember { mutableStateOf(false) }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        while (true) { vm.tick(); delay(500) }
    }
    BackHandler(full) { full = false }
    val nav = listOf(
        Triple("Home", Icons.Filled.Home, 0), Triple("Search", Icons.Filled.Search, 1),
        Triple("Library", Icons.Filled.LibraryMusic, 2), Triple("Settings", Icons.Filled.Settings, 3)
    )
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    vm.err?.let {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().clickable { vm.err = null }) {
                            Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall, maxLines = 3)
                        }
                    }
                    if (vm.cur != null) Mini(vm) { full = true }
                    if (!wide) NavigationBar {
                        nav.forEach { (n, ic, i) ->
                            NavigationBarItem(vm.tab == i, { vm.tab = i }, { Icon(ic, n) }, label = { Text(n) })
                        }
                    }
                }
            }
        ) { pad ->
            Row(Modifier.padding(pad).fillMaxSize()) {
                if (wide) NavigationRail(
                    header = {
                        ExtendedFloatingActionButton(onClick = { vm.tab = 1 }) {
                            Icon(Icons.Filled.Search, null); Spacer(Modifier.width(8.dp)); Text("Search")
                        }
                    }
                ) {
                    Spacer(Modifier.height(16.dp))
                    nav.forEach { (n, ic, i) ->
                        NavigationRailItem(vm.tab == i, { vm.tab = i }, { Icon(ic, n) }, label = { Text(n) }, alwaysShowLabel = true)
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (vm.tab) { 0 -> Home(vm); 1 -> SearchTab(vm); 2 -> Library(vm); else -> SettingsScreen() }
                    if (vm.tab == 0 || vm.tab == 2) FabMenu(
                        listOf(
                            FabItem("Shuffle liked", Icons.Filled.Shuffle) { if (vm.liked.isNotEmpty()) vm.play(vm.liked.shuffled(), 0) },
                            FabItem("Play history", Icons.Filled.History) { if (vm.history.isNotEmpty()) vm.play(vm.history, 0) },
                            FabItem("Search", Icons.Filled.Search) { vm.tab = 1 },
                            FabItem("Settings", Icons.Filled.Settings) { vm.tab = 3 }
                        ),
                        Modifier.align(Alignment.BottomEnd).padding(16.dp)
                    )
                }
            }
        }
        AnimatedVisibility(full, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            Full(vm) { full = false }
        }
    }
}

@Composable
fun Mini(vm: Vm, open: () -> Unit) {
    val s = vm.cur?.song() ?: return
    val frac = if (vm.dur > 0) (vm.pos.toFloat() / vm.dur).coerceIn(0f, 1f) else 0f
    Surface(
        onClick = open, color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(32.dp), tonalElevation = 3.dp, shadowElevation = 6.dp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(s.art, 48.dp, 20.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { frac }, modifier = Modifier.size(44.dp), strokeWidth = 3.dp,
                    trackColor = Color.Transparent
                )
                IconButton(vm::toggle) { Icon(if (vm.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null) }
            }
            IconButton(vm::next) { Icon(Icons.Filled.SkipNext, null) }
        }
    }
}

@Composable
fun SongRow(vm: Vm, list: List<Song>, i: Int) {
    val s = list[i]
    var m by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Art(s.art, 52.dp) },
        trailingContent = {
            Box {
                IconButton({ m = true }) { Icon(Icons.Filled.MoreVert, null) }
                DropdownMenu(m, { m = false }) {
                    DropdownMenuItem({ Text("Play next") }, { vm.playNext(s); m = false })
                    DropdownMenuItem({ Text("Add to queue") }, { vm.addQueue(s); m = false })
                    DropdownMenuItem({ Text(if (vm.isLiked(s)) "Unlike" else "Like") }, { vm.toggleLike(s); m = false })
                }
            }
        },
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { vm.play(list, i) }
    )
}

@Composable
fun Sec(t: String, m: Modifier = Modifier) =
    Text(t, m.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

@Composable
fun Tile(vm: Vm, list: List<Song>, i: Int, m: Modifier) {
    val s = list[i]
    Surface(
        onClick = { vm.play(list, i) }, shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = m
    ) {
        Row(Modifier.height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(s.art, 60.dp, 0.dp)
            Text(s.title, Modifier.padding(horizontal = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun Home(vm: Vm) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greet = when { hour < 5 -> "Late night vibes"; hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"
        hour < 22 -> "Good evening"; else -> "Late night vibes" }
    val feed = if (Mood.cur != null) vm.results else vm.suggestions
    val jump = vm.history.ifEmpty { feed }.take(6)
    val fresh = feed.drop(1).take(12)
    val rest = feed.drop(13)

    LazyColumn(Modifier.statusBarsPadding(), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greet, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("What are we playing?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton({ vm.tab = 1 }) { Icon(Icons.Filled.Search, null) }
                FilledTonalIconButton({ vm.tab = 3 }) { Icon(Icons.Filled.Settings, null) }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(moods) { (n, q) ->
                    FilterChip(
                        selected = Mood.cur == n, onClick = { pickMood(vm, n, q) }, label = { Text(n) },
                        leadingIcon = if (Mood.cur == n) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null
                    )
                }
            }
        }
        if ((Mood.cur != null && vm.loading) || feed.isEmpty()) item {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingBlob(Modifier.size(64.dp), contained = true) }
        }
        feed.firstOrNull()?.let { h ->
            item {
                Box(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(230.dp)
                        .clip(RoundedCornerShape(36.dp)).clickable { vm.play(feed, 0) }
                ) {
                    AsyncImage(h.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                        Text(if (Mood.cur != null) "${Mood.cur} pick" else "Featured", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f))
                        Text(h.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold, color = Color.White)
                        Text(h.artist, maxLines = 1, color = Color.White.copy(alpha = 0.85f))
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ vm.play(feed, 0) }) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play") }
                            FilledTonalButton({ vm.play(feed.shuffled(), 0) }) { Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle") }
                        }
                    }
                }
            }
        }
        if (jump.isNotEmpty()) {
            item { Sec(if (vm.history.isEmpty()) "Quick picks" else "Jump back in", Modifier.padding(top = 12.dp)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    jump.chunked(2).forEachIndexed { r, ch ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ch.forEachIndexed { c, _ -> Tile(vm, jump, r * 2 + c, Modifier.weight(1f)) }
                            if (ch.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if (fresh.isNotEmpty()) {
            item { Sec("Fresh for you", Modifier.padding(top = 12.dp)) }
            item {
                val st = rememberLazyListState()
                LazyRow(
                    state = st, flingBehavior = rememberSnapFlingBehavior(st),
                    contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(fresh) { i, s ->
                        Column(Modifier.width(156.dp).clip(RoundedCornerShape(24.dp)).clickable { vm.play(feed, i + 1) }) {
                            Art(s.art, 156.dp, 24.dp)
                            Text(s.title, Modifier.padding(top = 8.dp, start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge)
                            Text(s.artist, Modifier.padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (rest.isNotEmpty()) {
            item { Sec("More like this", Modifier.padding(top = 12.dp)) }
            itemsIndexed(rest) { i, _ -> Box(Modifier.padding(horizontal = 8.dp)) { SongRow(vm, rest, i) } }
        }
    }
}

@Composable
fun SearchTab(vm: Vm) {
    Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            vm.q, { vm.q = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp),
            placeholder = { Text("Search songs") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true, shape = RoundedCornerShape(32.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() })
        )
        if (vm.loading) WavyLine(null, Modifier.fillMaxWidth().padding(vertical = 8.dp))
        if (vm.results.isEmpty() && !vm.loading) {
            Text("Try", Modifier.padding(top = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Lo-fi beats", "Synthwave", "90s hits", "Acoustic covers", "Arijit Singh", "Jazz classics")) {
                    AssistChip({ vm.q = it; vm.search() }, { Text(it) })
                }
            }
        }
        LazyColumn { itemsIndexed(vm.results) { i, _ -> SongRow(vm, vm.results, i) } }
    }
}

@Composable
fun Library(vm: Vm) {
    var t by remember { mutableIntStateOf(0) }
    val list = if (t == 0) vm.liked else vm.history
    Column(Modifier.statusBarsPadding()) {
        ButtonGroupRow(listOf("Liked", "History"), t, { t = it }, Modifier.fillMaxWidth().padding(16.dp),
            listOf(Icons.Filled.Favorite, Icons.Filled.History))
        if (list.isEmpty()) Text("Nothing here yet", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            itemsIndexed(list) { i, _ -> SongRow(vm, list, i) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Full(vm: Vm, close: () -> Unit) {
    val s = vm.cur?.song() ?: return
    var lyr by remember { mutableStateOf(false) }
    var showQ by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = if (vm.dur > 0) (vm.pos.toFloat() / vm.dur).coerceIn(0f, 1f) else 0f
    val corner by animateDpAsState(if (vm.playing) 24.dp else 44.dp, label = "pc")
    val sc by animateFloatAsState(if (vm.playing) 1f else 0.88f, label = "sc")
    val surf = MaterialTheme.colorScheme.surface
    val tint = rememberArtColor(s.art, MaterialTheme.colorScheme.primary)

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.35f).compositeOver(surf), surf)))) {
        Column(Modifier.systemBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                IconButton(close) { Icon(Icons.Filled.KeyboardArrowDown, null) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                if (lyr) Lyrics(vm) else AsyncImage(
                    model = s.art, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        .graphicsLayer { scaleX = sc; scaleY = sc }
                        .clip(RoundedCornerShape(36.dp))
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalIconButton({ vm.toggleLike(s) }) {
                    Icon(if (vm.isLiked(s)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null)
                }
            }
            WavySlider(
                value = drag ?: frac, onChange = { drag = it },
                onDone = { drag?.let { vm.seek((it * vm.dur).toLong()) }; drag = null },
                modifier = Modifier.fillMaxWidth(), playing = vm.playing
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(vm.pos), style = MaterialTheme.typography.labelMedium)
                Text(fmt(vm.dur), style = MaterialTheme.typography.labelMedium)
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(vm::prev, Modifier.size(64.dp)) { Icon(Icons.Filled.SkipPrevious, null, Modifier.size(40.dp)) }
                Spacer(Modifier.width(12.dp))
                FilledIconButton(vm::toggle, Modifier.size(88.dp), shape = RoundedCornerShape(corner)) {
                    Icon(if (vm.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(46.dp))
                }
                Spacer(Modifier.width(12.dp))
                IconButton(vm::next, Modifier.size(64.dp)) { Icon(Icons.Filled.SkipNext, null, Modifier.size(40.dp)) }
            }
            FloatingToolbar(
                listOf(
                    TbItem(Icons.Filled.Shuffle, "Shuffle", vm.shuffle) { vm.toggleShuffle() },
                    TbItem(if (vm.repeat == 1) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat", vm.repeat != 0) { vm.cycleRepeat() },
                    TbItem(Icons.Filled.Lyrics, "Lyrics", lyr) { lyr = !lyr },
                    TbItem(Icons.AutoMirrored.Filled.QueueMusic, "Queue", false) { showQ = true }
                ),
                Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showQ) ModalBottomSheet({ showQ = false }) {
        val idx = vm.queue.indexOfFirst { it.mediaId == vm.cur?.mediaId }
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp)) {
            itemsIndexed(vm.queue) { i, it ->
                val q = it.song()
                ListItem(
                    headlineContent = { Text(q.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(q.artist, maxLines = 1) },
                    leadingContent = { Art(q.art, 48.dp) },
                    colors = ListItemDefaults.colors(
                        containerColor = if (i == idx) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    ),
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { vm.jump(i) }
                )
            }
        }
    }
}

@Composable
fun Lyrics(vm: Vm) {
    if (vm.lyrics.isEmpty()) {
        Text("No synced lyrics found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val st = rememberLazyListState()
    val idx = vm.lyrics.indexOfLast { it.first <= vm.pos }.coerceAtLeast(0)
    LaunchedEffect(idx) { st.animateScrollToItem((idx - 2).coerceAtLeast(0)) }
    LazyColumn(state = st, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(vm.lyrics) { i, l ->
            Text(
                l.second, Modifier.padding(vertical = 8.dp).clickable { vm.seek(l.first) },
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = if (i == idx) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}
