package com.example.viewmodel

import android.Manifest
import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DialerRepository
import com.example.data.PersonalizationManager
import com.example.model.AppThemeMode
import com.example.model.BlockedNumberEntry
import com.example.model.CallLogItem
import com.example.model.ContactFormData
import com.example.model.ContactItem
import com.example.model.ContactSortOrder
import com.example.model.DeletedContactEntry
import com.example.model.DuplicateContactGroup
import com.example.model.PersonalizationCategory
import com.example.model.PhoneNumberEntry
import com.example.model.RingtoneOption
import com.example.model.SimAccountInfo
import com.example.telecom.CallStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class DialerSubScreen {
    data object None : DialerSubScreen()
    data class ContactDetail(
        val contactId: Long?,
        val fallbackCallLog: CallLogItem? = null
    ) : DialerSubScreen()
    data class ContactForm(
        val initialData: ContactFormData
    ) : DialerSubScreen()
    data object Settings : DialerSubScreen()
    data class PersonalizationStudio(
        val initialCategory: PersonalizationCategory? = null
    ) : DialerSubScreen()
    data object BlockedNumbers : DialerSubScreen()
    data object RecycleBin : DialerSubScreen()
}

data class DialerUiState(
    val selectedBottomTab: Int = 0,
    val subScreen: DialerSubScreen = DialerSubScreen.None,
    val contacts: List<ContactItem> = emptyList(),
    val recentlyViewedContacts: List<ContactItem> = emptyList(),
    val duplicateGroups: List<DuplicateContactGroup> = emptyList(),
    val callLogs: List<CallLogItem> = emptyList(),
    val blockedNumbers: List<BlockedNumberEntry> = emptyList(),
    val recycleBinContacts: List<DeletedContactEntry> = emptyList(),
    val myProfile: ContactFormData = ContactFormData(),
    val simAccounts: List<SimAccountInfo> = emptyList(),
    val selectedSimIndex: Int = 0,
    val dialInput: String = "",
    val formattedDialInput: String = "",
    val isDialPadExpanded: Boolean = true,
    val sortOrder: ContactSortOrder = ContactSortOrder.NAME_ASC,
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    val ringtones: List<RingtoneOption> = emptyList(),
    val selectedRingtoneTitle: String = "System Default",
    val vibrationEnabled: Boolean = true,
    val lastBackupTimestamp: Long = 0L,
    val hasContactsPermission: Boolean = false,
    val hasWriteContactsPermission: Boolean = false,
    val hasCallLogPermission: Boolean = false,
    val hasCallPhonePermission: Boolean = false,
    val isDefaultDialer: Boolean = false,
    val showFirstLaunchRoleDialog: Boolean = false,
    val snackbarMessage: String? = null
)

class DialerViewModel(application: Application) : AndroidViewModel(application) {

    val repository = DialerRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(
        DialerUiState(
            themeMode = repository.getThemeMode(),
            sortOrder = repository.getContactSortOrder(),
            selectedRingtoneTitle = repository.getSelectedRingtoneTitle(),
            vibrationEnabled = repository.isVibrationEnabled(),
            lastBackupTimestamp = repository.getLastBackupTimestamp()
        )
    )
    val uiState: StateFlow<DialerUiState> = _uiState.asStateFlow()

