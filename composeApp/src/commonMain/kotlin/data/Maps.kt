package data

/**
 * Adresse d'une idée ouverte dans une app de cartes. Android préfère `geoUri`
 * (l'app par défaut du téléphone, ou le choix entre celles installées) et
 * retombe sur `searchUrl` (Google Maps dans le navigateur) s'il n'y en a aucune.
 * `searchUrl` suit les mêmes cas que `mapsUrl` du web.
 */
object Maps {
    fun geoUri(location: String): String = "geo:0,0?q=" + encode(location)

    fun searchUrl(location: String): String = "https://www.google.com/maps/search/?api=1&query=" + encode(location)

    private fun encode(location: String): String =
        java.net.URLEncoder.encode(location.trim(), "UTF-8").replace("+", "%20")
                // Comme encodeURIComponent côté web : ! ' ( ) ~ restent tels quels
                .replace("%21", "!").replace("%27", "'").replace("%28", "(").replace("%29", ")").replace("%7E", "~")
}
