package data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidStorage(context: Context) : Storage {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("tribbae_data", Context.MODE_PRIVATE)

    // Le jeton vit dans un fichier exclu des sauvegardes (backup_rules.xml ne
    // sauvegarde que tribbae_data) : il ne doit pas quitter l'appareil.
    private val secrets: SharedPreferences =
        context.getSharedPreferences("tribbae_secrets", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    override fun saveLinks(links: List<Link>) {
        prefs.edit().putString("links", json.encodeToString(links)).apply()
    }

    override fun loadLinks(): List<Link> {
        val raw = prefs.getString("links", null) ?: return emptyList()
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }

    override fun saveFolders(folders: List<Folder>) {
        prefs.edit().putString("folders", json.encodeToString(folders)).apply()
    }

    override fun loadFolders(): List<Folder> {
        val raw = prefs.getString("folders", null) ?: return emptyList()
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }

    override fun saveTags(tags: List<String>) {
        prefs.edit().putString("tags", json.encodeToString(tags)).apply()
    }

    override fun loadTags(): List<String> {
        val raw = prefs.getString("tags", null) ?: return emptyList()
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }

    override fun saveChildren(children: List<Child>) {
        prefs.edit().putString("children", json.encodeToString(children)).apply()
    }

    override fun loadChildren(): List<Child> {
        val raw = prefs.getString("children", null) ?: return emptyList()
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }

    override fun saveToken(token: String) {
        secrets.edit().putString("auth_token", token).apply()
        prefs.edit().remove("auth_token").apply()
    }

    // Migration : les versions précédentes rangeaient le jeton dans tribbae_data
    override fun loadToken(): String? =
        secrets.getString("auth_token", null)
            ?: prefs.getString("auth_token", null)?.also { saveToken(it) }
}
