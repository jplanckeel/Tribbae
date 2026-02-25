package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Récupère l'image d'une page web avec stratégie multi-fallback :
 * 1. og:image
 * 2. twitter:image
 * 3. <link rel="image_src">
 * 4. schema.org image (JSON-LD)
 * 5. Première grande <img> du <body>
 */
object OgImageFetcher {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    // og:image — les deux ordres d'attributs + variante name=
    private val ogImagePatterns = listOf(
        Regex("""<meta[^>]+property\s*=\s*["']og:image["'][^>]+content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+property\s*=\s*["']og:image["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta[^>]+name\s*=\s*["']og:image["'][^>]+content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+name\s*=\s*["']og:image["']""", RegexOption.IGNORE_CASE),
    )

    // twitter:image
    private val twitterImagePatterns = listOf(
        Regex("""<meta[^>]+(?:name|property)\s*=\s*["']twitter:image(?::src)?["'][^>]+content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+(?:name|property)\s*=\s*["']twitter:image(?::src)?["']""", RegexOption.IGNORE_CASE),
    )

    // <link rel="image_src">
    private val linkImageSrc = Regex(
        """<link[^>]+rel\s*=\s*["']image_src["'][^>]+href\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    // JSON-LD schema.org "image"
    private val jsonLdImage = Regex(
        """"image"\s*:\s*"([^"]+)"""",
        RegexOption.IGNORE_CASE
    )

    // Première <img> avec src http(s)
    private val imgTag = Regex(
        """<img[^>]+src\s*=\s*["'](https?://[^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    suspend fun fetch(pageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val html = downloadPage(pageUrl) ?: return@withContext null

            // 1. og:image
            findFirst(html, ogImagePatterns)?.let { return@withContext resolveUrl(it, pageUrl) }

            // 2. twitter:image
            findFirst(html, twitterImagePatterns)?.let { return@withContext resolveUrl(it, pageUrl) }

            // 3. <link rel="image_src">
            linkImageSrc.find(html)?.groupValues?.get(1)?.let { return@withContext resolveUrl(it, pageUrl) }

            // 4. JSON-LD schema.org
            jsonLdImage.find(html)?.groupValues?.get(1)?.let { return@withContext resolveUrl(it, pageUrl) }

            // 5. Première <img> avec URL absolue (filtre les icônes/petites images par nom)
            imgTag.findAll(html)
                .map { it.groupValues[1] }
                .firstOrNull { url ->
                    !url.contains("logo", ignoreCase = true) &&
                    !url.contains("icon", ignoreCase = true) &&
                    !url.contains("sprite", ignoreCase = true) &&
                    !url.contains("pixel", ignoreCase = true) &&
                    !url.contains("1x1", ignoreCase = true) &&
                    !url.contains("tracking", ignoreCase = true) &&
                    !url.endsWith(".svg", ignoreCase = true) &&
                    !url.endsWith(".gif", ignoreCase = true)
                }?.let { return@withContext resolveUrl(it, pageUrl) }

            null
        } catch (_: Exception) {
            null
        }
    }

    private fun downloadPage(pageUrl: String): String? {
        val conn = (URL(pageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.5")
        }
        return try {
            // Gère les redirections manuelles (certains sites redirigent 301/302 vers un autre domaine)
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location != null) {
                    return downloadPage(resolveUrl(location, pageUrl))
                }
                return null
            }
            if (code != 200) {
                conn.disconnect()
                return null
            }
            // Lire seulement les premiers 100KB (le head + début du body suffisent)
            val bytes = conn.inputStream.bufferedReader().use { reader ->
                val buf = CharArray(100_000)
                val read = reader.read(buf)
                if (read > 0) String(buf, 0, read) else ""
            }
            conn.disconnect()
            bytes
        } catch (_: Exception) {
            conn.disconnect()
            null
        }
    }

    private fun findFirst(html: String, patterns: List<Regex>): String? {
        for (pattern in patterns) {
            pattern.find(html)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    /** Résout une URL relative en absolue */
    private fun resolveUrl(imageUrl: String, pageUrl: String): String {
        val trimmed = imageUrl.trim()
            .replace("&amp;", "&")
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (trimmed.startsWith("//")) return "https:$trimmed"
        return try {
            URL(URL(pageUrl), trimmed).toString()
        } catch (_: Exception) {
            trimmed
        }
    }
}
