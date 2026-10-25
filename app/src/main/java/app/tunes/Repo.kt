package app.tunes

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as F

data class Song(val id: String, val title: String, val artist: String, val art: String?)

fun Song.item(): MediaItem = MediaItem.Builder()
    .setMediaId(id).setUri(id)
    .setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setArtist(artist)
            .setArtworkUri(art?.let { Uri.parse(it) }).build()
    ).build()

fun MediaItem.song() = Song(
    mediaId, mediaMetadata.title?.toString() ?: "", mediaMetadata.artist?.toString() ?: "",
    mediaMetadata.artworkUri?.toString()
)

object Repo {
    private var ready = false
    @Synchronized fun init() { if (!ready) { NewPipe.init(Net()); ready = true } }

    private fun StreamInfoItem.toSong() =
        Song(url, name, (uploaderName ?: "").removeSuffix(" - Topic"), thumbnails.firstOrNull()?.url)

    fun search(q: String): List<Song> {
        init()
        val yt = ServiceList.YouTube
        val h = yt.searchQHFactory.fromQuery(q, listOf(F.MUSIC_SONGS), "")
        return SearchInfo.getInfo(yt, h).relatedItems.filterIsInstance<StreamInfoItem>().map { it.toSong() }
    }

    fun related(url: String): List<Song> {
        init()
        return StreamInfo.getInfo(url).relatedItems.filterIsInstance<StreamInfoItem>().map { it.toSong() }
    }

    private val urls = HashMap<String, Pair<Long, String>>()
    fun streamUrl(url: String): String {
        synchronized(urls) {
            urls[url]?.let { if (System.currentTimeMillis() - it.first < 2 * 3600_000L) return it.second }
        }
        init()
        val a = StreamInfo.getInfo(url).audioStreams
        val best = (a.filter { it.format == MediaFormat.M4A }.ifEmpty { a }).maxByOrNull { it.averageBitrate }
            ?: error("no audio stream")
        val u = best.content
        synchronized(urls) { urls[url] = System.currentTimeMillis() to u }
        return u
    }

    private fun clean(t: String) = t.replace(Regex("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]"), "").trim()

    fun lyrics(title: String, artist: String): List<Pair<Long, String>> {
        val u = "https://lrclib.net/api/search?track_name=${Uri.encode(clean(title))}&artist_name=${Uri.encode(artist)}"
        val req = okhttp3.Request.Builder().url(u).header("User-Agent", "Tunes/0.2").build()
        val body = Http.c.newCall(req).execute().use { it.body?.string() } ?: return emptyList()
        val arr = JSONArray(body)
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i).optString("syncedLyrics")
            if (s.isNotBlank()) return parseLrc(s)
        }
        return emptyList()
    }

    private val lrc = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?](.*)""")
    private fun parseLrc(s: String) = s.lines().mapNotNull { l ->
        lrc.matchEntire(l.trim())?.let { m ->
            val (mm, ss, f, t) = m.destructured
            val ms = mm.toLong() * 60000 + ss.toLong() * 1000 + (if (f.isEmpty()) 0 else f.padEnd(3, '0').take(3).toLong())
            ms to t.trim().ifEmpty { "♪" }
        }
    }
}

object Store {
    private lateinit var sp: android.content.SharedPreferences
    fun init(c: android.content.Context) { sp = c.getSharedPreferences("s", 0) }
    fun load(k: String): List<Song> = runCatching {
        val a = JSONArray(sp.getString(k, "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Song(o.getString("id"), o.getString("t"), o.getString("a"), o.optString("r").ifEmpty { null })
        }
    }.getOrDefault(emptyList())
    fun save(k: String, l: List<Song>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("t", it.title).put("a", it.artist).put("r", it.art ?: "")) }
        sp.edit().putString(k, a.toString()).apply()
    }
}