    private var observersRegistered = false
    private var refreshJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val providerObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            refreshAllData()
        }
    }

    init {
        val studioConfig = PersonalizationManager.ensureInitialized(application.applicationContext)
        _uiState.update { it.copy(themeMode = studioConfig.themeMode) }
        syncPermissionsAndRoleStatus(checkFirstLaunchPrompt = true)
        refreshAllData()
    }

    fun syncPermissionsAndRoleStatus(checkFirstLaunchPrompt: Boolean = false) {
        val appContext = getApplication<Application>().applicationContext
        val readContacts = repository.hasPermission(Manifest.permission.READ_CONTACTS)
        val writeContacts = repository.hasPermission(Manifest.permission.WRITE_CONTACTS)
        val readCallLog = repository.hasPermission(Manifest.permission.READ_CALL_LOG)
        val callPhone = repository.hasPermission(Manifest.permission.CALL_PHONE)
        val isDefault = CallStateManager.isDefaultDialer(appContext)

        val shouldShowRolePrompt = if (checkFirstLaunchPrompt && !isDefault && !repository.hasSeenRolePrompt()) {
            true
        } else {
            _uiState.value.showFirstLaunchRoleDialog && !isDefault
        }

        _uiState.update { state ->
            state.copy(
                hasContactsPermission = readContacts,
                hasWriteContactsPermission = writeContacts,
                hasCallLogPermission = readCallLog,
                hasCallPhonePermission = callPhone,
                isDefaultDialer = isDefault,
                showFirstLaunchRoleDialog = shouldShowRolePrompt
            )
        }

        registerObserversIfPermitted(readContacts, readCallLog)
    }

    private fun registerObserversIfPermitted(readContacts: Boolean, readCallLog: Boolean) {
        if (observersRegistered) return
        val cr = getApplication<Application>().contentResolver
        try {
            if (readContacts) {
                cr.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, providerObserver)
                observersRegistered = true
            }
            if (readCallLog) {
                cr.registerContentObserver(CallLog.Calls.CONTENT_URI, true, providerObserver)
                observersRegistered = true
            }
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (observersRegistered) {
            try {
                getApplication<Application>().contentResolver.unregisterContentObserver(providerObserver)
            } catch (_: Exception) {
            }
        }
    }

    fun dismissFirstLaunchRoleDialog() {
        repository.markSeenRolePrompt()
        _uiState.update { it.copy(showFirstLaunchRoleDialog = false) }
    }

    fun refreshAllData() {
        syncPermissionsAndRoleStatus(checkFirstLaunchPrompt = false)
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val loadedState = withContext(Dispatchers.IO) {
                val simAccounts = repository.loadSimAccounts()
                val myProfile = repository.loadMyProfile()
                val blockedNumbers = repository.loadBlockedNumbers()
                val recycleBin = repository.loadRecycleBinContacts()
                val ringtones = repository.loadSystemRingtones()
                val currentSortOrder = repository.getContactSortOrder()

                // Single pass load of call logs to identify recent numbers & call frequency
                val rawCallLogs = repository.loadCallLogs()
                val recentNumbers = ArrayList<String>(rawCallLogs.size)
                val freqMap = HashMap<String, Int>(rawCallLogs.size)

                for (log in rawCallLogs) {
                    recentNumbers.add(log.number)
                    val key = repository.normalizePhoneKey(log.number)
                    if (key.isNotEmpty()) {
                        freqMap[key] = (freqMap[key] ?: 0) + 1
                    }
                }

                // Load real contacts enriched with recent-number badges & call frequency
                val contacts = repository.loadContacts(
                    recentNumbersOrdered = recentNumbers,
                    callFrequencyByPhoneKey = freqMap
                )

                // Resolve Recently Viewed Contacts (last 10)
                val recentIds = repository.getRecentlyViewedContactIds()
                val contactsById = contacts.associateBy { it.id }
                val recentlyViewed = recentIds.mapNotNull { contactsById[it] }.take(10)

                // Identify duplicate contacts
                val duplicates = repository.findDuplicateContacts(contacts)

                // Build lookup map by normalized phone key so call logs resolve latest contact names/Ids/photos
                val contactsByPhone = HashMap<String, ContactItem>(contacts.size * 2)
                for (contact in contacts) {
                    for (phone in contact.phones) {
                        val key = repository.normalizePhoneKey(phone.number)
                        if (key.isNotEmpty() && !contactsByPhone.containsKey(key)) {
                            contactsByPhone[key] = contact
                        }
                    }
                }

                // Enrich already-loaded call logs in memory instead of querying CallLog provider twice
                val enrichedCallLogs = repository.enrichCallLogsWithContacts(
                    rawCallLogs = rawCallLogs,
                    contactsByPhoneKey = contactsByPhone
                )

                val themeMode = repository.getThemeMode()
                val selectedRingtoneTitle = repository.getSelectedRingtoneTitle()
                val vibrationEnabled = repository.isVibrationEnabled()
                val lastBackupTimestamp = repository.getLastBackupTimestamp()

                _uiState.update { state ->
                    state.copy(
                        contacts = contacts,
                        recentlyViewedContacts = recentlyViewed,
                        duplicateGroups = duplicates,
                        callLogs = enrichedCallLogs,
                        blockedNumbers = blockedNumbers,
                        recycleBinContacts = recycleBin,
                        myProfile = myProfile,
                        simAccounts = simAccounts,
                        selectedSimIndex = 0, // SIM 1 only
                        sortOrder = currentSortOrder,
                        themeMode = themeMode,
                        ringtones = ringtones,
                        selectedRingtoneTitle = selectedRingtoneTitle,
                        vibrationEnabled = vibrationEnabled,
                        lastBackupTimestamp = lastBackupTimestamp
                    )
                }
            }
        }
    }

    fun selectBottomTab(tabIndex: Int) {
        _uiState.update {
            it.copy(
                selectedBottomTab = tabIndex,
                subScreen = DialerSubScreen.None
            )
        }
    }

    fun openSettingsScreen() {
        _uiState.update { it.copy(subScreen = DialerSubScreen.Settings) }
    }

    fun openPersonalizationStudio(category: PersonalizationCategory? = null) {
        _uiState.update { it.copy(subScreen = DialerSubScreen.PersonalizationStudio(category)) }
    }

    fun openBlockedNumbersScreen() {
        _uiState.update { it.copy(subScreen = DialerSubScreen.BlockedNumbers) }
    }

    fun openRecycleBinScreen() {
        _uiState.update { it.copy(subScreen = DialerSubScreen.RecycleBin) }
    }

    fun setDialInputFromIntent(number: String) {
        val clean = number.trim()
        _uiState.update {
            it.copy(
                selectedBottomTab = 0,
                subScreen = DialerSubScreen.None,
                dialInput = clean,
                formattedDialInput = repository.formatPhoneNumber(clean),
                isDialPadExpanded = true
            )
        }
    }

    fun onPasteDialInput(pasted: String) {
        val filtered = pasted.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (filtered.isEmpty()) return
        _uiState.update {
            it.copy(
                dialInput = filtered,
                formattedDialInput = repository.formatPhoneNumber(filtered),
                isDialPadExpanded = true
            )
        }
    }

    fun onDialDigit(digit: Char) {
        val next = _uiState.value.dialInput + digit
        _uiState.update {
            it.copy(
                dialInput = next,
                formattedDialInput = repository.formatPhoneNumber(next),
                isDialPadExpanded = true
            )
        }
        CallStateManager.playLocalKeyTone(digit)
    }

    fun onBackspace() {
        val current = _uiState.value.dialInput
        if (current.isEmpty()) return
        val next = current.dropLast(1)
        _uiState.update {
            it.copy(
                dialInput = next,
                formattedDialInput = repository.formatPhoneNumber(next)
            )
        }
    }

    fun onClearDialInput() {
        _uiState.update {
            it.copy(
                dialInput = "",
                formattedDialInput = ""
            )
        }
    }

    fun toggleDialPad(expanded: Boolean) {
        _uiState.update { it.copy(isDialPadExpanded = expanded) }
    }

    fun openContactDetailById(contactId: Long) {
        repository.recordRecentlyViewedContact(contactId)
        val recentIds = repository.getRecentlyViewedContactIds()
        val contactsById = _uiState.value.contacts.associateBy { it.id }
        val recentlyViewed = recentIds.mapNotNull { contactsById[it] }.take(10)

        _uiState.update {
            it.copy(
                recentlyViewedContacts = recentlyViewed,
                subScreen = DialerSubScreen.ContactDetail(contactId = contactId)
            )
        }
    }

    fun openCallLogDetails(callLogItem: CallLogItem) {
        val matchedContactId = callLogItem.contactId ?: run {
            val targetKey = repository.normalizePhoneKey(callLogItem.number)
            _uiState.value.contacts.firstOrNull { c ->
                c.phones.any { repository.normalizePhoneKey(it.number) == targetKey && targetKey.isNotEmpty() }
            }?.id
        }
        if (matchedContactId != null && matchedContactId > 0L) {
            repository.recordRecentlyViewedContact(matchedContactId)
        }
        _uiState.update {
            it.copy(
                subScreen = DialerSubScreen.ContactDetail(
                    contactId = matchedContactId,
                    fallbackCallLog = callLogItem
                )
            )
        }
    }

    fun openNewContactForm(prefilledNumber: String = "") {
        _uiState.update {
            it.copy(
                subScreen = DialerSubScreen.ContactForm(
                    initialData = ContactFormData(number = prefilledNumber)
                )
            )
        }
    }

    fun openEditContactForm(contact: ContactItem) {
        val form = ContactFormData(
            contactId = contact.id,
            rawContactId = contact.rawContactId,
            name = contact.displayName,
            phoneticName = contact.phoneticName,
            number = contact.phones.firstOrNull()?.number.orEmpty(),
            secondaryNumber = contact.phones.getOrNull(1)?.number.orEmpty(),
            email = contact.emails.firstOrNull().orEmpty(),
            company = contact.company,
            jobTitle = contact.jobTitle,
            address = contact.address,
            notes = contact.notes,
            photoUri = contact.photoUri
        )
        _uiState.update {
            it.copy(subScreen = DialerSubScreen.ContactForm(initialData = form))
        }
    }

    fun navigateBackToMain() {
        _uiState.update { it.copy(subScreen = DialerSubScreen.None) }
    }

    fun saveContact(form: ContactFormData) {
        viewModelScope.launch {
            val result = repository.saveContact(form)
            result.onSuccess { savedContactId ->
                if (savedContactId > 0L) {
                    repository.recordRecentlyViewedContact(savedContactId)
                }
                refreshAllData()
                _uiState.update {
                    it.copy(
                        subScreen = if (savedContactId > 0L) {
                            DialerSubScreen.ContactDetail(contactId = savedContactId)
                        } else {
                            DialerSubScreen.None
                        },
                        snackbarMessage = if (form.contactId != null) {
                            "Contact updated"
                        } else {
                            "Contact saved to device"
                        }
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        snackbarMessage = err.localizedMessage ?: "Failed to save contact"
                    )
                }
            }
        }
    }

    fun toggleFavorite(contact: ContactItem) {
        viewModelScope.launch {
            val targetStarred = !contact.isStarred
            val success = repository.toggleFavorite(contact.id, targetStarred)
            if (success) {
                refreshAllData()
                _uiState.update {
                    it.copy(
                        snackbarMessage = if (targetStarred) {
                            "Added ${contact.displayName} to Favorites"
                        } else {
                            "Removed ${contact.displayName} from Favorites"
                        }
                    )
                }
            } else {
                _uiState.update {
                    it.copy(snackbarMessage = "Grant Contacts write permission to update Favorites.")
                }
            }
        }
    }

    fun setDefaultPhoneNumber(contactId: Long, phoneDataId: Long) {
        viewModelScope.launch {
            val ok = repository.setDefaultNumber(contactId, phoneDataId)
            if (ok) {
                refreshAllData()
            }
        }
    }

    fun moveContactToRecycleBin(contact: ContactItem) {
        viewModelScope.launch {
            val ok = repository.moveContactToRecycleBin(contact)
            if (ok) {
                refreshAllData()
                _uiState.update {
                    it.copy(
                        subScreen = DialerSubScreen.None,
                        snackbarMessage = "Moved \"${contact.displayName}\" to Recycle Bin"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(snackbarMessage = "Unable to delete contact. Check Contacts write permission.")
                }
            }
        }
    }

    fun restoreFromRecycleBin(deletedId: String) {
        viewModelScope.launch {
            val entry = repository.loadRecycleBinContacts().firstOrNull { it.deletedId == deletedId }
            if (entry == null) return@launch
            val result = repository.restoreFromRecycleBin(entry)
            result.onSuccess {
                refreshAllData()
                _uiState.update { it.copy(snackbarMessage = "Contact restored from Recycle Bin") }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(snackbarMessage = err.localizedMessage ?: "Failed to restore contact")
                }
            }
        }
    }

    fun permanentlyDeleteFromRecycleBin(deletedId: String) {
        viewModelScope.launch {
            repository.permanentlyDeleteFromRecycleBin(deletedId)
            refreshAllData()
            _uiState.update { it.copy(snackbarMessage = "Permanently deleted contact") }
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch {
            repository.emptyRecycleBin()
            refreshAllData()
            _uiState.update { it.copy(snackbarMessage = "Recycle Bin emptied") }
        }
    }

    fun deleteCallLogEntry(callLogId: Long) {
        viewModelScope.launch {
            val ok = repository.deleteCallLogItem(callLogId)
            if (ok) {
                refreshAllData()
                _uiState.update { it.copy(snackbarMessage = "Call history entry deleted") }
            } else {
                _uiState.update {
                    it.copy(snackbarMessage = "Grant Call Log write permission to delete entries.")
                }
            }
        }
    }

    fun clearAllCallHistory() {
        viewModelScope.launch {
            val ok = repository.clearAllCallHistory()
            refreshAllData()
            _uiState.update {
                it.copy(
                    snackbarMessage = if (ok) {
                        "Call history cleared"
                    } else {
                        "Unable to clear call history"
                    }
                )
            }
        }
    }

    fun changeSortOrder(order: ContactSortOrder) {
        repository.setContactSortOrder(order)
        refreshAllData()
    }

    fun mergeDuplicateGroup(group: DuplicateContactGroup) {
        viewModelScope.launch {
            val ok = repository.mergeDuplicateContactGroup(group)
            if (ok) {
                refreshAllData()
                _uiState.update { it.copy(snackbarMessage = "Merged duplicate contacts (${group.keyLabel})") }
            } else {
                _uiState.update { it.copy(snackbarMessage = "Unable to merge duplicate contacts") }
            }
        }
    }

    fun mergeAllDuplicateGroups() {
        viewModelScope.launch {
            val groups = _uiState.value.duplicateGroups
            if (groups.isEmpty()) {
                _uiState.update { it.copy(snackbarMessage = "No duplicate contacts found") }
                return@launch
            }
            var mergedCount = 0
            groups.forEach { g ->
                if (repository.mergeDuplicateContactGroup(g)) {
                    mergedCount++
                }
            }
            refreshAllData()
            _uiState.update {
                it.copy(snackbarMessage = "Merged $mergedCount duplicate contact group(s)")
            }
        }
    }

    fun exportContactsToVcfUri(uri: Uri) {
        viewModelScope.launch {
            val result = repository.exportContactsToVcf(uri, _uiState.value.contacts)
            result.onSuccess { count ->
                refreshAllData()
                _uiState.update {
                    it.copy(snackbarMessage = "Exported $count contacts to .vcf backup")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(snackbarMessage = err.localizedMessage ?: "Failed to export contacts")
                }
            }
        }
    }

    fun importContactsFromVcfUri(uri: Uri) {
        viewModelScope.launch {
            val result = repository.importContactsFromVcf(uri)
            result.onSuccess { count ->
                refreshAllData()
                _uiState.update {
                    it.copy(snackbarMessage = "Imported $count contacts from .vcf file")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(snackbarMessage = err.localizedMessage ?: "Failed to import contacts")
                }
            }
        }
    }

    fun triggerManualContactsSync() {
        viewModelScope.launch {
            val ts = repository.triggerManualContactsSync()
            refreshAllData()
            _uiState.update {
                it.copy(
                    lastBackupTimestamp = ts,
                    snackbarMessage = "Manual Contacts sync requested"
                )
            }
        }
    }

    fun blockNumber(number: String, label: String = "") {
        viewModelScope.launch {
            val ok = repository.blockNumber(number)
            if (ok) {
                refreshAllData()
                _uiState.update { it.copy(snackbarMessage = "Blocked $number") }
            }
        }
    }

    fun unblockNumber(number: String) {
        viewModelScope.launch {
            val ok = repository.unblockNumber(number)
            if (ok) {
                refreshAllData()
                _uiState.update { it.copy(snackbarMessage = "Unblocked $number") }
            }
        }
    }

    fun isNumberBlocked(number: String): Boolean {
        val key = repository.normalizePhoneKey(number)
        if (key.isEmpty()) return false
        return _uiState.value.blockedNumbers.any { repository.normalizePhoneKey(it.number) == key }
    }

    fun setThemeMode(mode: AppThemeMode) {
        repository.setThemeMode(mode)
        PersonalizationManager.updateThemeModeEverywhere(getApplication<Application>().applicationContext, mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun selectRingtone(option: RingtoneOption) {
        repository.setSelectedRingtone(option)
        _uiState.update {
            it.copy(
                selectedRingtoneTitle = option.title,
                snackbarMessage = "Ringtone set to ${option.title}"
            )
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        repository.setVibrationEnabled(enabled)
        _uiState.update { it.copy(vibrationEnabled = enabled) }
    }

    fun saveMyProfile(form: ContactFormData) {
        repository.saveMyProfile(form)
        _uiState.update {
            it.copy(
                myProfile = form,
                snackbarMessage = "My profile updated"
            )
        }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * Resolves a ContactItem for ContactDetailScreen either from saved contacts or an unsaved CallLogItem.
     */
    fun resolveDetailContact(
        contactId: Long?,
        fallbackCallLog: CallLogItem?
    ): Pair<ContactItem?, Boolean> {
        val state = _uiState.value
        if (contactId != null) {
            val found = state.contacts.firstOrNull { it.id == contactId }
            if (found != null) return found to true
        }
        if (fallbackCallLog != null) {
            val numKey = repository.normalizePhoneKey(fallbackCallLog.number)
            val matched = state.contacts.firstOrNull { c ->
                c.phones.any { repository.normalizePhoneKey(it.number) == numKey && numKey.isNotEmpty() }
            }
            if (matched != null) return matched to true

            val unsavedContact = ContactItem(
                id = -1L,
                displayName = fallbackCallLog.displayTitle,
                phones = listOf(
                    PhoneNumberEntry(
                        dataId = 0L,
                        number = fallbackCallLog.formattedNumber.ifBlank { fallbackCallLog.number },
                        normalizedNumber = fallbackCallLog.number,
                        typeLabel = "Mobile",
                        isDefault = true,
                        isRecent = true,
                        location = fallbackCallLog.geocodedLocation
                    )
                )
            )
            return unsavedContact to false
        }
        return null to false
    }

    fun filterCallLogsForContact(contact: ContactItem): List<CallLogItem> {
        val keys = contact.phones
            .map { repository.normalizePhoneKey(it.number) }
            .filter { it.isNotEmpty() }
            .toSet()
        if (keys.isEmpty()) return emptyList()
        return _uiState.value.callLogs.filter { log ->
            val logKey = repository.normalizePhoneKey(log.number)
            keys.contains(logKey)
        }
    }
}
