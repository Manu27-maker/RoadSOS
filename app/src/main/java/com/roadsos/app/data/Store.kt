package com.roadsos.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Small SharedPreferences store: device id, reporter profile and contacts.
 * Incidents live in [com.roadsos.app.engine.LocalDb]; nothing here leaves the phone.
 */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("roadsos", Context.MODE_PRIVATE)
    private val gson = Gson()

    val deviceId: String
        get() {
            val existing = prefs.getString(KEY_DEVICE_ID, null)
            if (!existing.isNullOrBlank()) return existing
            val created = "AND-" + UUID.randomUUID().toString().take(12).uppercase()
            prefs.edit().putString(KEY_DEVICE_ID, created).apply()
            return created
        }

    var reporterName: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var reporterPhone: String
        get() = prefs.getString(KEY_PHONE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PHONE, value).apply()

    /** Automatic by default: online when the phone has internet, offline otherwise. */
    var onlineRouting: Boolean
        get() = prefs.getBoolean(KEY_ONLINE_ROUTING, true)
        set(value) = prefs.edit().putBoolean(KEY_ONLINE_ROUTING, value).apply()

    fun loadContacts(): List<Contact> {
        val raw = prefs.getString(KEY_CONTACTS, null) ?: run {
            val seeded = demoContacts()
            saveContacts(seeded)
            return seeded
        }
        return runCatching {
            val type = object : TypeToken<List<Contact>>() {}.type
            gson.fromJson<List<Contact>>(raw, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun saveContacts(contacts: List<Contact>) {
        prefs.edit().putString(KEY_CONTACTS, gson.toJson(contacts.take(MAX_CONTACTS))).apply()
    }

    fun resetContacts(): List<Contact> = demoContacts().also(::saveContacts)

    companion object {
        const val MAX_CONTACTS = 5
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_NAME = "reporter_name"
        private const val KEY_PHONE = "reporter_phone"
        private const val KEY_CONTACTS = "contacts"
        private const val KEY_ONLINE_ROUTING = "online_routing"
    }
}
