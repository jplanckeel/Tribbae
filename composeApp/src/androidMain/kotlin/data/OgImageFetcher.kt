package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/**
 * Récupère l'image d'une page directement depuis le téléphone : utilisé quand
 * le backend n'est pas joignable (hors ligne, pas de compte). Même stratégie que
 * le backend (backend/internal/preview) : plusieurs identités essayées, oEmbed
 * des plateformes vidéo, extraction par PageImageExtractor.
 */
object OgImageFetcher {

    private const val MAX_BYTES = 2 shl 20
    private const val MAX_REDIRECTS = 5

    /** Identités essayées dans l'ordre : navigateur, puis robots d'aperçu des messageries. */
    private val attempts = listOf(
        mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language" to "fr-FR,fr;q=0.9,en;q=0.7",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "none",
            "Upgrade-Insecure-Requests" to "1",
        ),
        mapOf("User-Agent" to "WhatsApp/2.24.20.0", "Accept-Language" to "fr-FR,fr;q=0.9"),
        mapOf("User-Agent" to "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)", "Accept-Language" to "fr-FR,fr;q=0.9"),
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetch(pageUrl: String): String? = withContext(Dispatchers.IO) {
        runCatching { fetchImages(pageUrl).firstOrNull() }.getOrNull()
    }

    /** Images candidates de la page, la meilleure d'abord (vide si rien trouvé). */
    fun fetchImages(pageUrl: String): List<String> {
        val url = pageUrl.trim()
        knownThumbnail(url)?.let { return listOf(it) }
        for (headers in attempts) {
            val page = runCatching { download(url, headers) }.getOrNull() ?: continue
            if (page.isImage) return listOf(page.finalUrl)
            val doc = Jsoup.parse(ByteArrayInputStream(page.body), page.charset, page.finalUrl)
            val result = PageImageExtractor.extract(doc, page.finalUrl)
            if (result.images.isNotEmpty()) return result.images
            // oEmbed déclaré par la page (vidéos, réseaux)
            doc.selectFirst("link[type=application/json+oembed][href]")?.absUrl("href")
                ?.takeIf { it.isNotEmpty() }
                ?.let { oembedThumbnail(it) }
                ?.let { return listOf(it) }
        }
        return emptyList()
    }

    private class Page(val finalUrl: String, val body: ByteArray, val charset: String?, val isImage: Boolean)

    /** Télécharge la page en suivant les redirections, chaque saut étant vérifié. */
    private fun download(start: String, headers: Map<String, String>, anyType: Boolean = false): Page? {
        var current = start
        repeat(MAX_REDIRECTS + 1) {
            val target = URL(current)
            if (target.protocol != "http" && target.protocol != "https") return null
            if (!isPublicHost(target.host)) return null
            val conn = (target.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = false // suivies à la main, pour vérifier chaque hôte
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: return null
                    current = URL(target, location).toString()
                    return@repeat
                }
                if (code >= 400) return null
                val type = conn.contentType.orEmpty().lowercase()
                if (type.startsWith("image/")) return Page(current, ByteArray(0), null, isImage = true)
                if (!anyType && type.isNotEmpty() && !type.contains("html") && !type.contains("xml")) return null
                val body = conn.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    while (out.size() < MAX_BYTES) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
                val charset = Regex("charset=([\\w-]+)").find(type)?.groupValues?.get(1)
                return Page(current, body, charset, isImage = false)
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    /**
     * Refuse les adresses du réseau local : un lien partagé ne doit pas faire
     * interroger la box, le NAS ou une autre machine du domicile.
     */
    private fun isPublicHost(host: String): Boolean = runCatching {
        InetAddress.getAllByName(host).all { a ->
            !(a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress ||
                a.isAnyLocalAddress || a.isMulticastAddress ||
                (a.address.size == 4 && a.address[0].toInt() == 100 && (a.address[1].toInt() and 0xC0) == 64))
        }
    }.getOrDefault(false)

    private val youTubeId = Regex("""^[A-Za-z0-9_-]{11}$""")

    /** Miniature connue sans télécharger la page (vidéos), ou null. */
    private fun knownThumbnail(url: String): String? {
        val u = runCatching { URI(url) }.getOrNull() ?: return null
        val host = u.host.orEmpty().lowercase().removePrefix("www.").removePrefix("m.")
        val path = u.path.orEmpty()
        val ytId = when (host) {
            "youtu.be" -> path.trim('/')
            "youtube.com", "music.youtube.com" -> when {
                path == "/watch" -> u.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
                else -> path.trim('/').split('/').takeIf { it.size == 2 && it[0] in setOf("shorts", "embed", "live") }?.get(1)
            }
            else -> null
        }
        if (ytId != null && youTubeId.matches(ytId)) return "https://i.ytimg.com/vi/$ytId/hqdefault.jpg"

        val q = URLEncoder.encode(url, "UTF-8")
        val endpoint = when {
            host == "vimeo.com" -> "https://vimeo.com/api/oembed.json?url=$q"
            host == "tiktok.com" || host.endsWith(".tiktok.com") -> "https://www.tiktok.com/oembed?url=$q"
            host == "dailymotion.com" || host == "dai.ly" -> "https://www.dailymotion.com/services/oembed?format=json&url=$q"
            host.startsWith("pinterest.") || host.contains(".pinterest.") -> {
                // L'oEmbed Pinterest n'accepte que le domaine .com
                val com = URI(u.scheme, "www.pinterest.com", u.path, u.query, null).toString()
                "https://www.pinterest.com/oembed.json?url=${URLEncoder.encode(com, "UTF-8")}"
            }
            else -> null
        } ?: return null
        return oembedThumbnail(endpoint)
    }

    private fun oembedThumbnail(endpoint: String): String? = runCatching {
        val page = download(endpoint, mapOf("Accept" to "application/json", "User-Agent" to attempts[0].getValue("User-Agent")), anyType = true)
            ?: return null
        val obj = json.parseToJsonElement(String(page.body, Charsets.UTF_8)).jsonObject
        obj["thumbnail_url"]?.jsonPrimitive?.content?.takeIf { it.startsWith("http") }
    }.getOrNull()
}
