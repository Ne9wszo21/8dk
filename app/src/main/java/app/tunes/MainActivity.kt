package app.tunes

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContent { Theme { App() } }
    }
}

@Composable
fun Theme(c: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val cs = if (Build.VERSION.SDK_INT >= 31)
        (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx))
    else (if (dark) darkColorScheme() else lightColorScheme())
    MaterialTheme(colorScheme = cs, content = c)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<Song>()) }
    var now by remember { mutableStateOf<Song?>(null) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { playing = p }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    fun search() = scope.launch {
        loading = true; err = null
        runCatching { withContext(Dispatchers.IO) { Repo.search(q) } }
            .onSuccess { results = it }.onFailure { err = it.message }
        loading = false
    }

    fun play(s: Song) = scope.launch {
        now = s; err = null
        runCatching { withContext(Dispatchers.IO) { Repo.streamUrl(s.url) } }
            .onSuccess { player.setMediaItem(MediaItem.fromUri(it)); player.prepare(); player.play() }
            .onFailure { err = it.message }
    }

    Scaffold(
        bottomBar = {
            now?.let { s ->
                Surface(
                    tonalElevation = 3.dp,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.navigationBarsPadding().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(s.art, null, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium)
                            Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        FilledIconButton(onClick = { if (playing) player.pause() else player.play() }) {
                            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
                        }
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).statusBarsPadding().padding(horizontal = 16.dp)) {
            SearchBar(
                query = q, onQueryChange = { q = it }, onSearch = { search() },
                active = false, onActiveChange = {},
                placeholder = { Text("Search songs") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.fillMaxWidth()
            ) {}
            Spacer(Modifier.height(8.dp))
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn {
                items(results) { s ->
                    ListItem(
                        headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(s.artist, maxLines = 1) },
                        leadingContent = {
                            AsyncImage(s.art, null, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
                        },
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { play(s) }
                    )
                }
            }
        }
    }
}
