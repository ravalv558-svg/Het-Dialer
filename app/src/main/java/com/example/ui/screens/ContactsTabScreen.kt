package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppBackgroundStyle
import com.example.model.ContactFormData
import com.example.model.ContactItem
import com.example.model.ContactSortOrder
import com.example.model.DuplicateContactGroup
import com.example.ui.components.ContactCircleAvatar
import com.example.ui.personalization.LiquidGlassBackdrop
import com.example.ui.theme.ColorOsGreen
import com.example.ui.theme.ColorOsStarGold
import com.example.ui.theme.ColorOsWhite
import com.example.ui.theme.LocalPersonalizationConfig
import kotlinx.coroutines.launch

private val ALPHABET_INDEX = listOf(
    "☆", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
    "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#"
)

/**
 * OPPO ColorOS 16 Contacts Screen:
 * - Search pill with green "Cancel" button (matches IMG-20261107-WA0022)
 * - Large bold "Contacts" heading + total contacts count (matches IMG-20261107-WA0013)
 * - Duplicate contacts banner card ("Duplicate contacts have been found." with "Dismiss" & "Merge")
 * - "My profile" row with green circular avatar
 * - Contact photos, Favorites section, A-Z fast scroll index bar, and floating green `+` Add Contact FAB
 */
@Composable
fun ContactsTabScreen(
    contacts: List<ContactItem>,
    recentlyViewedContacts: List<ContactItem>,
    duplicateGroups: List<DuplicateContactGroup>,
    sortOrder: ContactSortOrder,
    myProfile: ContactFormData,
    recycleBinCount: Int,
    hasContactsPermission: Boolean,
    onChangeSortOrder: (ContactSortOrder) -> Unit,
    onOpenContact: (Long) -> Unit,
    onAddContactClick: () -> Unit,
    onSaveMyProfile: (ContactFormData) -> Unit,
    onMergeDuplicateGroup: (DuplicateContactGroup) -> Unit,
    onImportContactsVcf: () -> Unit,
    onExportContactsVcf: () -> Unit,
    onManualSyncContacts: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestContactsPermission: () -> Unit,
    onOpenPersonalizationStudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val personalization = LocalPersonalizationConfig.current
    val useBackdrop = personalization.homeDialer.backgroundStyle != AppBackgroundStyle.THEME_SOLID
    val primaryAccent = if (personalization.useCustomColors) personalization.primaryColor else ColorOsGreen

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var favoritesOnlyFilter by remember { mutableStateOf(false) }
    var duplicateBannerDismissed by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showDuplicatesDialog by remember { mutableStateOf(false) }
    var showMyProfileDialog by remember { mutableStateOf(false) }

    val bgColor = if (useBackdrop) Color.Transparent else MaterialTheme.colorScheme.background
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)

    val filteredAndSortedContacts = remember(contacts, searchQuery, favoritesOnlyFilter, sortOrder) {
        val base = contacts.filter { contact ->
            val favOk = !favoritesOnlyFilter || contact.isStarred
            val q = searchQuery.trim()
            val queryOk = if (q.isEmpty()) {
                true
            } else {
                contact.displayName.contains(q, ignoreCase = true) ||
                    contact.company.contains(q, ignoreCase = true) ||
                    contact.jobTitle.contains(q, ignoreCase = true) ||
                    contact.phones.any { it.number.contains(q, ignoreCase = true) }
            }
            favOk && queryOk
        }

        when (sortOrder) {
            ContactSortOrder.NAME_ASC -> base.sortedWith(
                compareBy<ContactItem> {
                    val letter = it.sectionLetter
                    if (letter == "#") "ZZZ" else letter
                }.thenBy { it.displayName.lowercase() }
            )
            ContactSortOrder.NAME_DESC -> base.sortedByDescending { it.displayName.lowercase() }
            ContactSortOrder.RECENTLY_ADDED -> base.sortedByDescending {
                if (it.lastUpdatedTimestamp > 0L) it.lastUpdatedTimestamp else it.id
            }
            ContactSortOrder.FREQUENTLY_CONTACTED -> base.sortedWith(
                compareByDescending<ContactItem> { it.timesContacted }
                    .thenBy { it.displayName.lowercase() }
            )
        }
    }

    val favoriteContacts = remember(filteredAndSortedContacts) {
        filteredAndSortedContacts.filter { it.isStarred }
    }

    val groupedByLetter = remember(filteredAndSortedContacts, sortOrder) {
        val grouped = LinkedHashMap<String, List<ContactItem>>()
        if (sortOrder == ContactSortOrder.NAME_ASC || sortOrder == ContactSortOrder.NAME_DESC) {
            filteredAndSortedContacts.groupBy { it.sectionLetter }.forEach { (letter, items) ->
                grouped[letter] = items
            }
        } else {
            val sectionTitle = sortOrder.label
            grouped[sectionTitle] = filteredAndSortedContacts
        }
        grouped
    }

    val showDuplicateBanner = !isSearchActive && duplicateGroups.isNotEmpty() && !duplicateBannerDismissed

    // Build flat item index lookup for A-Z fast scroll
    val sectionIndexMap = remember(
        favoriteContacts,
        groupedByLetter,
        isSearchActive,
        recentlyViewedContacts,
        showDuplicateBanner
    ) {
        val map = mutableMapOf<String, Int>()
        var currentIndex = 0
        if (!isSearchActive) {
            if (showDuplicateBanner) currentIndex += 1
            currentIndex += 1 // My profile
            if (recentlyViewedContacts.isNotEmpty()) {
                currentIndex += 1 // Recently viewed row
            }
        }
        if (favoriteContacts.isNotEmpty() && !favoritesOnlyFilter) {
            map["☆"] = currentIndex
            currentIndex += 1 + favoriteContacts.size
        }
        groupedByLetter.forEach { (letter, list) ->
            map[letter] = currentIndex
            currentIndex += 1 + list.size
        }
        map
    }

    LiquidGlassBackdrop(
        config = personalization,
        forceStudioAtmosphere = false,
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: Search & Overflow Menu (or ColorOS 16 Search Pill + Cancel)
            if (isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = surfaceVariantColor,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(ColorOsGreen),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Search contacts",
                                                color = textSecondary,
                                                fontSize = 15.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("contacts_search_input")
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Cancel",
                        color = ColorOsGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                searchQuery = ""
                                isSearchActive = false
                            }
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isSearchActive = true },
                        modifier = Modifier.testTag("contacts_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search contacts",
                            tint = textPrimary,
                            modifier = Modifier.size(25.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.testTag("contacts_overflow_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = textPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            shape = RoundedCornerShape(18.dp),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sort by: ${sortOrder.label}", fontSize = 15.sp) },
                                onClick = {
                                    showOverflowMenu = false
                                    showSortDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    val count = duplicateGroups.size
                                    Text(
                                        if (count > 0) "Merge duplicates ($count)" else "Duplicate contacts",
                                        fontSize = 15.sp
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showDuplicatesDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Import contacts (.vcf)", fontSize = 15.sp) },
                                onClick = {
                                    showOverflowMenu = false
                                    onImportContactsVcf()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export contacts (.vcf)", fontSize = 15.sp) },
                                onClick = {
                                    showOverflowMenu = false
                                    onExportContactsVcf()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Manual contacts sync", fontSize = 15.sp) },
                                onClick = {
                                    showOverflowMenu = false
                                    onManualSyncContacts()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (recycleBinCount > 0) {
                                            "Deleted contacts ($recycleBinCount)"
                                        } else {
                                            "Deleted contacts (Recycle bin)"
                                        },
                                        fontSize = 15.sp
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenRecycleBin()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Personalization Studio", fontSize = 15.sp) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenPersonalizationStudio()
                                },
                                modifier = Modifier.testTag("contacts_menu_personalization_studio")
                            )
                            DropdownMenuItem(
                                text = { Text("Settings", fontSize = 15.sp) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenSettings()
                                }
                            )
                        }
                    }
                }
            }

            // Large bold "Contacts" heading + contact count + All / Favorites filter pills
            if (!isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 20.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Contacts",
                            color = textPrimary,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            modifier = Modifier.testTag("contacts_screen_heading")
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${contacts.size} contacts",
                            color = textSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.testTag("contacts_count_label")
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = if (!favoritesOnlyFilter) {
                                primaryAccent.copy(alpha = 0.14f)
                            } else {
                                surfaceVariantColor
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .clickable { favoritesOnlyFilter = false }
                                .testTag("contacts_filter_all")
                        ) {
                            Text(
                                text = "All",
                                color = if (!favoritesOnlyFilter) primaryAccent else textSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (!favoritesOnlyFilter) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        Surface(
                            color = if (favoritesOnlyFilter) {
                                ColorOsStarGold.copy(alpha = 0.18f)
                            } else {
                                surfaceVariantColor
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .clickable { favoritesOnlyFilter = true }
                                .testTag("contacts_filter_favorites")
                        ) {
                            Text(
                                text = "Favorites",
                                color = if (favoritesOnlyFilter) Color(0xFFD69900) else textSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (favoritesOnlyFilter) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (!hasContactsPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Contacts permission required",
                        color = textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Allow Contacts access to load, search, favorite, and manage your contacts.",
                        color = textSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onRequestContactsPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = ColorOsGreen),
                        modifier = Modifier.testTag("grant_contacts_permission_btn")
                    ) {
                        Text("Allow Contacts Access", color = ColorOsWhite)
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 22.dp)
                            .testTag("contacts_list"),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        if (!isSearchActive) {
                            // ColorOS 16 Duplicate contacts banner card (matches IMG-20261107-WA0013)
                            if (showDuplicateBanner) {
                                item(
                                    key = "duplicate_contacts_banner",
                                    contentType = "duplicate_banner"
                                ) {
                                    Surface(
                                        color = surfaceVariantColor,
                                        shape = RoundedCornerShape(18.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 6.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Outlined.ContentCopy,
                                                    contentDescription = null,
                                                    tint = textSecondary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = "Duplicate contacts have been found.",
                                                    color = textSecondary,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Dismiss",
                                                    color = ColorOsGreen,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { duplicateBannerDismissed = true }
                                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = "Merge",
                                                    color = ColorOsGreen,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { showDuplicatesDialog = true }
                                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // "My profile" row with OPPO Green circular avatar (matches IMG-20261107-WA0013)
                            item(
                                key = "row_my_profile",
                                contentType = "my_profile_row"
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showMyProfileDialog = true }
                                            .padding(horizontal = 24.dp, vertical = 12.dp)
                                            .testTag("my_profile_row"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(primaryAccent),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Person,
                                                contentDescription = "My profile",
                                                tint = ColorOsWhite,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                text = "My profile",
                                                color = textPrimary,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Normal
                                            )
                                            if (myProfile.name.isNotBlank()) {
                                                Text(
                                                    text = myProfile.name,
                                                    color = textSecondary,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(
                                        color = dividerColor,
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(start = 82.dp, end = 16.dp)
                                    )
                                }
                            }

                            // Recently Viewed Contacts (last 10)
                            if (recentlyViewedContacts.isNotEmpty()) {
                                item(
                                    key = "recently_viewed_section",
                                    contentType = "recently_viewed_row"
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, bottom = 6.dp)
                                    ) {
                                        Text(
                                            text = "Recently viewed",
                                            color = textSecondary,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(
                                                start = 24.dp,
                                                bottom = 8.dp
                                            )
                                        )
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 24.dp),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            items(
                                                items = recentlyViewedContacts,
                                                key = { "recent_view_${it.id}" },
                                                contentType = { "recent_view_chip" }
                                            ) { rc ->
                                                Column(
                                                    modifier = Modifier
                                                        .width(56.dp)
                                                        .clickable { onOpenContact(rc.id) },
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    ContactCircleAvatar(
                                                        name = rc.displayName,
                                                        photoUri = rc.photoUri,
                                                        size = 42.dp,
                                                        fontSize = 18.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = rc.displayName,
                                                        color = textPrimary,
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Favorites section (Gold star header + favorite contacts)
                        if (favoriteContacts.isNotEmpty() && !favoritesOnlyFilter) {
                            item(
                                key = "header_favorites",
                                contentType = "section_header_star"
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 24.dp, top = 12.dp, bottom = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = "Favorites",
                                        tint = ColorOsStarGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            items(
                                items = favoriteContacts,
                                key = { "fav_contact_${it.id}" },
                                contentType = { "contact_row" }
                            ) { contact ->
                                ContactListRow(
                                    contact = contact,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    dividerColor = dividerColor,
                                    onClick = { onOpenContact(contact.id) }
                                )
                            }
                        }

                        // Alphabetical or Sorted sections
                        groupedByLetter.forEach { (letter, sectionContacts) ->
                            item(
                                key = "header_section_$letter",
                                contentType = "section_header_letter"
                            ) {
                                Text(
                                    text = letter,
                                    color = textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(
                                        start = 24.dp,
                                        top = 14.dp,
                                        bottom = 4.dp
                                    )
                                )
                            }

                            items(
                                items = sectionContacts,
                                key = { "sec_${letter}_contact_${it.id}" },
                                contentType = { "contact_row" }
                            ) { contact ->
                                ContactListRow(
                                    contact = contact,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    dividerColor = dividerColor,
                                    onClick = { onOpenContact(contact.id) }
                                )
                            }
                        }

                        if (filteredAndSortedContacts.isEmpty()) {
                            item(
                                key = "empty_contacts",
                                contentType = "empty_state"
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp, horizontal = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (searchQuery.isNotBlank() || favoritesOnlyFilter) {
                                            "No matching contacts found"
                                        } else {
                                            "No contacts on this device yet. Tap + to create one."
                                        },
                                        color = textSecondary,
                                        fontSize = 15.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Right-side A–Z Fast-Scroll Index Bar
                    if (!isSearchActive) {
                        AlphabetFastScrollBar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 6.dp, top = 8.dp, bottom = 76.dp),
                            onLetterSelected = { letter ->
                                val targetIdx = sectionIndexMap[letter]
                                if (targetIdx != null) {
                                    coroutineScope.launch {
                                        listState.scrollToItem(targetIdx)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Floating Add-Contact Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 26.dp, bottom = 22.dp)
                .size(58.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(primaryAccent)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAddContactClick()
                }
                .testTag("fab_add_contact"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New contact",
                tint = ColorOsWhite,
                modifier = Modifier.size(30.dp)
            )
        }
        }
    }

    // Contact Sorting Dialog
    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Sort contacts by", color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ContactSortOrder.entries.forEach { option ->
                        Surface(
                            color = if (option == sortOrder) {
                                ColorOsGreen.copy(alpha = 0.14f)
                            } else {
                                surfaceVariantColor
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onChangeSortOrder(option)
                                    showSortDialog = false
                                }
                        ) {
                            Text(
                                text = if (option == sortOrder) "✓ ${option.label}" else option.label,
                                fontSize = 15.sp,
                                color = textPrimary,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("Close", color = ColorOsGreen)
                }
            }
        )
    }

    // Duplicate Contact Identification & Merge Dialog
    if (showDuplicatesDialog) {
        AlertDialog(
            onDismissRequest = { showDuplicatesDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Duplicate contacts", color = textPrimary) },
            text = {
                if (duplicateGroups.isEmpty()) {
                    Text("No duplicate contacts detected on your device.", color = textSecondary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        duplicateGroups.take(6).forEach { group ->
                            Surface(
                                color = surfaceVariantColor,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = group.keyLabel,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = textPrimary
                                        )
                                        Text(
                                            text = group.contacts.joinToString(", ") { it.displayName },
                                            fontSize = 12.sp,
                                            color = textSecondary
                                        )
                                    }
                                    TextButton(onClick = { onMergeDuplicateGroup(group) }) {
                                        Text("Merge", color = ColorOsGreen, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDuplicatesDialog = false }) {
                    Text("Done", color = ColorOsGreen)
                }
            }
        )
    }

    // My Profile Dialog
    if (showMyProfileDialog) {
        var profileName by remember(myProfile) { mutableStateOf(myProfile.name) }
        var profilePhone by remember(myProfile) { mutableStateOf(myProfile.number) }
        var profileTitle by remember(myProfile) { mutableStateOf(myProfile.jobTitle) }
        var profileCompany by remember(myProfile) { mutableStateOf(myProfile.company) }

        AlertDialog(
            onDismissRequest = { showMyProfileDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("My profile", color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = profilePhone,
                        onValueChange = { profilePhone = it },
                        label = { Text("Phone number") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = profileTitle,
                        onValueChange = { profileTitle = it },
                        label = { Text("Job title") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = profileCompany,
                        onValueChange = { profileCompany = it },
                        label = { Text("Company") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveMyProfile(
                            myProfile.copy(
                                name = profileName,
                                number = profilePhone,
                                jobTitle = profileTitle,
                                company = profileCompany
                            )
                        )
                        showMyProfileDialog = false
                    }
                ) {
                    Text("Save", color = ColorOsGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMyProfileDialog = false }) {
                    Text("Close", color = textSecondary)
                }
            }
        )
    }
}

@Composable
private fun ContactListRow(
    contact: ContactItem,
    textPrimary: Color,
    textSecondary: Color,
    dividerColor: Color,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .testTag("contact_row_${contact.id}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactCircleAvatar(
                name = contact.displayName,
                photoUri = contact.photoUri,
                size = 42.dp,
                fontSize = 19.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            val showCompanySubtitle = LocalPersonalizationConfig.current.contacts.showCompanySubtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.displayName,
                    color = textPrimary,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showCompanySubtitle && !contact.subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = contact.subtitle.orEmpty(),
                        color = textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        HorizontalDivider(
            color = dividerColor,
            thickness = 0.5.dp,
            modifier = Modifier.padding(start = 82.dp, end = 16.dp)
        )
    }
}

@Composable
private fun AlphabetFastScrollBar(
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var barHeightPx by remember { mutableFloatStateOf(1f) }
    val indexTint = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .width(18.dp)
            .fillMaxHeight()
            .onSizeChanged { barHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    val fraction = (change.position.y / barHeightPx).coerceIn(0f, 0.999f)
                    val index = (fraction * ALPHABET_INDEX.size).toInt()
                    onLetterSelected(ALPHABET_INDEX[index])
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        ALPHABET_INDEX.forEach { symbol ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLetterSelected(symbol) },
                contentAlignment = Alignment.Center
            ) {
                if (symbol == "☆") {
                    Icon(
                        imageVector = Icons.Outlined.StarOutline,
                        contentDescription = "Favorites index",
                        tint = indexTint,
                        modifier = Modifier.size(10.dp)
                    )
                } else {
                    Text(
                        text = symbol,
                        color = indexTint,
                        fontSize = 9.5.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
