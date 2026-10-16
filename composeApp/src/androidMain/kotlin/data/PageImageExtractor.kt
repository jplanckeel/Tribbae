package data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/** Aperçu d'une page : titre et images candidates, la meilleure d'abord. */
data class PageImages(val title: String, val images: List<String>) {
    val image: String? get() = images.firstOrNull()
}

/**
 * Extraction des images d'une page HTML, même logique que le backend
 * (backend/internal/preview/extract.go) : balises de partage, données
 * schema.org, puis images du corps (chargement différé, srcset, noscript),
 * en écartant logos, icônes et pixels de suivi.
 */
object PageImageExtractor {

    private const val SCORE_OG = 100
    private const val SCORE_TWITTER = 90
    private const val SCORE_JSONLD = 85
    private const val SCORE_IMAGE_SRC = 80
    private const val SCORE_ITEMPROP = 75
    private const val SCORE_THUMBNAIL = 70
    private const val SCORE_POSTER = 45
    private const val SCORE_BODY = 30
    private const val SCORE_DECLARED_LOGO = 12
    private const val SCORE_TOUCH_ICON = 1
    private const val MAX_IMAGES = 12
    private const val MAX_BODY_IMAGES = 40

    private val junkHint = Regex("""\b(logo|icon|avatar|sprite|badge|emoji|pixel|spinner|loader|placeholder)""")
    private val junkPath = Regex("""logo|icon|sprite|avatar|pixel|spacer|blank\.|1x1|tracking|badge|emoji|gravatar|placeholder|spinner|loader|/ads?/|doubleclick|facebook\.com/tr""")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Images génériques que certaines plateformes servent à un visiteur
     * non reconnu (au lieu de la photo du post) : elles ne comptent pas.
     */
    private val genericImage = Regex("""static\.cdninstagram\.com/rsrc\.php|s\.pinimg\.com/webapp/|static\.xx\.fbcdn\.net/rsrc\.php""")

    fun isGeneric(url: String): Boolean = genericImage.containsMatchIn(url)

    private class Candidate(val url: String, val score: Int, val order: Int)

    fun extract(html: String, pageUrl: String): PageImages = extract(Jsoup.parse(html, pageUrl), pageUrl)

