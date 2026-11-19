package com.example.data

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.BlockedNumberContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import com.example.model.AppThemeMode
import com.example.model.BlockedNumberEntry
import com.example.model.CallDirection
import com.example.model.CallLogItem
import com.example.model.ContactFormData
import com.example.model.ContactGroupItem
import com.example.model.ContactItem
import com.example.model.DeletedContactEntry
import com.example.model.DuplicateContactGroup
import com.example.model.PhoneNumberEntry
import com.example.model.RingtoneOption
import com.example.model.SimAccountInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Locale
import java.util.UUID

class DialerRepository(private val context: Context) {

    private val contentResolver = context.contentResolver
    private val prefs = context.getSharedPreferences("color_dialer_prefs", Context.MODE_PRIVATE)

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun formatPhoneNumber(raw: String): String {
        if (raw.isBlank()) return ""
        val countryIso = Locale.getDefault().country.ifBlank { "US" }
        return try {
            PhoneNumberUtils.formatNumber(raw, countryIso) ?: raw
        } catch (_: Exception) {
            raw
        }
    }

    fun normalizePhoneKey(raw: String): String {
        val digits = raw.filter { it.isDigit() || it == '+' }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    /**
     * Loads SIM 1 only as required by the ColorOS 17 specification ("SIM 1 only. Do not display SIM 2 selection.").
     */
    suspend fun loadSimAccounts(): List<SimAccountInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SimAccountInfo>()
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            return@withContext result
        }
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                ?: return@withContext result
            val firstHandle = telecomManager.callCapablePhoneAccounts.firstOrNull()
            if (firstHandle != null) {
                val account = telecomManager.getPhoneAccount(firstHandle)
                val label = account?.label?.toString()?.takeIf { it.isNotBlank() } ?: "SIM 1"
                val shortDesc = account?.shortDescription?.toString()?.takeIf { it.isNotBlank() } ?: label
                val isHd = (account?.capabilities ?: 0) and PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION != 0
                result.add(
                    SimAccountInfo(
                        handle = firstHandle,
                        label = label,
                        slotIndex = 1,
                        carrierName = shortDesc,
                        isHdCapable = isHd
                    )
                )
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        result
    }

    suspend fun loadGroups(): List<ContactGroupItem> = withContext(Dispatchers.IO) {
        emptyList()
    }

    suspend fun createContactGroup(title: String): Long? = withContext(Dispatchers.IO) {
        null
    }

    private fun getHiddenCallLogIds(): Set<Long> {
        val raw = prefs.getStringSet("hidden_call_log_ids", emptySet()).orEmpty()
        return raw.mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun addHiddenCallLogId(id: Long) {
        val current = prefs.getStringSet("hidden_call_log_ids", emptySet()).orEmpty().toMutableSet()
        current.add(id.toString())
        prefs.edit().putStringSet("hidden_call_log_ids", current).apply()
    }

    suspend fun loadCallLogs(
        contactsByPhoneKey: Map<String, ContactItem> = emptyMap(),
        blockedPhoneKeys: Set<String> = emptySet()
    ): List<CallLogItem> = withContext(Dispatchers.IO) {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) return@withContext emptyList()
        val list = mutableListOf<CallLogItem>()
        val hiddenIds = getHiddenCallLogIds()
        val clearedBeforeTimestamp = prefs.getLong("call_logs_cleared_timestamp", 0L)

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_FORMATTED_NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE,
            CallLog.Calls.FEATURES,
            CallLog.Calls.GEOCODED_LOCATION,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )

