package com.roadsos.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadsos.app.MainViewModel
import com.roadsos.app.data.CHANNELS
import com.roadsos.app.data.Contact
import com.roadsos.app.data.RELATIONSHIPS
import com.roadsos.app.data.Store
import com.roadsos.app.data.errorMessage
import com.roadsos.app.ui.EmptyState
import com.roadsos.app.ui.ErrorBanner
import com.roadsos.app.ui.PanelCard
import com.roadsos.app.ui.PrimaryAction
import com.roadsos.app.ui.SecondaryAction
import com.roadsos.app.ui.SectionLabel
import com.roadsos.app.ui.theme.Amber
import com.roadsos.app.ui.theme.Cyan
import com.roadsos.app.ui.theme.Green
import com.roadsos.app.ui.theme.Panel
import com.roadsos.app.ui.theme.PanelRaised
import com.roadsos.app.ui.theme.Red
import com.roadsos.app.ui.theme.Stroke
import com.roadsos.app.ui.theme.TextDim
import com.roadsos.app.ui.theme.TextMain
import com.roadsos.app.ui.theme.TextMuted
import kotlinx.coroutines.launch

private val CHANNEL_COLORS = mapOf(
    "sms" to Cyan,
    "whatsapp" to Green,
    "email" to Amber,
)

