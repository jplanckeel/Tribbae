package data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Session glissante : le jeton dure 30 jours ; on l'échange contre un neuf au
 * plus une fois par jour, pour qu'une personne qui utilise l'app ne soit
 * jamais déconnectée. Même règle côté web : frontend/src/lib/session.ts
 * (tokenIssuedAt, shouldRenew).
 */
object TokenRenewal {
    const val RENEW_AFTER_MS = 24L * 60 * 60 * 1000

    /** Date d'émission (ms) lue dans le jeton (`iat`, sans vérifier la signature). Null si illisible. */
    fun issuedAt(token: String): Long? = runCatching {
        val part = token.split(".").getOrNull(1) ?: return null
        val payload = String(java.util.Base64.getUrlDecoder().decode(part.trimEnd('=')))
        Json.parseToJsonElement(payload).jsonObject["iat"]?.jsonPrimitive?.longOrNull?.times(1000)
    }.getOrNull()

    /** Le jeton a plus d'un jour (ou une date illisible) : à renouveler. */
    fun shouldRenew(token: String, now: Long = System.currentTimeMillis()): Boolean {
        val iat = issuedAt(token) ?: return true
        return now - iat > RENEW_AFTER_MS
    }
}
