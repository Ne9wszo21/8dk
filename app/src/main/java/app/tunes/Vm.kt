package app.tunes

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Vm(app: Application) : AndroidViewModel(app) {
    var tab by mutableIntStateOf(0)
    var q by mutableStateOf("")
    var results by mutableStateOf<List<Song>>(emptyList())
    var loading by mutableStateOf(false)
    var err by mutableStateOf<String?>(null)
    var liked by mutableStateOf<List<Song>>(emptyList())
    var history by mutableStateOf<List<Song>>(emptyList())
    var suggestions by mutableStateOf<List<Song>>(emptyList())
    var queue by mutableStateOf<List<MediaItem>>(emptyList())
    var cur by mutableStateOf<MediaItem?>(null)
    var playing by mutableStateOf(false)
    var pos by mutableLongStateOf(0L)
    var dur by mutableLongStateOf(0L)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(0)
    var lyrics by mutableStateOf<List<Pair<Long, String>>>(emptyList())
    private var mc: MediaController? = null

    private val l = object : Player.Listener {
        override fun onIsPlayingChanged(p: Boolean) { playing = p }
        override fun onMediaItemTransition(i: MediaItem?, r: Int) { sync(); i?.let { track(it) } }
        override fun onTimelineChanged(t: Timeline, r: Int) { sync() }
        override fun onShuffleModeEnabledChanged(b: Boolean) { shuffle = b }
        override fun onRepeatModeChanged(m: Int) { repeat = m }
        override fun onPlayerError(e: PlaybackException) {
            err = e.errorCodeName + (e.cause?.message?.let { ": $it" } ?: "")
        }
    }

    init {
        Store.init(app)
        liked = Store.load("liked")
        history = Store.load("hist")
        val t = SessionToken(app, ComponentName(app, PlayerService::class.java))
        val f = MediaController.Builder(app, t).buildAsync()
        f.addListener({
            runCatching { f.get() }.onSuccess { c ->
                mc = c; c.addListener(l); playing = c.isPlaying
                shuffle = c.shuffleModeEnabled; repeat = c.repeatMode; sync()
                if (c.currentMediaItem != null) loadLyrics(c.currentMediaItem!!)
            }
        }, ContextCompat.getMainExecutor(app))
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val h = history.firstOrNull()
                    if (h != null) Repo.related(h.id) else Repo.search("top hits")
                }.getOrDefault(emptyList())
            }
            suggestions = r
        }
    }

    private fun sync() {
        val c = mc ?: return
        cur = c.currentMediaItem
        queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }
    }

    fun tick() { mc?.let { pos = it.currentPosition; dur = it.duration.coerceAtLeast(0L) } }

    private fun track(i: MediaItem) {
        val s = i.song()
        history = (listOf(s) + history.filter { it.id != s.id }).take(50)
        Store.save("hist", history)
        loadLyrics(i)
        extend()
    }

    private fun loadLyrics(i: MediaItem) {
        lyrics = emptyList()
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { Repo.lyrics(i.mediaMetadata.title.toString(), i.mediaMetadata.artist.toString()) }
                    .getOrDefault(emptyList())
            }
            if (cur?.mediaId == i.mediaId) lyrics = r
        }
    }

    private fun extend() {
        val c = mc ?: return
        if (c.currentMediaItemIndex < c.mediaItemCount - 2) return
        val id = c.currentMediaItem?.mediaId ?: return
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Repo.related(id) }.getOrDefault(emptyList()) }
            val have = queue.map { it.mediaId }.toSet()
            val add = r.filter { it.id !in have }.take(15).map { it.item() }
            if (add.isNotEmpty()) c.addMediaItems(add)
        }
    }

    fun search() {
        if (q.isBlank()) return
        viewModelScope.launch {
            loading = true; err = null
            runCatching { withContext(Dispatchers.IO) { Repo.search(q) } }
                .onSuccess { results = it }.onFailure { err = it.message ?: "search failed" }
            loading = false
        }
    }

    fun play(list: List<Song>, i: Int) {
        val c = mc ?: return
        err = null
        c.setMediaItems(list.map { it.item() }, i, 0L)
        c.prepare(); c.play()
    }

    fun playNext(s: Song) {
        val c = mc ?: return
        if (c.mediaItemCount == 0) play(listOf(s), 0) else c.addMediaItem(c.currentMediaItemIndex + 1, s.item())
    }

    fun addQueue(s: Song) {
        val c = mc ?: return
        if (c.mediaItemCount == 0) play(listOf(s), 0) else c.addMediaItem(s.item())
    }

    fun isLiked(s: Song) = liked.any { it.id == s.id }
    fun toggleLike(s: Song) {
        liked = if (isLiked(s)) liked.filter { it.id != s.id } else listOf(s) + liked
        Store.save("liked", liked)
    }

    fun toggle() { mc?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { mc?.seekToNextMediaItem() }
    fun prev() { mc?.let { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() } }
    fun seek(ms: Long) { mc?.seekTo(ms) }
    fun jump(i: Int) { mc?.seekTo(i, 0L) }
    fun toggleShuffle() { mc?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() { mc?.let { it.repeatMode = when (it.repeatMode) { 0 -> 2; 2 -> 1; else -> 0 } } }

    override fun onCleared() { mc?.removeListener(l); mc?.release() }
}
