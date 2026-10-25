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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        enableEdgeToEdge()
        setContent { Theme { Root(viewModel()) } }
    }
}

@Composable
fun Theme(c: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val cs = if (Build.VERSION.SDK_INT >= 31)
        (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx))
    else (if (dark) darkColorScheme() else lightColorScheme())
    MaterialTheme(
        colorScheme = cs,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp)
        ),
        content = c
    )
}

fun fmt(ms: Long) = String.format("%d:%02d", ms / 60000, ms / 1000 % 60)

@Composable
fun Art(url: String?, size: Dp, r: Dp = 14.dp) = AsyncImage(
    model = url, contentDescription = null, contentScale = ContentScale.Crop,
    modifier = Modifier.size(size).clip(RoundedCornerShape(r))
)

@Composable
fun Root(vm: Vm) {
    var full by remember { mutableStateOf(false) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        while (true) { vm.tick(); delay(500) }
    }
    BackHandler(full) { full = false }
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
                    NavigationBar {
                        listOf("Home" to Icons.Filled.Home, "Search" to Icons.Filled.Search,
                            "Library" to Icons.Filled.LibraryMusic).forEachIndexed { i, (n, ic) ->
                            NavigationBarItem(vm.tab == i, { vm.tab = i }, { Icon(ic, n) }, label = { Text(n) })
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (vm.tab) { 0 -> Home(vm); 1 -> SearchTab(vm); else -> Library(vm) }
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
    Surface(
        onClick = open, color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(s.art, 48.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(vm::toggle) { Icon(if (vm.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null) }
                IconButton(vm::next) { Icon(Icons.Filled.SkipNext, null) }
            }
            LinearProgressIndicator(
                progress = { if (vm.dur > 0) (vm.pos.toFloat() / vm.dur).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth()
            )
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
fun Home(vm: Vm) {
    LazyColumn(Modifier.statusBarsPadding(), contentPadding = PaddingValues(16.dp)) {
        item { Text("Tunes", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        if (vm.history.isNotEmpty()) {
            item { Text("Recently played", Modifier.padding(top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge) }
            item {
                val h = vm.history.take(15)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(h) { i, s ->
                        Column(Modifier.width(140.dp).clip(RoundedCornerShape(20.dp)).clickable { vm.play(h, i) }) {
                            Art(s.art, 140.dp, 24.dp)
                            Text(s.title, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge)
                            Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item { Text(if (vm.history.isEmpty()) "Top hits" else "Recommended", Modifier.padding(top = 20.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleLarge) }
        itemsIndexed(vm.suggestions) { i, _ -> SongRow(vm, vm.suggestions, i) }
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
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn { itemsIndexed(vm.results) { i, _ -> SongRow(vm, vm.results, i) } }
    }
}

@Composable
fun Library(vm: Vm) {
    var t by remember { mutableIntStateOf(0) }
    val list = if (t == 0) vm.liked else vm.history
    Column(Modifier.statusBarsPadding()) {
        TabRow(t) {
            Tab(t == 0, { t = 0 }, text = { Text("Liked") })
            Tab(t == 1, { t = 1 }, text = { Text("History") })
        }
        if (list.isEmpty()) Text("Nothing here yet", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
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
    val corner by animateDpAsState(if (vm.playing) 24.dp else 40.dp, label = "pc")

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.systemBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(close) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                Row {
                    IconButton({ lyr = !lyr }) {
                        Icon(Icons.Filled.Lyrics, null, tint = if (lyr) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton({ showQ = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                if (lyr) Lyrics(vm) else AsyncImage(
                    model = s.art, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(36.dp))
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
            Slider(
                value = drag ?: frac, onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { vm.seek((it * vm.dur).toLong()) }; drag = null }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(vm.pos), style = MaterialTheme.typography.labelMedium)
                Text(fmt(vm.dur), style = MaterialTheme.typography.labelMedium)
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconToggleButton(vm.shuffle, { vm.toggleShuffle() }) { Icon(Icons.Filled.Shuffle, null) }
                IconButton(vm::prev, Modifier.size(56.dp)) { Icon(Icons.Filled.SkipPrevious, null, Modifier.size(36.dp)) }
                FilledIconButton(vm::toggle, Modifier.size(84.dp), shape = RoundedCornerShape(corner)) {
                    Icon(if (vm.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(44.dp))
                }
                IconButton(vm::next, Modifier.size(56.dp)) { Icon(Icons.Filled.SkipNext, null, Modifier.size(36.dp)) }
                FilledTonalIconToggleButton(vm.repeat != 0, { vm.cycleRepeat() }) {
                    Icon(if (vm.repeat == 1) Icons.Filled.RepeatOne else Icons.Filled.Repeat, null)
                }
            }
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
                        containerColor = if (i == idx) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent
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