@Composable
fun ContactsScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()

    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Contact?>(null) }
    var deleting by remember { mutableStateOf<Contact?>(null) }
    var sendingTest by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun openAdd() {
        if (vm.contacts.size >= Store.MAX_CONTACTS) {
            scope.launch { snackbar.showSnackbar("You can save up to ${Store.MAX_CONTACTS} contacts.") }
            return
        }
        editing = null
        showDialog = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("On-device contacts", accent = Cyan)
                Spacer(Modifier.height(6.dp))
                Text("Emergency contacts", color = TextMain, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Alerted automatically when you file a report",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = Cyan.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, Cyan.copy(alpha = 0.3f)),
            ) {
                Text(
                    "${vm.contacts.size}/${Store.MAX_CONTACTS}",
                    color = Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        PanelCard(accent = Red) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Red.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Red, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("Family first, then a friend", color = TextMain, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Saved on this device only. SMS, WhatsApp and email are labels on a mock delivery, not sends.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel("Saved contacts")
        Spacer(Modifier.height(8.dp))

        if (vm.contacts.isEmpty()) {
            PanelCard {
                EmptyState(
                    title = "No contacts yet",
                    body = "Add someone who should hear from you in an emergency.",
                    icon = Icons.Filled.Person,
                    accent = Cyan,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.contacts.forEach { contact ->
                ContactRow(
                    contact = contact,
                    onEdit = {
                        editing = contact
                        showDialog = true
                    },
                    onDelete = { deleting = contact },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        PrimaryAction(
            text = if (vm.contacts.size >= Store.MAX_CONTACTS) "Contact limit reached (${Store.MAX_CONTACTS})" else "Add emergency contact",
            onClick = ::openAdd,
            enabled = vm.contacts.size < Store.MAX_CONTACTS,
            color = Cyan,
            icon = Icons.Filled.Add,
        )

        Spacer(Modifier.height(10.dp))
        SecondaryAction(
            text = if (sendingTest) "Sending test alert..." else "Send test alert to all contacts",
            onClick = {
                scope.launch {
                    sendingTest = true
                    error = null
                    vm.sendTestAlert()
                        .onSuccess { summary ->
                            snackbar.showSnackbar(
                                "Test alert queued: ${summary.sent ?: 0}/${summary.total ?: 0} contacts" +
                                    if (summary.mocked == true) " (mock delivery)" else "",
                            )
                        }
                        .onFailure {
                            error = errorMessage(it)
                            snackbar.showSnackbar(errorMessage(it))
                        }
                    sendingTest = false
                }
            },
            enabled = vm.contacts.isNotEmpty() && !sendingTest,
            contentColor = Cyan,
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            ErrorBanner(error!!)
        }

        Spacer(Modifier.height(14.dp))
        PanelCard {
            Column {
                SectionLabel("How delivery works")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Each contact gets their own rendered message with the incident location, the fused severity " +
                        "and their channel label. This hackathon build only queues that message on the phone: " +
                        "no SMS, WhatsApp, email or push is sent.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "For a real emergency always call 112 directly.",
                    color = Amber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showDialog) {
        ContactDialog(
            initial = editing,
            onDismiss = {
                showDialog = false
                editing = null
            },
            onSave = { contact ->
                val message = if (editing == null) vm.addContact(contact) else {
                    vm.updateContact(contact)
                    null
                }
                if (message != null) {
                    error = message
                    scope.launch { snackbar.showSnackbar(message) }
                } else {
                    error = null
                    scope.launch {
                        snackbar.showSnackbar(
                            if (editing == null) "Contact saved." else "Contact updated.",
                        )
                    }
                }
                showDialog = false
                editing = null
            },
        )
    }

    deleting?.let { contact ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove ${contact.name}?", color = TextMain) },
            text = {
                Text(
                    "They will no longer be alerted when you file a report.",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteContact(contact.id)
                    deleting = null
                    scope.launch { snackbar.showSnackbar("${contact.name} removed.") }
                }) {
                    Text("Remove", color = Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = PanelRaised,
            shape = RoundedCornerShape(18.dp),
        )
    }
}

@Composable
private fun ContactRow(contact: Contact, onEdit: () -> Unit, onDelete: () -> Unit) {
    val channelColor = CHANNEL_COLORS[contact.channel] ?: Cyan
    PanelCard(accent = channelColor) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                channelColor.copy(alpha = 0.26f),
                                channelColor.copy(alpha = 0.06f),
                            ),
                        ),
                    )
                    .border(1.dp, channelColor.copy(alpha = 0.32f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    contact.initials(),
                    color = channelColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp,
                )
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contact.name,
                        color = TextMain,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    ChannelChip(contact.channel, channelColor)
                }
                Text(
                    "${contact.relationship} · ${contact.phone}",
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Edit ${contact.name}",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove ${contact.name}",
                    tint = Red.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ChannelChip(channel: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            channel.uppercase(),
            color = color,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactDialog(
    initial: Contact?,
    onDismiss: () -> Unit,
    onSave: (Contact) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var relationship by remember { mutableStateOf(initial?.relationship ?: RELATIONSHIPS.first()) }
    var channel by remember { mutableStateOf(initial?.channel ?: CHANNELS.first()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        val cleanName = name.trim()
        val cleanPhone = phone.trim()
        if (cleanName.length < 2) {
            error = "Enter a name with at least 2 characters."
            return
        }
        if (cleanPhone.length < 6) {
            error = "Enter a phone number or email address."
            return
        }
        if (channel == "email" && !cleanPhone.contains("@")) {
            error = "An email contact needs an email address."
            return
        }
        if (channel != "email" && cleanPhone.count { it.isDigit() } < 6) {
            error = "Enter a valid phone number."
            return
        }
        onSave(
            Contact(
                id = initial?.id ?: "",
                name = cleanName,
                phone = cleanPhone,
                relationship = relationship,
                channel = channel,
            ),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initial == null) "Add contact" else "Edit contact", color = TextMain)
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("Name", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = dialogFieldColors(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.take(80) },
                    label = { Text(if (channel == "email") "Email" else "Phone", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (channel == "email") KeyboardType.Email else KeyboardType.Phone,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = dialogFieldColors(),
                )
                DialogChipRow(
                    label = "Relationship",
                    options = RELATIONSHIPS,
                    selected = relationship,
                    onSelect = { relationship = it },
                )
                DialogChipRow(
                    label = "Channel",
                    options = CHANNELS,
                    selected = channel,
                    onSelect = { channel = it },
                    display = { it.replaceFirstChar { c -> c.uppercase() } },
                )
                if (error != null) {
                    Text(error!!, color = Red, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::save) {
                Text(if (initial == null) "Save contact" else "Update", color = Cyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
        containerColor = PanelRaised,
        shape = RoundedCornerShape(18.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogChipRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    display: (String) -> String = { it },
) {
    Column {
        Text(label, color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                val on = option == selected
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onSelect(option) },
                    shape = RoundedCornerShape(50),
                    color = if (on) Cyan.copy(alpha = 0.16f) else Color.Transparent,
                    border = BorderStroke(
                        1.dp,
                        if (on) Cyan.copy(alpha = 0.5f) else Stroke,
                    ),
                ) {
                    Text(
                        display(option),
                        color = if (on) Cyan else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cyan,
    unfocusedBorderColor = Stroke,
    focusedContainerColor = Panel,
    unfocusedContainerColor = Panel,
    focusedLabelColor = Cyan,
    cursorColor = Cyan,
)
