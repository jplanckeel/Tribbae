package data

import java.net.URI

// Adresse sans schéma : limitée aux extensions courantes pour ne pas prendre
// « fin.Début » pour un site.
private val bareDomain = Regex(
    """(?:^|\s)((?:www\.)?[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:fr|com|org|net|eu|be|ch|ca|io|co|uk|de|es|it|info|shop|blog|app)(?:/[^\s<>"']*)?)(?=$|\s)""",
    RegexOption.IGNORE_CASE
)
private val withScheme = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

/**
 * Extrait une adresse web d'un texte collé ou partagé (« Regarde ça https://… »)
 * et complète le schéma s'il manque (« www.site.fr/page »). Null si rien.
 * Même logique que `extractUrl` côté web (frontend/src/lib/links.ts).
 */
fun extractUrl(text: String?): String? {
    val raw = text?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val found = withScheme.find(raw)?.value ?: bareDomain.find(raw)?.groupValues?.get(1) ?: return null
    // Ponctuation de fin de phrase collée à l'adresse
    val cleaned = found.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '»')
    val full = if (cleaned.startsWith("http://", true) || cleaned.startsWith("https://", true)) cleaned else "https://$cleaned"
    return runCatching {
        val uri = URI(full)
        if (uri.host.isNullOrBlank()) null else full
    }.getOrNull()
}
