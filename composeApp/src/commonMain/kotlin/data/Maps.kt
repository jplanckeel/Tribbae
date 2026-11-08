package data

/** Adresse d'une idée ouverte dans Google Maps (l'app si elle est installée). Mêmes cas que `mapsUrl` du web. */
object Maps {
    fun searchUrl(location: String): String =
        "https://www.google.com/maps/search/?api=1&query=" +
            java.net.URLEncoder.encode(location.trim(), "UTF-8").replace("+", "%20")
                // Comme encodeURIComponent côté web : ! ' ( ) ~ restent tels quels
                .replace("%21", "!").replace("%27", "'").replace("%28", "(").replace("%29", ")").replace("%7E", "~")
}
