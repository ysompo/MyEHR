package com.myehr.app.desktop

import com.github.javakeyring.Keyring
import com.myehr.app.data.Person
import com.myehr.app.data.RecordStore
import java.io.File
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Per-user app folder: %APPDATA%\MyEHR on Windows, ~/Library/Application Support/MyEHR on macOS. */
val appDir: File by lazy {
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    val dir = when {
        "win" in os -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "MyEHR")
        "mac" in os -> File(home, "Library/Application Support/MyEHR")
        else -> File(home, ".myehr")
    }
    dir.apply { mkdirs() }
}

/** Secrets in the operating system's own keychain (Windows Credential Manager / macOS Keychain). */
object Keychain {
    private const val SERVICE = "MyEHR"
    private val ring by lazy { Keyring.create() }
    fun get(name: String): String? = runCatching { ring.getPassword(SERVICE, name) }.getOrNull()
    fun set(name: String, value: String?) {
        if (value == null) runCatching { ring.deletePassword(SERVICE, name) } else ring.setPassword(SERVICE, name, value)
    }
}

/** The data key: 32 random bytes, kept only in the OS keychain. */
fun keychainDataKey(): ByteArray {
    val b64 = Keychain.get("data-key") ?: Base64.getEncoder().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        .also { Keychain.set("data-key", it) }
    return Base64.getDecoder().decode(b64)
}

/** AES-256-GCM for every stored value. */
private class Crypto(rawKey: ByteArray) {
    private val key = SecretKeySpec(rawKey, "AES")
    fun enc(text: String): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return iv + c.doFinal(text.toByteArray())
    }
    fun dec(blob: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, 12)) }
        return String(c.doFinal(blob, 12, blob.size - 12))
    }
}

/** Encrypted local store, same layout as the Android one. */
class DesktopStore(file: File = File(appDir, "myehr.db"), dataKey: () -> ByteArray = ::keychainDataKey) : RecordStore {
    private val crypto by lazy { Crypto(dataKey()) }
    private val db: Connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").apply {
        createStatement().use {
            it.executeUpdate("CREATE TABLE IF NOT EXISTS person (id TEXT PRIMARY KEY, data BLOB NOT NULL, last_sync INTEGER)")
            it.executeUpdate("CREATE TABLE IF NOT EXISTS raw (person TEXT NOT NULL, kind TEXT NOT NULL, k TEXT NOT NULL, json BLOB NOT NULL, at INTEGER NOT NULL, PRIMARY KEY(person, kind, k))")
            it.executeUpdate("CREATE TABLE IF NOT EXISTS ai_log (at INTEGER NOT NULL, person TEXT NOT NULL, data BLOB NOT NULL)")
        }
    }

    @Synchronized override fun putPerson(p: Person) {
        db.prepareStatement("INSERT OR REPLACE INTO person (id, data, last_sync) VALUES (?, ?, ?)").use {
            it.setString(1, p.id); it.setBytes(2, crypto.enc("${if (p.isChild) 1 else 0}|${p.name}")); it.setObject(3, p.lastSync); it.executeUpdate()
        }
    }

    @Synchronized override fun people(): List<Person> = db.prepareStatement("SELECT id, data, last_sync FROM person").use { st ->
        st.executeQuery().use { r ->
            buildList {
                while (r.next()) {
                    val (child, name) = crypto.dec(r.getBytes(2)).split("|", limit = 2)
                    add(Person(r.getString(1), name, child == "1", r.getLong(3).takeIf { !r.wasNull() }))
                }
            }.sortedWith(compareBy({ it.isChild }, { it.name }))
        }
    }

    @Synchronized override fun put(person: String, kind: String, key: String, json: String) {
        db.prepareStatement("INSERT OR REPLACE INTO raw (person, kind, k, json, at) VALUES (?, ?, ?, ?, ?)").use {
            it.setString(1, person); it.setString(2, kind); it.setString(3, key); it.setBytes(4, crypto.enc(json)); it.setLong(5, System.currentTimeMillis()); it.executeUpdate()
        }
    }

    @Synchronized override fun get(person: String, kind: String, key: String): String? =
        db.prepareStatement("SELECT json FROM raw WHERE person=? AND kind=? AND k=?").use {
            it.setString(1, person); it.setString(2, kind); it.setString(3, key)
            it.executeQuery().use { r -> if (r.next()) crypto.dec(r.getBytes(1)) else null }
        }

    @Synchronized override fun fetchedAt(person: String, kind: String, key: String): Long? =
        db.prepareStatement("SELECT at FROM raw WHERE person=? AND kind=? AND k=?").use {
            it.setString(1, person); it.setString(2, kind); it.setString(3, key)
            it.executeQuery().use { r -> if (r.next()) r.getLong(1) else null }
        }

    @Synchronized override fun all(person: String, kind: String): Map<String, String> =
        db.prepareStatement("SELECT k, json FROM raw WHERE person=? AND kind=?").use {
            it.setString(1, person); it.setString(2, kind)
            it.executeQuery().use { r -> buildMap { while (r.next()) put(r.getString(1), crypto.dec(r.getBytes(2))) } }
        }

    @Synchronized fun logAi(person: String, question: String, sent: String, answer: String) {
        db.prepareStatement("INSERT INTO ai_log (at, person, data) VALUES (?, ?, ?)").use {
            it.setLong(1, System.currentTimeMillis()); it.setString(2, person)
            it.setBytes(3, crypto.enc(listOf(question, sent, answer).joinToString("\u0000"))); it.executeUpdate()
        }
    }
}
