package com.example.model

import android.telecom.Call
import android.telecom.PhoneAccountHandle

enum class CallDirection {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED
}

enum class AppThemeMode(val label: String) {
    FOLLOW_SYSTEM("Follow system"),
    LIGHT("Light mode"),
    DARK("Dark mode")
}

enum class ContactSortOrder(val label: String) {
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    RECENTLY_ADDED("Recently added"),
    FREQUENTLY_CONTACTED("Frequently contacted")
}

data class CallLogItem(
    val id: Long,
    val number: String,
    val formattedNumber: String,
    val cachedName: String?,
    val contactId: Long?,
    val timestamp: Long,
    val durationSeconds: Long,
    val direction: CallDirection,
    val isHd: Boolean,
    val geocodedLocation: String?,
    val simLabel: String?,
    val phoneAccountId: String?,
    val photoUri: String? = null,
    val isBlockedOrSpam: Boolean = false
) {
    val displayTitle: String
        get() = cachedName?.takeIf { it.isNotBlank() }
            ?: formattedNumber.takeIf { it.isNotBlank() }
            ?: number.ifBlank { "Unknown" }

    val displaySubtitle: String
        get() {
            return if (!cachedName.isNullOrBlank()) {
                formattedNumber.ifBlank { number }
            } else {
                geocodedLocation?.takeIf { it.isNotBlank() } ?: formattedNumber.ifBlank { number }
            }
        }
}

data class PhoneNumberEntry(
    val dataId: Long = 0L,
    val number: String,
    val normalizedNumber: String = "",
    val typeLabel: String = "Mobile",
    val isDefault: Boolean = false,
    val isRecent: Boolean = false,
    val location: String? = null
)

data class ContactGroupItem(
    val id: Long,
    val title: String,
    val memberCount: Int = 0
)

data class ContactItem(
    val id: Long,
    val rawContactId: Long? = null,
    val lookupKey: String? = null,
    val displayName: String,
    val phoneticName: String = "",
    val photoUri: String? = null,
    val isStarred: Boolean = false,
    val company: String = "",
    val jobTitle: String = "",
    val phones: List<PhoneNumberEntry> = emptyList(),
    val emails: List<String> = emptyList(),
    val address: String = "",
    val notes: String = "",
    val groupIds: List<Long> = emptyList(),
    val groupNames: List<String> = emptyList(),
    val lastUpdatedTimestamp: Long = 0L,
    val timesContacted: Int = 0
) {
    val subtitle: String?
        get() = when {
            jobTitle.isNotBlank() && company.isNotBlank() -> "$jobTitle · $company"
            jobTitle.isNotBlank() -> jobTitle
            company.isNotBlank() -> company
            else -> null
        }

    val initial: String
        get() {
            val trimmed = displayName.trim()
            if (trimmed.isEmpty()) return "#"
            val firstChar = trimmed.first().uppercaseChar()
            return if (firstChar in 'A'..'Z') firstChar.toString() else firstChar.toString()
        }

    val sectionLetter: String
        get() {
            val trimmed = displayName.trim()
            if (trimmed.isEmpty()) return "#"
            val firstChar = trimmed.first().uppercaseChar()
            return if (firstChar in 'A'..'Z') firstChar.toString() else "#"
        }

    val primaryPhone: String?
        get() = phones.firstOrNull { it.isDefault }?.number ?: phones.firstOrNull()?.number
}

data class SimAccountInfo(
    val handle: PhoneAccountHandle,
    val label: String,
    val slotIndex: Int,
    val carrierName: String,
    val isHdCapable: Boolean = false
)

data class ContactFormData(
    val contactId: Long? = null,
    val rawContactId: Long? = null,
    val name: String = "",
    val phoneticName: String = "",
    val number: String = "",
    val secondaryNumber: String = "",
    val email: String = "",
    val selectedGroupIds: List<Long> = emptyList(),
    val selectedGroupNames: List<String> = emptyList(),
    val company: String = "",
    val jobTitle: String = "",
    val address: String = "",
    val notes: String = "",
    val photoUri: String? = null,
    val photoBytes: ByteArray? = null
)

