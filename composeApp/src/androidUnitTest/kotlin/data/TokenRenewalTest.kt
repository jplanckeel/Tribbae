package data

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mêmes règles que le web (frontend/src/lib/session.ts). */
class TokenRenewalTest {
    private fun jwt(payload: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray()) + "." +
            enc.encodeToString(payload.toByteArray()) + ".signature"
    }

    private val now = 1_800_000_000_000L

    @Test
    fun readsTheIssueDate() {
        assertEquals(1_799_990_000_000L, TokenRenewal.issuedAt(jwt("""{"sub":"6a","iat":1799990000,"exp":1802582000}""")))
        assertNull(TokenRenewal.issuedAt("pas-un-jeton"))
        assertNull(TokenRenewal.issuedAt(jwt("""{"sub":"6a"}""")))
    }

    @Test
    fun renewsAtMostOnceADay() {
        val hourOld = jwt("""{"iat":${(now - 3_600_000) / 1000}}""")
        val twoDaysOld = jwt("""{"iat":${(now - 2 * TokenRenewal.RENEW_AFTER_MS) / 1000}}""")
        assertFalse(TokenRenewal.shouldRenew(hourOld, now))
        assertTrue(TokenRenewal.shouldRenew(twoDaysOld, now))
        assertTrue(TokenRenewal.shouldRenew("illisible", now), "date illisible : on renouvelle")
    }
}
