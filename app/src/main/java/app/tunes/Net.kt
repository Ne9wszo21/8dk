package app.tunes

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

const val UA = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

object Http { val c = OkHttpClient() }

class Net : Downloader() {
    override fun execute(r: Request): Response {
        val m = r.httpMethod()
        var body = r.dataToSend()?.toRequestBody()
        if (body == null && m == "POST") body = ByteArray(0).toRequestBody()
        val b = okhttp3.Request.Builder().url(r.url()).method(m, body)
        b.header("User-Agent", UA)
        r.headers().forEach { (k, v) ->
            b.removeHeader(k)
            v.forEach { b.addHeader(k, it) }
        }
        val res = Http.c.newCall(b.build()).execute()
        if (res.code == 429) { res.close(); throw ReCaptchaException("429", r.url()) }
        val text = res.body?.string()
        return Response(res.code, res.message, res.headers.toMultimap(), text, res.request.url.toString())
    }
}
