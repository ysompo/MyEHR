package com.myehr.app.data

import android.content.ContentValues
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.myehr.app.sync.RawSink
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Database passphrase: 32 random bytes, encrypted with an AES key that lives in the
 * Android Keystore (never leaves the device). Only the encrypted blob is stored in prefs.
 */
object KeyManager {
    private const val ALIAS = "myehr_db_key"
    private const val PREFS = "myehr_keys"
    private const val PREF_BLOB = "db_pass"

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun passphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = keystoreKey()
        prefs.getString(PREF_BLOB, null)?.let { stored ->
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            return cipher.doFinal(raw.copyOfRange(12, raw.size))
        }
        val pass = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val blob = cipher.iv + cipher.doFinal(pass)
        prefs.edit().putString(PREF_BLOB, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
        return pass
    }
}

/**
 * Encrypted local store (SQLCipher). Raw site answers are kept as-is per person and
 * parsed when shown, so a parser fix never needs a re-sync.
 */
class Store private constructor(context: Context) : RawSink {
    private val helper: SQLiteOpenHelper

    init {
        System.loadLibrary("sqlcipher")
        helper = object : SQLiteOpenHelper(context, "myehr.db", KeyManager.passphrase(context), null, 2, 0, null, null, false) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE person (id TEXT PRIMARY KEY, name TEXT NOT NULL, is_child INTEGER NOT NULL, last_sync INTEGER)")
                db.execSQL("CREATE TABLE raw (person TEXT NOT NULL, kind TEXT NOT NULL, k TEXT NOT NULL, json TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY(person, kind, k))")
                onUpgrade(db, 1, 2)
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion < 2) db.execSQL("CREATE TABLE ai_log (at INTEGER NOT NULL, person TEXT NOT NULL, question TEXT NOT NULL, sent TEXT NOT NULL, answer TEXT NOT NULL)")
            }
        }
    }

    private val db get() = helper.writableDatabase

    override fun putPerson(p: Person) {
        db.insertWithOnConflict("person", null, ContentValues().apply {
            put("id", p.id); put("name", p.name); put("is_child", if (p.isChild) 1 else 0); put("last_sync", p.lastSync)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun people(): List<Person> = db.rawQuery("SELECT id, name, is_child, last_sync FROM person ORDER BY is_child, name", null).use { c ->
        buildList {
            while (c.moveToNext()) add(Person(c.getString(0), c.getString(1), c.getInt(2) == 1, if (c.isNull(3)) null else c.getLong(3)))
        }
    }

    override fun put(person: String, kind: String, key: String, json: String) {
        db.insertWithOnConflict("raw", null, ContentValues().apply {
            put("person", person); put("kind", kind); put("k", key); put("json", json); put("at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun get(person: String, kind: String, key: String = ""): String? =
        db.rawQuery("SELECT json FROM raw WHERE person=? AND kind=? AND k=?", arrayOf(person, kind, key)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    fun all(person: String, kind: String): Map<String, String> =
        db.rawQuery("SELECT k, json FROM raw WHERE person=? AND kind=?", arrayOf(person, kind)).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }

    fun logAi(person: String, question: String, sent: String, answer: String) {
        db.insert("ai_log", null, ContentValues().apply {
            put("at", System.currentTimeMillis()); put("person", person); put("question", question); put("sent", sent); put("answer", answer)
        })
    }

    /** (time, question, what was sent, answer), newest first. */
    fun aiLog(person: String): List<List<String>> =
        db.rawQuery("SELECT at, question, sent, answer FROM ai_log WHERE person=? ORDER BY at DESC LIMIT 100", arrayOf(person)).use { c ->
            buildList { while (c.moveToNext()) add(listOf(c.getLong(0).toString(), c.getString(1), c.getString(2), c.getString(3))) }
        }

    fun deleteAll() {
        db.execSQL("DELETE FROM raw"); db.execSQL("DELETE FROM person")
    }

    companion object {
        @Volatile private var instance: Store? = null
        fun get(context: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(context.applicationContext).also { instance = it }
        }
    }
}