        try {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
                val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val formattedIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_FORMATTED_NUMBER)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val featuresIdx = cursor.getColumnIndex(CallLog.Calls.FEATURES)
                val locationIdx = cursor.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)
                val accountIdx = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                var count = 0
                while (cursor.moveToNext() && count < 400) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else continue
                    val timestamp = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    if (hiddenIds.contains(id) || timestamp <= clearedBeforeTimestamp) continue

                    count++
                    val rawNumber = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty() else ""
                    val cachedName = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val cachedFormatted = if (formattedIdx >= 0) cursor.getString(formattedIdx) else null
                    val duration = if (durationIdx >= 0) cursor.getLong(durationIdx) else 0L
                    val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
                    val features = if (featuresIdx >= 0) cursor.getInt(featuresIdx) else 0
                    val geocoded = if (locationIdx >= 0) cursor.getString(locationIdx) else null
                    val accountId = if (accountIdx >= 0) cursor.getString(accountIdx) else null

                    val phoneKey = normalizePhoneKey(rawNumber)
                    val matchedContact = if (phoneKey.isNotEmpty()) contactsByPhoneKey[phoneKey] else null
                    val resolvedName = matchedContact?.displayName?.takeIf { it.isNotBlank() }
                        ?: cachedName?.takeIf { it.isNotBlank() }

                    val resolvedLocation = geocoded?.takeIf { it.isNotBlank() }
                        ?: matchedContact?.address?.takeIf { it.isNotBlank() }

                    val direction = when (type) {
                        CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallDirection.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallDirection.REJECTED
                        CallLog.Calls.BLOCKED_TYPE -> CallDirection.BLOCKED
                        else -> CallDirection.INCOMING
                    }

                    val isHd = (features and CallLog.Calls.FEATURES_HD_CALL) != 0 ||
                        (features and CallLog.Calls.FEATURES_VOLTE) != 0 ||
                        (features and CallLog.Calls.FEATURES_WIFI) != 0

                    val formatted = cachedFormatted?.takeIf { it.isNotBlank() }
                        ?: formatPhoneNumber(rawNumber)

                    val isBlocked = direction == CallDirection.BLOCKED ||
                        (phoneKey.isNotEmpty() && blockedPhoneKeys.contains(phoneKey))

                    list.add(
                        CallLogItem(
                            id = id,
                            number = rawNumber,
                            formattedNumber = formatted,
                            cachedName = resolvedName,
                            contactId = matchedContact?.id,
                            timestamp = timestamp,
                            durationSeconds = duration,
                            direction = direction,
                            isHd = isHd,
                            geocodedLocation = resolvedLocation,
                            simLabel = "SIM 1",
                            phoneAccountId = accountId,
                            photoUri = matchedContact?.photoUri,
                            isBlockedOrSpam = isBlocked
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        list
    }

    fun enrichCallLogsWithContacts(
        rawCallLogs: List<CallLogItem>,
        contactsByPhoneKey: Map<String, ContactItem>
    ): List<CallLogItem> {
        if (rawCallLogs.isEmpty() || contactsByPhoneKey.isEmpty()) return rawCallLogs
        return rawCallLogs.map { item ->
            val phoneKey = normalizePhoneKey(item.number)
            val matchedContact = if (phoneKey.isNotEmpty()) contactsByPhoneKey[phoneKey] else null
            if (matchedContact == null) {
                item
            } else {
                val resolvedName = matchedContact.displayName.takeIf { it.isNotBlank() }
                    ?: item.cachedName
                val resolvedLocation = item.geocodedLocation?.takeIf { it.isNotBlank() }
                    ?: matchedContact.address.takeIf { it.isNotBlank() }
                item.copy(
                    cachedName = resolvedName,
                    contactId = matchedContact.id,
                    geocodedLocation = resolvedLocation,
                    photoUri = matchedContact.photoUri
                )
            }
        }
    }

    suspend fun deleteCallLogItem(callLogId: Long): Boolean = withContext(Dispatchers.IO) {
        addHiddenCallLogId(callLogId)
        if (!hasPermission(Manifest.permission.WRITE_CALL_LOG)) return@withContext true
        try {
            contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} = ?",
                arrayOf(callLogId.toString())
            )
            true
        } catch (_: Exception) {
            true
        }
    }

    suspend fun clearAllCallHistory(): Boolean = withContext(Dispatchers.IO) {
        prefs.edit().putLong("call_logs_cleared_timestamp", System.currentTimeMillis()).apply()
        if (hasPermission(Manifest.permission.WRITE_CALL_LOG)) {
            try {
                contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
            } catch (_: Exception) {
            }
        }
        true
    }

    suspend fun loadContacts(
        recentNumbersOrdered: List<String> = emptyList(),
        callFrequencyByPhoneKey: Map<String, Int> = emptyMap()
    ): List<ContactItem> = withContext(Dispatchers.IO) {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return@withContext emptyList()

        val recentKeyIndexMap = HashMap<String, Int>(recentNumbersOrdered.size)
        for (i in recentNumbersOrdered.indices) {
            val key = normalizePhoneKey(recentNumbersOrdered[i])
            if (key.isNotEmpty() && !recentKeyIndexMap.containsKey(key)) {
                recentKeyIndexMap[key] = i
            }
        }

        data class MutableContact(
            val id: Long,
            var rawContactId: Long? = null,
            val lookupKey: String?,
            var displayName: String,
            var phoneticName: String = "",
            val photoUri: String?,
            val isStarred: Boolean,
            val lastUpdatedTimestamp: Long = 0L,
            var company: String = "",
            var jobTitle: String = "",
            val phones: MutableList<PhoneNumberEntry> = mutableListOf(),
            val emails: MutableList<String> = mutableListOf(),
            var address: String = "",
            var regionLocation: String? = null,
            var notes: String = ""
        )

        val contactsMap = LinkedHashMap<Long, MutableContact>()

        try {
            val contactProjection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.STARRED,
                ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP
            )

            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                contactProjection,
                null,
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val lookupIdx = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val photoIdx = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                val starredIdx = cursor.getColumnIndex(ContactsContract.Contacts.STARRED)
                val updatedIdx = cursor.getColumnIndex(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else continue
                    val lookup = if (lookupIdx >= 0) cursor.getString(lookupIdx) else null
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                    val photo = if (photoIdx >= 0) cursor.getString(photoIdx) else null
                    val starred = if (starredIdx >= 0) cursor.getInt(starredIdx) == 1 else false
                    val updated = if (updatedIdx >= 0) cursor.getLong(updatedIdx) else id

                    contactsMap[id] = MutableContact(
                        id = id,
                        lookupKey = lookup,
                        displayName = name.ifBlank { "Unnamed" },
                        photoUri = photo,
                        isStarred = starred,
                        lastUpdatedTimestamp = updated
                    )
                }
            }

            if (contactsMap.isEmpty()) return@withContext emptyList()

            val dataProjection = arrayOf(
                ContactsContract.Data._ID,
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.RAW_CONTACT_ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA2,
                ContactsContract.Data.DATA3,
                ContactsContract.Data.DATA4,
                ContactsContract.Data.DATA7,
                ContactsContract.Data.DATA8,
                ContactsContract.Data.DATA9,
                ContactsContract.Data.IS_SUPER_PRIMARY,
                ContactsContract.Data.IS_PRIMARY
            )

            contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                dataProjection,
                null,
                null,
                null
            )?.use { cursor ->
                val dataIdIdx = cursor.getColumnIndex(ContactsContract.Data._ID)
                val contactIdIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                val rawIdIdx = cursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID)
                val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val d1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                val d2Idx = cursor.getColumnIndex(ContactsContract.Data.DATA2)
                val d3Idx = cursor.getColumnIndex(ContactsContract.Data.DATA3)
                val d4Idx = cursor.getColumnIndex(ContactsContract.Data.DATA4)
                val d7Idx = cursor.getColumnIndex(ContactsContract.Data.DATA7)
                val d8Idx = cursor.getColumnIndex(ContactsContract.Data.DATA8)
                val d9Idx = cursor.getColumnIndex(ContactsContract.Data.DATA9)
                val superPrimaryIdx = cursor.getColumnIndex(ContactsContract.Data.IS_SUPER_PRIMARY)
                val primaryIdx = cursor.getColumnIndex(ContactsContract.Data.IS_PRIMARY)

                while (cursor.moveToNext()) {
                    val contactId = if (contactIdIdx >= 0) cursor.getLong(contactIdIdx) else continue
                    val contact = contactsMap[contactId] ?: continue
                    val rawId = if (rawIdIdx >= 0) cursor.getLong(rawIdIdx) else null
                    if (contact.rawContactId == null && rawId != null) {
                        contact.rawContactId = rawId
                    }
                    val dataId = if (dataIdIdx >= 0) cursor.getLong(dataIdIdx) else 0L
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx).orEmpty() else ""
                    val d1 = if (d1Idx >= 0) cursor.getString(d1Idx).orEmpty() else ""
                    val d2 = if (d2Idx >= 0) cursor.getString(d2Idx).orEmpty() else ""
                    val d3 = if (d3Idx >= 0) cursor.getString(d3Idx).orEmpty() else ""
                    val d4 = if (d4Idx >= 0) cursor.getString(d4Idx).orEmpty() else ""
                    val d7 = if (d7Idx >= 0) cursor.getString(d7Idx).orEmpty() else ""
                    val d8 = if (d8Idx >= 0) cursor.getString(d8Idx).orEmpty() else ""
                    val d9 = if (d9Idx >= 0) cursor.getString(d9Idx).orEmpty() else ""
                    val isSuperPrimary = if (superPrimaryIdx >= 0) cursor.getInt(superPrimaryIdx) > 0 else false
                    val isPrimary = if (primaryIdx >= 0) cursor.getInt(primaryIdx) > 0 else false

                    when (mime) {
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            if (d1.isNotBlank()) contact.displayName = d1.trim()
                            val phonetic = listOf(d9, d8, d7).filter { it.isNotBlank() }.joinToString(" ").trim()
                            if (phonetic.isNotBlank()) {
                                contact.phoneticName = phonetic
                            }
                        }
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            val rawNum = d1.trim()
                            if (rawNum.isNotBlank()) {
                                val normalizedKey = normalizePhoneKey(rawNum)
                                val alreadyAdded = contact.phones.any {
                                    normalizePhoneKey(it.number) == normalizedKey && normalizedKey.isNotEmpty()
                                }
                                if (!alreadyAdded) {
                                    val typeInt = d2.toIntOrNull() ?: ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                                    val typeLabel = when (typeInt) {
                                        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                                        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Main"
                                        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> d3.ifBlank { "Custom" }
                                        else -> "Mobile"
                                    }
                                    contact.phones.add(
                                        PhoneNumberEntry(
                                            dataId = dataId,
                                            number = formatPhoneNumber(rawNum),
                                            normalizedNumber = d4.ifBlank { rawNum },
                                            typeLabel = typeLabel,
                                            isDefault = isSuperPrimary || isPrimary,
                                            isRecent = false,
                                            location = null
                                        )
                                    )
                                }
                            }
                        }
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                            if (d1.isNotBlank() && !contact.emails.contains(d1.trim())) {
                                contact.emails.add(d1.trim())
                            }
                        }
                        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                            if (d1.isNotBlank()) contact.company = d1.trim()
                            if (d4.isNotBlank()) contact.jobTitle = d4.trim()
                        }
                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                            if (d1.isNotBlank()) contact.address = d1.trim()
                            val regionOrCity = d8.ifBlank { d7 }.ifBlank { d1 }.trim()
                            if (regionOrCity.isNotBlank()) {
                                contact.regionLocation = regionOrCity
                            }
                        }
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                            if (d1.isNotBlank()) contact.notes = d1.trim()
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }

        contactsMap.values.map { mc ->
            val hasExplicitDefault = mc.phones.any { it.isDefault }
            var bestRecentIdx = Int.MAX_VALUE
            var mostRecentKey: String? = null
            val phoneKeys = Array(mc.phones.size) { idx ->
                val k = normalizePhoneKey(mc.phones[idx].number)
                if (k.isNotEmpty()) {
                    val rIdx = recentKeyIndexMap[k]
                    if (rIdx != null && rIdx < bestRecentIdx) {
                        bestRecentIdx = rIdx
                        mostRecentKey = k
                    }
                }
                k
            }

            var totalTimesContacted = 0
            val enrichedPhones = mc.phones.mapIndexed { idx, entry ->
                val key = phoneKeys[idx]
                totalTimesContacted += callFrequencyByPhoneKey[key] ?: 0
                entry.copy(
                    isDefault = if (hasExplicitDefault) entry.isDefault else (idx == 0 && mc.phones.isNotEmpty()),
                    isRecent = mostRecentKey != null && key == mostRecentKey,
                    location = entry.location ?: mc.regionLocation
                )
            }

            ContactItem(
                id = mc.id,
                rawContactId = mc.rawContactId,
                lookupKey = mc.lookupKey,
                displayName = mc.displayName,
                phoneticName = mc.phoneticName,
                photoUri = mc.photoUri,
                isStarred = mc.isStarred,
                company = mc.company,
                jobTitle = mc.jobTitle,
                phones = enrichedPhones,
                emails = mc.emails,
                address = mc.address,
                notes = mc.notes,
                lastUpdatedTimestamp = mc.lastUpdatedTimestamp,
                timesContacted = totalTimesContacted
            )
        }
    }

    suspend fun saveContact(form: ContactFormData): Result<Long> = withContext(Dispatchers.IO) {
        if (!hasPermission(Manifest.permission.WRITE_CONTACTS)) {
            return@withContext Result.failure(SecurityException("WRITE_CONTACTS permission is required to save contacts."))
        }
        val cleanName = form.name.trim()
        val cleanNumber = form.number.trim()
        if (cleanName.isEmpty() && cleanNumber.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter at least a name or phone number."))
        }

        try {
            val ops = ArrayList<ContentProviderOperation>()

            if (form.contactId != null) {
                val rawId = form.rawContactId ?: findRawContactId(form.contactId)
                    ?: return@withContext Result.failure(IllegalStateException("Could not find raw contact record to update."))

                val editableMimes = mutableListOf(
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
                )
                if (form.photoBytes != null) {
                    editableMimes.add(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                }
                for (mime in editableMimes) {
                    ops.add(
                        ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                            .withSelection(
                                "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                                arrayOf(rawId.toString(), mime)
                            )
                            .build()
                    )
                }

                appendContactDataOperations(
                    ops = ops,
                    rawContactId = rawId,
                    rawContactBackRef = null,
                    form = form
                )
                contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                com.example.telecom.CallStateManager.invalidateCallerCache()
                return@withContext Result.success(form.contactId)
            } else {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                        .build()
                )

                appendContactDataOperations(
                    ops = ops,
                    rawContactId = null,
                    rawContactBackRef = 0,
                    form = form
                )

                val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                com.example.telecom.CallStateManager.invalidateCallerCache()
                val rawContactUri = results.firstOrNull()?.uri
                val newContactId = if (rawContactUri != null) {
                    val rawId = ContentUris.parseId(rawContactUri)
                    queryContactIdForRawContact(rawId) ?: rawId
                } else {
                    0L
                }
                return@withContext Result.success(newContactId)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    private fun appendContactDataOperations(
        ops: ArrayList<ContentProviderOperation>,
        rawContactId: Long?,
        rawContactBackRef: Int?,
        form: ContactFormData
    ) {
        fun newDataInsert(): ContentProviderOperation.Builder {
            val builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            if (rawContactId != null) {
                builder.withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            } else if (rawContactBackRef != null) {
                builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactBackRef)
            }
            return builder
        }

        if (form.name.isNotBlank() || form.phoneticName.isNotBlank()) {
            ops.add(
                newDataInsert()
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                        form.name.trim().ifBlank { form.number.trim() }
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredName.PHONETIC_GIVEN_NAME,
                        form.phoneticName.trim()
                    )
                    .build()
            )
        }

        if (form.number.isNotBlank()) {
            ops.add(
                newDataInsert()
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, form.number.trim())
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    )
                    .withValue(ContactsContract.Data.IS_PRIMARY, 1)
                    .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
                    .build()
            )
        }

        if (form.secondaryNumber.isNotBlank()) {
            ops.add(
                newDataInsert()
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, form.secondaryNumber.trim())
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK
                    )
                    .build()
            )
        }

        if (form.email.isNotBlank()) {
            ops.add(
                newDataInsert()
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, form.email.trim())
                    .withValue(
                        ContactsContract.CommonDataKinds.Email.TYPE,
                        ContactsContract.CommonDataKinds.Email.TYPE_MOBILE
                    )
                    .build()
            )
        }

        if (form.company.isNotBlank() || form.jobTitle.isNotBlank()) {
            ops.add(
                newDataInsert()
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, form.company.trim())
                    .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, form.jobTitle.trim())
                    .withValue(
                        ContactsContract.CommonDataKinds.Organization.TYPE,
                        ContactsContract.CommonDataKinds.Organization.TYPE_WORK
                    )
                    .build()
            )
        }

        if (form.address.isNotBlank()) {
            ops.add(
                newDataInsert()
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
                        form.address.trim()
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredPostal.REGION,
                        form.address.trim()
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME
                    )
                    .build()
            )
        }

        if (form.notes.isNotBlank()) {
            ops.add(
                newDataInsert()
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, form.notes.trim())
                    .build()
            )
        }

        if (form.photoBytes != null && form.photoBytes.isNotEmpty()) {
            ops.add(
                newDataInsert()
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.Photo.PHOTO,
                        form.photoBytes
                    )
                    .build()
            )
        }
    }

    private fun findRawContactId(contactId: Long): Long? {
        contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return null
    }

    private fun queryContactIdForRawContact(rawContactId: Long): Long? {
        contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.CONTACT_ID),
            "${ContactsContract.RawContacts._ID} = ?",
            arrayOf(rawContactId.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return null
    }

    suspend fun toggleFavorite(contactId: Long, isFavorite: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasPermission(Manifest.permission.WRITE_CONTACTS)) return@withContext false
            try {
                val values = ContentValues().apply {
                    put(ContactsContract.Contacts.STARRED, if (isFavorite) 1 else 0)
                }
                val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
                contentResolver.update(uri, values, null, null) > 0
            } catch (_: Exception) {
                false
            }
        }

    suspend fun setDefaultNumber(contactId: Long, phoneDataId: Long): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasPermission(Manifest.permission.WRITE_CONTACTS) || phoneDataId <= 0L) return@withContext false
            try {
                val ops = ArrayList<ContentProviderOperation>()
                ops.add(
                    ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                            arrayOf(
                                contactId.toString(),
                                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                            )
                        )
                        .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 0)
                        .withValue(ContactsContract.Data.IS_PRIMARY, 0)
                        .build()
                )
                ops.add(
                    ContentProviderOperation.newUpdate(
                        ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, phoneDataId)
                    )
                        .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
                        .withValue(ContactsContract.Data.IS_PRIMARY, 1)
                        .build()
                )
                contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                true
            } catch (_: Exception) {
                false
            }
        }

    /**
     * Deletes a contact from Android ContactsContract and stores it in the persistent Recycle Bin
     * so deleted contacts remain until manually removed.
     */
    suspend fun moveContactToRecycleBin(contact: ContactItem): Boolean = withContext(Dispatchers.IO) {
        if (!hasPermission(Manifest.permission.WRITE_CONTACTS)) return@withContext false
        try {
            val rows = contentResolver.delete(
                ContactsContract.RawContacts.CONTENT_URI,
                "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                arrayOf(contact.id.toString())
            )
            if (rows > 0) {
                val entry = DeletedContactEntry(
                    deletedId = UUID.randomUUID().toString(),
                    deletedTimestamp = System.currentTimeMillis(),
                    displayName = contact.displayName,
                    primaryNumber = contact.phones.firstOrNull()?.number.orEmpty(),
                    secondaryNumber = contact.phones.getOrNull(1)?.number.orEmpty(),
                    phoneticName = contact.phoneticName,
                    email = contact.emails.firstOrNull().orEmpty(),
                    company = contact.company,
                    jobTitle = contact.jobTitle,
                    address = contact.address,
                    notes = contact.notes
                )
                val current = loadRecycleBinContacts().toMutableList()
                current.add(0, entry)
                saveRecycleBinEntries(current)
                return@withContext true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun loadRecycleBinContacts(): List<DeletedContactEntry> {
        val rawJson = prefs.getString("recycle_bin_contacts_json", null) ?: return emptyList()
        val result = mutableListOf<DeletedContactEntry>()
        try {
            val arr = JSONArray(rawJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    DeletedContactEntry(
                        deletedId = obj.optString("deletedId"),
                        deletedTimestamp = obj.optLong("deletedTimestamp"),
                        displayName = obj.optString("displayName"),
                        primaryNumber = obj.optString("primaryNumber"),
                        secondaryNumber = obj.optString("secondaryNumber"),
                        phoneticName = obj.optString("phoneticName"),
                        email = obj.optString("email"),
                        company = obj.optString("company"),
                        jobTitle = obj.optString("jobTitle"),
                        address = obj.optString("address"),
                        notes = obj.optString("notes")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun saveRecycleBinEntries(entries: List<DeletedContactEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            val obj = JSONObject().apply {
                put("deletedId", e.deletedId)
                put("deletedTimestamp", e.deletedTimestamp)
                put("displayName", e.displayName)
                put("primaryNumber", e.primaryNumber)
                put("secondaryNumber", e.secondaryNumber)
                put("phoneticName", e.phoneticName)
                put("email", e.email)
                put("company", e.company)
                put("jobTitle", e.jobTitle)
                put("address", e.address)
                put("notes", e.notes)
            }
            arr.put(obj)
        }
        prefs.edit().putString("recycle_bin_contacts_json", arr.toString()).apply()
    }

    suspend fun restoreFromRecycleBin(entry: DeletedContactEntry): Result<Long> {
        val form = ContactFormData(
            name = entry.displayName,
            phoneticName = entry.phoneticName,
            number = entry.primaryNumber,
            secondaryNumber = entry.secondaryNumber,
            email = entry.email,
            company = entry.company,
            jobTitle = entry.jobTitle,
            address = entry.address,
            notes = entry.notes
        )
        val res = saveContact(form)
        if (res.isSuccess) {
            permanentlyDeleteFromRecycleBin(entry.deletedId)
        }
        return res
    }

    fun permanentlyDeleteFromRecycleBin(deletedId: String) {
        val updated = loadRecycleBinContacts().filterNot { it.deletedId == deletedId }
        saveRecycleBinEntries(updated)
    }

    fun emptyRecycleBin() {
        saveRecycleBinEntries(emptyList())
    }

    /**
     * Tracks the last 10 Recently Viewed Contacts.
     */
    fun recordRecentlyViewedContact(contactId: Long) {
        if (contactId <= 0L) return
        val existing = getRecentlyViewedContactIds().toMutableList()
        existing.remove(contactId)
        existing.add(0, contactId)
        val top10 = existing.take(10)
        prefs.edit().putString("recently_viewed_contact_ids", top10.joinToString(",")).apply()
    }

    fun getRecentlyViewedContactIds(): List<Long> {
        val raw = prefs.getString("recently_viewed_contact_ids", "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.trim().toLongOrNull() }.take(10)
    }

    /**
     * Blocked Numbers & Spam management using Android BlockedNumberContract (when Default Dialer) + local storage.
     */
    suspend fun loadBlockedNumbers(): List<BlockedNumberEntry> = withContext(Dispatchers.IO) {
        val map = LinkedHashMap<String, BlockedNumberEntry>()

        // Load from local persistent list
        val localRaw = prefs.getString("local_blocked_numbers_json", null)
        if (!localRaw.isNullOrBlank()) {
            try {
                val arr = JSONArray(localRaw)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val num = obj.optString("number")
                    val key = normalizePhoneKey(num)
                    if (key.isNotEmpty()) {
                        map[key] = BlockedNumberEntry(
                            id = obj.optLong("id", (i + 1).toLong()),
                            number = num,
                            formattedNumber = formatPhoneNumber(num),
                            addedTimestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Also query Android BlockedNumberContract if default dialer
        try {
            if (BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
                contentResolver.query(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    arrayOf(
                        BlockedNumberContract.BlockedNumbers.COLUMN_ID,
                        BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(BlockedNumberContract.BlockedNumbers.COLUMN_ID)
                    val numIdx = cursor.getColumnIndex(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
                    while (cursor.moveToNext()) {
                        val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                        val num = if (numIdx >= 0) cursor.getString(numIdx).orEmpty() else ""
                        val key = normalizePhoneKey(num)
                        if (key.isNotEmpty() && !map.containsKey(key)) {
                            map[key] = BlockedNumberEntry(
                                id = id,
                                number = num,
                                formattedNumber = formatPhoneNumber(num),
                                addedTimestamp = System.currentTimeMillis()
                            )
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }

        map.values.toList()
    }

    suspend fun blockNumber(rawNumber: String): Boolean = withContext(Dispatchers.IO) {
        val clean = rawNumber.trim()
        val key = normalizePhoneKey(clean)
        if (key.isEmpty()) return@withContext false

        val current = loadBlockedNumbers().toMutableList()
        if (current.none { normalizePhoneKey(it.number) == key }) {
            current.add(
                0,
                BlockedNumberEntry(
                    id = System.currentTimeMillis(),
                    number = clean,
                    formattedNumber = formatPhoneNumber(clean),
                    addedTimestamp = System.currentTimeMillis()
                )
            )
            saveLocalBlockedNumbers(current)
        }

        try {
            if (BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
                val values = ContentValues().apply {
                    put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, clean)
                }
                contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
            }
        } catch (_: Exception) {
        }
        true
    }

    suspend fun unblockNumber(rawNumber: String): Boolean = withContext(Dispatchers.IO) {
        val clean = rawNumber.trim()
        val key = normalizePhoneKey(clean)
        if (key.isEmpty()) return@withContext false

        val updated = loadBlockedNumbers().filterNot { normalizePhoneKey(it.number) == key }
        saveLocalBlockedNumbers(updated)

        try {
            if (BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
                BlockedNumberContract.unblock(context, clean)
            }
        } catch (_: Exception) {
        }
        true
    }

    private fun saveLocalBlockedNumbers(list: List<BlockedNumberEntry>) {
        val arr = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("number", item.number)
                put("timestamp", item.addedTimestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString("local_blocked_numbers_json", arr.toString()).apply()
    }

    fun isNumberBlockedSync(rawNumber: String): Boolean {
        val key = normalizePhoneKey(rawNumber)
        if (key.isEmpty()) return false
        val localRaw = prefs.getString("local_blocked_numbers_json", null)
        if (!localRaw.isNullOrBlank()) {
            try {
                val arr = JSONArray(localRaw)
                for (i in 0 until arr.length()) {
                    val num = arr.getJSONObject(i).optString("number")
                    if (normalizePhoneKey(num) == key) return true
                }
            } catch (_: Exception) {
            }
        }
        return try {
            if (BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
                BlockedNumberContract.isBlocked(context, rawNumber)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Duplicate Contact Identification: groups contacts sharing identical normalized phone numbers or exact names.
     */
    fun findDuplicateContacts(contacts: List<ContactItem>): List<DuplicateContactGroup> {
        val groups = mutableListOf<DuplicateContactGroup>()
        val usedContactIds = mutableSetOf<Long>()

        // Group by normalized primary phone number
        val byPhone = contacts.filter { !it.primaryPhone.isNullOrBlank() }
            .groupBy { normalizePhoneKey(it.primaryPhone.orEmpty()) }
            .filter { (key, list) -> key.length >= 6 && list.size > 1 }

        byPhone.forEach { (_, dupList) ->
            val formattedNum = dupList.first().primaryPhone.orEmpty()
            groups.add(
                DuplicateContactGroup(
                    keyLabel = "Same number: $formattedNum",
                    contacts = dupList
                )
            )
            dupList.forEach { usedContactIds.add(it.id) }
        }

        // Also check identical names not yet grouped
        val byName = contacts.filter { !usedContactIds.contains(it.id) && it.displayName.isNotBlank() }
            .groupBy { it.displayName.trim().lowercase() }
            .filter { (_, list) -> list.size > 1 }

        byName.forEach { (_, dupList) ->
            groups.add(
                DuplicateContactGroup(
                    keyLabel = "Same name: ${dupList.first().displayName}",
                    contacts = dupList
                )
            )
        }

        return groups
    }

    suspend fun mergeDuplicateContactGroup(group: DuplicateContactGroup): Boolean =
        withContext(Dispatchers.IO) {
            if (group.contacts.size < 2 || !hasPermission(Manifest.permission.WRITE_CONTACTS)) {
                return@withContext false
            }
            val primary = group.contacts.first()
            val allPhones = group.contacts.flatMap { it.phones }.map { it.number }.distinct()
            val allEmails = group.contacts.flatMap { it.emails }.distinct()

            val mergedForm = ContactFormData(
                contactId = primary.id,
                rawContactId = primary.rawContactId,
                name = primary.displayName,
                phoneticName = group.contacts.firstOrNull { it.phoneticName.isNotBlank() }?.phoneticName.orEmpty(),
                number = allPhones.firstOrNull().orEmpty(),
                secondaryNumber = allPhones.getOrNull(1).orEmpty(),
                email = allEmails.firstOrNull().orEmpty(),
                company = group.contacts.firstOrNull { it.company.isNotBlank() }?.company.orEmpty(),
                jobTitle = group.contacts.firstOrNull { it.jobTitle.isNotBlank() }?.jobTitle.orEmpty(),
                address = group.contacts.firstOrNull { it.address.isNotBlank() }?.address.orEmpty(),
                notes = group.contacts.firstOrNull { it.notes.isNotBlank() }?.notes.orEmpty()
            )
            val saveRes = saveContact(mergedForm)
            if (saveRes.isSuccess) {
                group.contacts.drop(1).forEach { dup ->
                    moveContactToRecycleBin(dup)
                }
                return@withContext true
            }
            false
        }

    /**
     * Exports all contacts to a standard vCard (.vcf) file at the given Document URI.
     */
    suspend fun exportContactsToVcf(uri: Uri, contacts: List<ContactItem>): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val outStream = contentResolver.openOutputStream(uri)
                    ?: return@withContext Result.failure(IllegalStateException("Unable to open output stream."))
                OutputStreamWriter(outStream, Charsets.UTF_8).use { writer ->
                    contacts.forEach { c ->
                        writer.write("BEGIN:VCARD\r\n")
                        writer.write("VERSION:3.0\r\n")
                        writer.write("FN:${c.displayName}\r\n")
                        c.phones.forEach { p ->
                            writer.write("TEL;TYPE=CELL:${p.number}\r\n")
                        }
                        c.emails.forEach { email ->
                            writer.write("EMAIL;TYPE=INTERNET:$email\r\n")
                        }
                        if (c.company.isNotBlank()) {
                            writer.write("ORG:${c.company}\r\n")
                        }
                        if (c.jobTitle.isNotBlank()) {
                            writer.write("TITLE:${c.jobTitle}\r\n")
                        }
                        writer.write("END:VCARD\r\n")
                    }
                }
                prefs.edit().putLong("last_contacts_backup_time", System.currentTimeMillis()).apply()
                Result.success(contacts.size)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Imports contacts from a standard vCard (.vcf) file at the given Document URI into Android ContactsContract.
     */
    suspend fun importContactsFromVcf(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        if (!hasPermission(Manifest.permission.WRITE_CONTACTS)) {
            return@withContext Result.failure(SecurityException("Contacts write permission is required to import contacts."))
        }
        try {
            val inStream = contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Unable to open .vcf file."))
            var importedCount = 0
            BufferedReader(InputStreamReader(inStream, Charsets.UTF_8)).use { reader ->
                var inCard = false
                var name = ""
                val phones = mutableListOf<String>()
                var email = ""
                var company = ""
                var title = ""

                var line: String? = reader.readLine()
                while (line != null) {
                    val trimmed = line.trim()
                    when {
                        trimmed.equals("BEGIN:VCARD", ignoreCase = true) -> {
                            inCard = true
                            name = ""
                            phones.clear()
                            email = ""
                            company = ""
                            title = ""
                        }
                        trimmed.equals("END:VCARD", ignoreCase = true) && inCard -> {
                            inCard = false
                            if (name.isNotBlank() || phones.isNotEmpty()) {
                                val res = saveContact(
                                    ContactFormData(
                                        name = name,
                                        number = phones.firstOrNull().orEmpty(),
                                        secondaryNumber = phones.getOrNull(1).orEmpty(),
                                        email = email,
                                        company = company,
                                        jobTitle = title
                                    )
                                )
                                if (res.isSuccess) importedCount++
                            }
                        }
                        inCard && trimmed.startsWith("FN:", ignoreCase = true) -> {
                            name = trimmed.substringAfter(":").trim()
                        }
                        inCard && trimmed.startsWith("TEL", ignoreCase = true) -> {
                            val num = trimmed.substringAfter(":").trim()
                            if (num.isNotBlank()) phones.add(num)
                        }
                        inCard && trimmed.startsWith("EMAIL", ignoreCase = true) -> {
                            email = trimmed.substringAfter(":").trim()
                        }
                        inCard && trimmed.startsWith("ORG:", ignoreCase = true) -> {
                            company = trimmed.substringAfter(":").replace(";", " ").trim()
                        }
                        inCard && trimmed.startsWith("TITLE:", ignoreCase = true) -> {
                            title = trimmed.substringAfter(":").trim()
                        }
                    }
                    line = reader.readLine()
                }
            }
            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Triggers an immediate manual Android Contacts sync across configured device/Google accounts.
     */
    fun triggerManualContactsSync(): Long {
        try {
            val extras = Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }
            ContentResolver.requestSync(null, ContactsContract.AUTHORITY, extras)
        } catch (_: Exception) {
        }
        val now = System.currentTimeMillis()
        prefs.edit().putLong("last_contacts_backup_time", now).apply()
        return now
    }

    fun getLastBackupTimestamp(): Long {
        return prefs.getLong("last_contacts_backup_time", 0L)
    }

    /**
     * Ringtone, Vibration, and Theme Preferences.
     */
    suspend fun loadSystemRingtones(): List<RingtoneOption> = withContext(Dispatchers.IO) {
        cachedSystemRingtones?.let { return@withContext it }
        val options = mutableListOf<RingtoneOption>()
        try {
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (defaultUri != null) {
                options.add(RingtoneOption("Phone Default Ringtone", defaultUri.toString()))
            }
            val rm = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_RINGTONE)
            }
            val cursor = rm.cursor
            var count = 0
            while (cursor != null && cursor.moveToNext() && count < 25) {
                count++
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX).orEmpty()
                val uri = rm.getRingtoneUri(cursor.position)?.toString().orEmpty()
                if (title.isNotBlank() && uri.isNotBlank() && options.none { it.uriString == uri }) {
                    options.add(RingtoneOption(title = title, uriString = uri))
                }
            }
        } catch (_: Exception) {
        }
        if (options.isEmpty()) {
            options.add(RingtoneOption("System Default Ringtone", "default"))
        }
        cachedSystemRingtones = options
        options
    }

    companion object {
        @Volatile
        private var cachedSystemRingtones: List<RingtoneOption>? = null
    }

    fun getSelectedRingtoneTitle(): String {
        return prefs.getString("selected_ringtone_title", "Phone Default Ringtone")
            ?: "Phone Default Ringtone"
    }

    fun getSelectedRingtoneUri(): String {
        return prefs.getString("selected_ringtone_uri", "") ?: ""
    }

    fun setSelectedRingtone(option: RingtoneOption) {
        prefs.edit()
            .putString("selected_ringtone_title", option.title)
            .putString("selected_ringtone_uri", option.uriString)
            .apply()
    }

    fun isVibrationEnabled(): Boolean = prefs.getBoolean("vibrate_on_ring", true)

    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("vibrate_on_ring", enabled).apply()
    }

    fun getThemeMode(): AppThemeMode {
        val raw = prefs.getString("app_theme_mode", AppThemeMode.FOLLOW_SYSTEM.name)
        return try {
            AppThemeMode.valueOf(raw ?: AppThemeMode.FOLLOW_SYSTEM.name)
        } catch (_: Exception) {
            AppThemeMode.FOLLOW_SYSTEM
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("app_theme_mode", mode.name).apply()
    }

    fun getContactSortOrder(): com.example.model.ContactSortOrder {
        val raw = prefs.getString("contact_sort_order", com.example.model.ContactSortOrder.NAME_ASC.name)
        return try {
            com.example.model.ContactSortOrder.valueOf(raw ?: com.example.model.ContactSortOrder.NAME_ASC.name)
        } catch (_: Exception) {
            com.example.model.ContactSortOrder.NAME_ASC
        }
    }

    fun setContactSortOrder(order: com.example.model.ContactSortOrder) {
        prefs.edit().putString("contact_sort_order", order.name).apply()
    }

    suspend fun loadMyProfile(): ContactFormData = withContext(Dispatchers.IO) {
        var name = prefs.getString("my_profile_name", "") ?: ""
        val number = prefs.getString("my_profile_number", "") ?: ""
        val email = prefs.getString("my_profile_email", "") ?: ""
        val company = prefs.getString("my_profile_company", "") ?: ""
        val jobTitle = prefs.getString("my_profile_job_title", "") ?: ""
        val address = prefs.getString("my_profile_address", "") ?: ""

        if (name.isBlank() && hasPermission(Manifest.permission.READ_CONTACTS)) {
            try {
                contentResolver.query(
                    ContactsContract.Profile.CONTENT_URI,
                    arrayOf(ContactsContract.Profile.DISPLAY_NAME_PRIMARY),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0).orEmpty()
                    }
                }
            } catch (_: Exception) {
            }
        }
        ContactFormData(
            name = name,
            number = number,
            email = email,
            company = company,
            jobTitle = jobTitle,
            address = address
        )
    }

    fun saveMyProfile(form: ContactFormData) {
        prefs.edit()
            .putString("my_profile_name", form.name.trim())
            .putString("my_profile_number", form.number.trim())
            .putString("my_profile_email", form.email.trim())
            .putString("my_profile_company", form.company.trim())
            .putString("my_profile_job_title", form.jobTitle.trim())
            .putString("my_profile_address", form.address.trim())
            .apply()
    }

    fun loadCallNote(number: String): String {
        val key = normalizePhoneKey(number).ifBlank { "general" }
        return prefs.getString("call_note_$key", "") ?: ""
    }

    fun saveCallNote(number: String, note: String) {
        val key = normalizePhoneKey(number).ifBlank { "general" }
        prefs.edit().putString("call_note_$key", note).apply()
    }

    fun hasSeenRolePrompt(): Boolean = prefs.getBoolean("seen_default_dialer_prompt", false)

    fun markSeenRolePrompt() {
        prefs.edit().putBoolean("seen_default_dialer_prompt", true).apply()
    }
}
