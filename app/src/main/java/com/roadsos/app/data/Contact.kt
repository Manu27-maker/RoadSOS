package com.roadsos.app.data

import androidx.compose.runtime.Immutable

@Immutable
data class Contact(
    val id: String,
    val name: String,
    val phone: String,
    val relationship: String = "Friend",
    val channel: String = "sms",
) {
    fun payload(): ContactPayload = ContactPayload(name, phone, channel, relationship)

    fun initials(): String {
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> (parts[0].take(1) + parts.last().take(1)).uppercase()
        }
    }
}

val CHANNELS = listOf("sms", "whatsapp", "email")
val RELATIONSHIPS = listOf("Sister", "Brother", "Mother", "Father", "Spouse", "Friend", "Colleague")

fun demoContacts(): List<Contact> = listOf(
    Contact("CON-DEMO-1", "Ananya Rao", "+91 98450 11223", "Sister", "whatsapp"),
    Contact("CON-DEMO-2", "Vikram Shetty", "+91 99001 44556", "Friend", "sms"),
    Contact("CON-DEMO-3", "Meera Krishnan", "+91 98860 77889", "Mother", "sms"),
)
