package app.tunes

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.File
import java.io.IOException

object MediaCache {
    private var c: SimpleCache? = null
    @Synchronized fun get(ctx: Context): SimpleCache = c ?: SimpleCache(
        File(ctx.cacheDir, "media"), LeastRecentlyUsedCacheEvictor(512L * 1024 * 1024),
        StandaloneDatabaseProvider(ctx)
    ).also { c = it }
}

class PlayerService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory().setUserAgent(UA).setAllowCrossProtocolRedirects(true)
        val resolving = ResolvingDataSource.Factory(http, ResolvingDataSource.Resolver { spec ->
            val u = spec.uri.toString()
            try {
                if (u.contains("youtube.com/watch")) spec.withUri(Uri.parse(Repo.streamUrl(u))) else spec
            } catch (e: Exception) { throw IOException(e) }
        })
        val cached = CacheDataSource.Factory().setCache(MediaCache.get(this))
            .setUpstreamDataSourceFactory(resolving)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cached))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(c: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(i: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