data class DeletedContactEntry(
    val deletedId: String,
    val deletedTimestamp: Long,
    val displayName: String,
    val primaryNumber: String,
    val secondaryNumber: String = "",
    val phoneticName: String = "",
    val email: String = "",
    val company: String = "",
    val jobTitle: String = "",
    val address: String = "",
    val notes: String = ""
)

data class BlockedNumberEntry(
    val id: Long,
    val number: String,
    val formattedNumber: String,
    val addedTimestamp: Long,
    val label: String = "Blocked"
)

data class DuplicateContactGroup(
    val keyLabel: String,
    val contacts: List<ContactItem>
)

data class RingtoneOption(
    val title: String,
    val uriString: String
)

enum class CallAudioEndpointType {
    EARPIECE,
    BLUETOOTH,
    WIRED_HEADSET,
    SPEAKER,
    STREAMING
}

data class CallAudioEndpointItem(
    val id: String,
    val name: String,
    val type: CallAudioEndpointType,
    val isActive: Boolean = false,
    val bluetoothAddress: String? = null
)

data class ActiveCallUiState(
    val callId: String = "",
    val number: String = "",
    val displayName: String? = null,
    val photoUri: String? = null,
    val locationLabel: String? = null,
    val state: Int = Call.STATE_DISCONNECTED,
    val connectTimeMillis: Long = 0L,
    val isIncoming: Boolean = false,
    val canHold: Boolean = false,
    val isOnHold: Boolean = false,
    val canMute: Boolean = true,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isBluetoothOn: Boolean = false,
    val isBluetoothAvailable: Boolean = false,
    val activeAudioEndpoint: CallAudioEndpointItem = CallAudioEndpointItem(
        id = "earpiece",
        name = "Phone earpiece",
        type = CallAudioEndpointType.EARPIECE,
        isActive = true
    ),
    val availableAudioEndpoints: List<CallAudioEndpointItem> = listOf(
        CallAudioEndpointItem(
            id = "earpiece",
            name = "Phone earpiece",
            type = CallAudioEndpointType.EARPIECE,
            isActive = true
        ),
        CallAudioEndpointItem(
            id = "speaker",
            name = "Speaker",
            type = CallAudioEndpointType.SPEAKER,
            isActive = false
        )
    ),
    val pendingAudioEndpointId: String? = null,
    val supportsVideo: Boolean = false,
    val isVideoActive: Boolean = false,
    val callCount: Int = 1,
    val secondaryCallerName: String? = null,
    val waitingCallNumber: String? = null,
    val waitingCallerName: String? = null,
    val isWaitingCallRinging: Boolean = false,
    val isUnknownNumber: Boolean = false,
    val isBlockedOrSpam: Boolean = false,
    val spamWarningLabel: String? = null,
    val isNearEar: Boolean = false,
    val isRecordingActive: Boolean = false,
    val statusMessage: String? = null
) {
    val isRinging: Boolean
        get() = state == Call.STATE_RINGING

    val isOutgoingDialing: Boolean
        get() = state == Call.STATE_DIALING ||
            state == Call.STATE_CONNECTING ||
            state == Call.STATE_SELECT_PHONE_ACCOUNT

    val isActiveOrDialing: Boolean
        get() = state == Call.STATE_ACTIVE ||
            state == Call.STATE_DIALING ||
            state == Call.STATE_CONNECTING ||
            state == Call.STATE_HOLDING ||
            state == Call.STATE_SELECT_PHONE_ACCOUNT

    val hasMultipleSelectableEndpoints: Boolean
        get() = isBluetoothAvailable ||
            isBluetoothOn ||
            availableAudioEndpoints.size > 2 ||
            availableAudioEndpoints.any {
                it.type == CallAudioEndpointType.BLUETOOTH ||
                    it.type == CallAudioEndpointType.WIRED_HEADSET
            }
}