    fun extract(doc: Document, pageUrl: String): PageImages {
        val cands = mutableListOf<Candidate>()
        var order = 0
        fun push(raw: String?, score: Int) {
            val u = resolve(doc, raw) ?: return
            if (u.contains("captcha", ignoreCase = true) || isGeneric(u)) return
            val s = if (pathOf(u).contains("logo")) SCORE_DECLARED_LOGO else score
            cands += Candidate(u, s, order++)
        }

        // Balises de partage
        for (meta in doc.select("meta[content]")) {
            val content = meta.attr("content").trim()
            if (content.isEmpty()) continue
            for (key in listOf("property", "name", "itemprop").map { meta.attr(it).trim().lowercase() }) {
                when (key) {
                    "og:image", "og:image:url", "og:image:secure_url" -> push(content, SCORE_OG)
                    "twitter:image", "twitter:image:src" -> push(content, SCORE_TWITTER)
                    "thumbnail", "thumbnailurl" -> push(content, SCORE_THUMBNAIL)
                }
            }
            if (meta.attr("itemprop") == "image") push(content, SCORE_ITEMPROP)
        }
        doc.select("link[rel=image_src][href]").forEach { push(it.attr("href"), SCORE_IMAGE_SRC) }
        doc.select("link[itemprop=image][href]").forEach { push(it.attr("href"), SCORE_ITEMPROP) }

        // Données structurées schema.org
        for (script in doc.select("script[type*=ld+json]")) {
            val root = runCatching { json.parseToJsonElement(script.data()) }.getOrNull() ?: continue
            ldImages(root).forEach { push(it, SCORE_JSONLD) }
        }

        doc.select("video[poster]").forEach { push(it.attr("poster"), SCORE_POSTER) }

        // Images du corps de page (noscript compris : jsoup y lit le HTML)
        var bodyImages = 0
        for (img in doc.select("img, picture > source")) {
            if (bodyImages >= MAX_BODY_IMAGES) break
            if (img.tagName() == "img" && img.attr("itemprop") == "image") {
                imgSource(img)?.let { push(it, SCORE_ITEMPROP) }
                continue
            }
            val w = dimension(img, "width")
            val h = dimension(img, "height")
            if ((w in 1..119) || (h in 1..119)) continue
            val hint = "${img.attr("class")} ${img.id()} ${img.attr("alt")}".lowercase()
            if (junkHint.containsMatchIn(hint)) continue
            val src = imgSource(img) ?: continue
            val abs = resolve(doc, src) ?: continue
            if (junkImageUrl(abs)) continue
            var score = SCORE_BODY
            if (w * h >= 300 * 200) score += 20
            if (img.parents().any { it.tagName() in setOf("header", "nav", "footer", "aside") }) score -= 25
            if (img.parents().any { it.tagName() in setOf("article", "main") }) score += 10
            if (pathOf(abs).endsWith(".gif")) score -= 10
            bodyImages++
            cands += Candidate(abs, score, order++)
        }

        val images = cands.sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.order })
            .map { it.url }.distinct().toMutableList()
        // Icône du site : seulement s'il n'y a vraiment rien d'autre
        if (images.isEmpty()) {
            doc.select("link[rel~=apple-touch-icon][href]").forEach { el ->
                resolve(doc, el.attr("href"))?.let { if (it !in images) images += it }
            }
        }
        val title = listOf(
            doc.select("meta[property=og:title]").attr("content"),
            doc.select("meta[name=twitter:title]").attr("content"),
            doc.title(),
        ).map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        return PageImages(title, images.take(MAX_IMAGES))
    }

    /** Images du JSON-LD (Recipe, Event, Product, Article…), sans les logos d'organisation. */
    private fun ldImages(el: JsonElement, depth: Int = 0): List<String> {
        if (depth > 6) return emptyList()
        return when (el) {
            is JsonArray -> el.flatMap { ldImages(it, depth + 1) }
            is JsonObject -> {
                val out = mutableListOf<String>()
                el["@graph"]?.let { out += ldImages(it, depth + 1) }
                el["mainEntity"]?.let { out += ldImages(it, depth + 1) }
                val types = ldStrings(el["@type"]).map { it.substringAfterLast('/') }
                val organization = types.any { it in setOf("Organization", "Person", "WebSite", "Brand") }
                if ("ImageObject" in types) {
                    val id = (ldString(el["@id"]) + ldString(el["url"])).lowercase()
                    if (!id.contains("logo")) {
                        (ldString(el["contentUrl"]).ifEmpty { ldString(el["url"]) }).takeIf { it.isNotEmpty() }?.let { out += it }
                    }
                } else if (types.isNotEmpty() && !organization) {
                    for (key in listOf("image", "thumbnailUrl", "primaryImageOfPage")) {
                        el[key]?.let { out += imageValues(it) }
                    }
                }
                out
            }
            else -> emptyList()
        }
    }

    private fun imageValues(el: JsonElement): List<String> = when (el) {
        is JsonPrimitive -> listOfNotNull(el.contentOrNull)
        is JsonArray -> el.flatMap { imageValues(it) }
        is JsonObject -> listOf(ldString(el["url"]).ifEmpty { ldString(el["contentUrl"]) }).filter { it.isNotEmpty() }
    }

    private fun ldString(el: JsonElement?): String = when (el) {
        is JsonPrimitive -> el.contentOrNull.orEmpty()
        is JsonArray -> el.firstOrNull()?.let { ldString(it) }.orEmpty()
        else -> ""
    }

    private fun ldStrings(el: JsonElement?): List<String> = when (el) {
        is JsonPrimitive -> listOfNotNull(el.contentOrNull)
        is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        else -> emptyList()
    }

    /** Meilleure source d'une image, chargement différé compris. */
    private fun imgSource(el: Element): String? {
        bestFromSrcset(listOf("data-srcset", "data-lazy-srcset", "srcset").map { el.attr(it) }.firstOrNull { it.isNotBlank() }.orEmpty())
            ?.let { return it }
        return listOf("data-src", "data-lazy-src", "data-original", "data-lazy", "data-url", "src")
            .map { el.attr(it).trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("data:") }
    }

    /** Plus grande variante d'un srcset jusqu'à 1600 px, sinon la plus petite au-delà. */
    fun bestFromSrcset(srcset: String): String? {
        var best: String? = null
        var bestW = -1
        var over: String? = null
        var overW = Int.MAX_VALUE
        for (part in srcset.split(",")) {
            val fields = part.trim().split(Regex("\\s+"))
            val url = fields.firstOrNull()?.takeIf { it.isNotEmpty() && !it.startsWith("data:") } ?: continue
            val d = fields.getOrNull(1).orEmpty()
            val w = when {
                d.endsWith("w") -> d.dropLast(1).toIntOrNull() ?: 0
                d.endsWith("x") -> ((d.dropLast(1).toDoubleOrNull() ?: 0.0) * 600).toInt()
                else -> 0
            }
            if (w > 1600) {
                if (w < overW) { over = url; overW = w }
            } else if (w > bestW) {
                best = url; bestW = w
            }
        }
        return best ?: over
    }

    private fun dimension(el: Element, attr: String): Int = el.attr(attr).trim().removeSuffix("px").toIntOrNull() ?: 0

    /** URL absolue http(s), en tenant compte de <base href>. */
    private fun resolve(doc: Document, raw: String?): String? {
        val r = raw?.trim()?.replace("&amp;", "&") ?: return null
        if (r.isEmpty() || r.startsWith("data:")) return null
        val base = doc.selectFirst("base[href]")?.absUrl("href")?.takeIf { it.isNotEmpty() } ?: doc.location()
        val abs = runCatching { URI(base).resolve(r.replace(" ", "%20")).toString() }.getOrNull()
            ?: (if (r.startsWith("//")) "https:$r" else r)
        val scheme = abs.substringBefore(':').lowercase()
        if (scheme != "http" && scheme != "https") return null
        return abs.substringBefore('#')
    }

    private fun pathOf(url: String): String = runCatching { URI(url).path.orEmpty().lowercase() }.getOrDefault(url.lowercase())

    private fun junkImageUrl(url: String): Boolean {
        val u = runCatching { URI(url) }.getOrNull() ?: return true
        val p = (u.host.orEmpty() + u.path.orEmpty()).lowercase()
        if (p.endsWith(".svg") || p.endsWith(".ico")) return true
        return junkPath.containsMatchIn(p)
    }
}
