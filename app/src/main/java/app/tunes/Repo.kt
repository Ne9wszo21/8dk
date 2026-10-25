package app.tunes

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory as F

data class Song(val title: String, val artist: String, val url: String, val art: String?)

object Repo {
    private var ready = false
    private fun init() { if (!ready) { NewPipe.init(Net()); ready = true } }

    fun search(q: String): List<Song> {
        init()
        val yt = ServiceList.YouTube
        val h = yt.searchQHFactory.fromQuery(q, listOf(F.MUSIC_SONGS), "")
        return SearchInfo.getInfo(yt, h).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { Song(it.name, it.uploaderName ?: "", it.url, it.thumbnails.firstOrNull()?.url) }
    }

    fun streamUrl(url: String): String {
        init()
        val i = StreamInfo.getInfo(url)
        return i.audioStreams.maxByOrNull { it.averageBitrate }!!.content
    }
}
