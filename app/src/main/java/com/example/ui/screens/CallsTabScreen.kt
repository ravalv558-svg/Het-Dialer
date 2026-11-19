package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.PersonalizationManager
import com.example.model.AppBackgroundStyle
import com.example.model.CallDirection
import com.example.model.CallDirectionIconStyle
import com.example.model.CallHistoryAppearanceConfig
import com.example.model.CallHistoryLayoutStyle
import com.example.model.CallLogItem
import com.example.model.ContactItem
import com.example.model.DialpadButtonVisualStyle
import com.example.ui.components.CallDirectionArrowIcon
import com.example.ui.components.ContactCircleAvatar
import com.example.ui.components.HdBadge
import com.example.ui.components.OutlinedBackspaceIcon
import com.example.ui.components.OutlinedInfoIcon
import com.example.ui.components.TenDotKeypadIcon
import com.example.ui.components.VoLteBadge
import com.example.ui.components.VoicemailReelIcon
import com.example.ui.personalization.LiquidGlassBackdrop
import com.example.ui.personalization.LiquidGlassCard
import com.example.ui.theme.ColorOsGreen
import com.example.ui.theme.ColorOsRed
import com.example.ui.theme.ColorOsWhite
import com.example.ui.theme.LocalPersonalizationConfig
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class CallFilter(val label: String) {
    ALL("All calls"),
    MISSED("Missed calls"),
    INCOMING("Incoming calls"),
    OUTGOING("Outgoing calls")
}

/**
 * OPPO ColorOS 16 Phone / Call History Screen & Expandable Dialpad:
 * - Top centered segmented capsule ("All" | "Missed") + Search icon + 3-dot menu ("Edit", "Block & filter", "Manage contacts", "Settings")
 * - Large bold "Phone" heading
 * - Call log rows with direction arrows, VoLTE & HD badges, formatted time/date, and (i) info button
 * - Clean ColorOS 16 Dialpad with large numeric keys, alphabet sub-labels, Paste/Copy/Formatting, and green Call button
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallsTabScreen(
    callLogs: List<CallLogItem>,
    contacts: List<ContactItem>,
    dialInput: String,
    formattedDialInput: String,
    isDialPadExpanded: Boolean,
    hasCallLogPermission: Boolean,
    isDefaultDialer: Boolean,
    onDialDigit: (Char) -> Unit,
    onSetDialInput: (String) -> Unit,
    onBackspace: () -> Unit,
    onClearDialInput: () -> Unit,
    onToggleDialPad: (Boolean) -> Unit,
    onPlaceCall: (String) -> Unit,
    onOpenCallDetails: (CallLogItem) -> Unit,
    onOpenContactDetails: (Long) -> Unit,
    onDeleteCallLogItem: (Long) -> Unit,
    onClearAllCallHistory: () -> Unit,
    onCreateContactWithNumber: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBlockedNumbers: () -> Unit,
    onRequestCallLogPermission: () -> Unit,
    onRequestDefaultDialerRole: () -> Unit,
    onOpenManageContacts: () -> Unit = onOpenSettings,
    onOpenPersonalizationStudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val personalization = LocalPersonalizationConfig.current
    val useBackdrop = personalization.homeDialer.backgroundStyle != AppBackgroundStyle.THEME_SOLID
    val primaryAccent = if (personalization.useCustomColors) personalization.primaryColor else ColorOsGreen

    var showOverflowMenu by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf(CallFilter.ALL) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var selectedLogForAction by remember { mutableStateOf<CallLogItem?>(null) }

    val baseCallLogsByFilter = remember(callLogs, activeFilter) {
        when (activeFilter) {
            CallFilter.ALL -> callLogs
            CallFilter.MISSED -> callLogs.filter { it.direction == CallDirection.MISSED }
            CallFilter.INCOMING -> callLogs.filter { it.direction == CallDirection.INCOMING }
            CallFilter.OUTGOING -> callLogs.filter { it.direction == CallDirection.OUTGOING }
        }
    }

    val filteredCallLogs = remember(baseCallLogsByFilter, searchQuery, dialInput, isSearchActive) {
        val query = if (isSearchActive) searchQuery.trim() else dialInput.trim()
        if (query.isEmpty()) {
            baseCallLogsByFilter
        } else {
            val cleanDigits = extractDialSearchChars(query)
            baseCallLogsByFilter.filter { item ->
                val matchesName = item.displayTitle.contains(query, ignoreCase = true) ||
                    matchesT9Name(item.displayTitle, cleanDigits)
                val matchesNumber = cleanDigits.isNotEmpty() &&
                    containsDialDigits(item.number, cleanDigits)
                matchesName || matchesNumber
            }
        }
    }

    val dialPadMatchedContacts = remember(contacts, dialInput) {
        val clean = dialInput.trim()
        if (clean.isEmpty()) {
            emptyList()
        } else {
            val cleanDigits = extractDialSearchChars(clean)
            val result = ArrayList<ContactItem>(6)
            for (contact in contacts) {
                val nameMatch = contact.displayName.contains(clean, ignoreCase = true) ||
                    matchesT9Name(contact.displayName, cleanDigits)
                val phoneMatch = cleanDigits.isNotEmpty() && contact.phones.any { phone ->
                    containsDialDigits(phone.number, cleanDigits)
                }
                if (nameMatch || phoneMatch) {
                    result.add(contact)
                    if (result.size >= 6) break
                }
            }
            result
        }
    }

    val bgColor = if (useBackdrop) Color.Transparent else MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val pillContainerColor = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outline

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
            // Top Bar: Centered "All | Missed" Segmented Capsule + Search & 3-Dot Overflow Menu
            if (isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = pillContainerColor,
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
                                    fontSize = 16.sp
                                ),
                                cursorBrush = SolidColor(ColorOsGreen),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Search calls or contacts",
                                                color = textSecondary,
                                                fontSize = 16.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("calls_search_input")
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
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                searchQuery = ""
                                isSearchActive = false
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Centered ColorOS 16 "All | Missed" Segmented Capsule
                    Surface(
                        color = pillContainerColor,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(192.dp)
                            .height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isAllSelected = activeFilter != CallFilter.MISSED
                            val allTabBg by animateColorAsState(
                                targetValue = if (isAllSelected) surfaceColor else Color.Transparent,
                                animationSpec = tween(180),
                                label = "all_tab_bg"
                            )
                            val missedTabBg by animateColorAsState(
                                targetValue = if (!isAllSelected) surfaceColor else Color.Transparent,
                                animationSpec = tween(180),
                                label = "missed_tab_bg"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(19.dp))
                                    .background(allTabBg)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        activeFilter = CallFilter.ALL
                                    }
                                    .testTag("filter_pill_all_calls"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "All",
                                    color = if (isAllSelected) textPrimary else textSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isAllSelected) FontWeight.Medium else FontWeight.Normal
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(19.dp))
                                    .background(missedTabBg)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        activeFilter = CallFilter.MISSED
                                    }
                                    .testTag("filter_pill_missed_calls"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Missed",
                                    color = if (!isAllSelected) textPrimary else textSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = if (!isAllSelected) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // Right-aligned Search & 3-Dot Overflow Menu
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                onToggleDialPad(false)
                                isSearchActive = true
                            },
                            modifier = Modifier.testTag("calls_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search calls",
                                tint = textPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.testTag("calls_overflow_button")
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
                                containerColor = surfaceColor
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Edit",
                                            color = textPrimary,
                                            fontSize = 16.sp
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        if (callLogs.isNotEmpty()) {
                                            showClearHistoryConfirm = true
                                        }
                                    }
                                )
                                HorizontalDivider(
                                    color = dividerColor.copy(alpha = 0.6f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Block & filter",
                                            color = textPrimary,
                                            fontSize = 16.sp
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        onOpenBlockedNumbers()
                                    }
                                )
                                HorizontalDivider(
                                    color = dividerColor.copy(alpha = 0.6f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Manage contacts",
                                            color = textPrimary,
                                            fontSize = 16.sp
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        onOpenManageContacts()
                                    }
                                )
                                HorizontalDivider(
                                    color = dividerColor.copy(alpha = 0.6f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Personalization Studio",
                                            color = primaryAccent,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        onOpenPersonalizationStudio()
                                    },
                                    modifier = Modifier.testTag("calls_menu_personalization_studio")
                                )
                                HorizontalDivider(
                                    color = dividerColor.copy(alpha = 0.6f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Settings",
                                            color = textPrimary,
                                            fontSize = 16.sp
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        onOpenSettings()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Large Bold "Phone" Heading matching IMG-20261107-WA0012
            if (!isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Phone",
                        color = textPrimary,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("calls_screen_heading")
                    )
                }
            }

            // Default dialer status banner when not yet default
            if (!isDefaultDialer) {
                Surface(
                    color = ColorOsGreen.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Not set as Default Phone App",
                                color = textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Enable to handle incoming SIM 1 calls in Phone",
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = ColorOsGreen,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .clickable { onRequestDefaultDialerRole() }
                                .testTag("banner_set_default_dialer")
                        ) {
                            Text(
                                text = "Set Default",
                                color = ColorOsWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Call History List
            if (!hasCallLogPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Call history permission required",
                        color = textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Grant Call Log permission to view your incoming, outgoing, and missed calls.",
                        color = textSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onRequestCallLogPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = ColorOsGreen),
                        modifier = Modifier.testTag("grant_call_log_permission_btn")
                    ) {
                        Text("Allow Call Log Access", color = ColorOsWhite)
                    }
                }
            } else {
                val density = LocalDensity.current
                val currentIsDialPadExpanded by rememberUpdatedState(isDialPadExpanded)
                val currentOnToggleDialPad by rememberUpdatedState(onToggleDialPad)
                val upwardSwipeThresholdPx = remember(density) { with(density) { 36.dp.toPx() } }
                val upwardFlingVelocityThresholdPx = remember(density) { with(density) { 380.dp.toPx() } }
                var accumulatedUpwardScrollPx by remember { mutableFloatStateOf(0f) }

                val callHistoryNestedScrollConnection = remember(upwardSwipeThresholdPx, upwardFlingVelocityThresholdPx) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (source == NestedScrollSource.UserInput && currentIsDialPadExpanded) {
                                if (available.y < 0f) {
                                    accumulatedUpwardScrollPx += available.y
                                    if (shouldAutoHideDialPadOnCallHistorySwipe(
                                            isDialPadExpanded = currentIsDialPadExpanded,
                                            accumulatedUpwardDeltaPx = accumulatedUpwardScrollPx,
                                            distanceThresholdPx = upwardSwipeThresholdPx
                                        )
                                    ) {
                                        accumulatedUpwardScrollPx = 0f
                                        currentOnToggleDialPad(false)
                                    }
                                } else if (available.y > 0f) {
                                    // Reset on downward drag; downward swipe must never open or toggle the Dialpad
                                    accumulatedUpwardScrollPx = 0f
                                }
                            } else if (!currentIsDialPadExpanded) {
                                accumulatedUpwardScrollPx = 0f
                            }
                            // Never consume scroll delta so Call History scrolls continuously and naturally
                            return Offset.Zero
                        }

                        override suspend fun onPreFling(available: Velocity): Velocity {
                            if (shouldAutoHideDialPadOnCallHistorySwipe(
                                    isDialPadExpanded = currentIsDialPadExpanded,
                                    accumulatedUpwardDeltaPx = accumulatedUpwardScrollPx,
                                    flingVelocityY = available.y,
                                    distanceThresholdPx = upwardSwipeThresholdPx,
                                    velocityThresholdPxPerSec = upwardFlingVelocityThresholdPx
                                )
                            ) {
                                accumulatedUpwardScrollPx = 0f
                                currentOnToggleDialPad(false)
                            } else {
                                accumulatedUpwardScrollPx = 0f
                            }
                            return Velocity.Zero
                        }
                    }
                }

                val callHistoryBottomPadding by animateDpAsState(
                    targetValue = if (isDialPadExpanded) 430.dp else 92.dp,
                    animationSpec = tween(durationMillis = 220),
                    label = "call_history_bottom_padding"
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(callHistoryNestedScrollConnection)
                        .pointerInput(upwardSwipeThresholdPx) {
                            // Passive upward-swipe observer (never consumes pointer changes) so swiping upward
                            // over Call History collapses the Dialpad even when the list is short or at the bottom,
                            // without interfering with item taps, long-presses, (i) info buttons, or scrolling.
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial
                                )
                                val pointerId = down.id
                                var totalDx = 0f
                                var totalDy = 0f
                                var upwardDragAccumulatedPx = 0f
                                var hasCollapsedInThisGesture = false

                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                        ?: event.changes.firstOrNull()
                                        ?: break
                                    if (!change.pressed) break

                                    val dx = change.position.x - change.previousPosition.x
                                    val dy = change.position.y - change.previousPosition.y
                                    totalDx += dx
                                    totalDy += dy

                                    if (dy < 0f) {
                                        upwardDragAccumulatedPx += dy
                                    } else if (dy > 0f) {
                                        upwardDragAccumulatedPx = 0f
                                    }

                                    val isPredominantlyVertical =
                                        kotlin.math.abs(totalDy) > kotlin.math.abs(totalDx) * 1.15f

                                    if (!hasCollapsedInThisGesture &&
                                        isPredominantlyVertical &&
                                        shouldAutoHideDialPadOnCallHistorySwipe(
                                            isDialPadExpanded = currentIsDialPadExpanded,
                                            accumulatedUpwardDeltaPx = upwardDragAccumulatedPx,
                                            distanceThresholdPx = upwardSwipeThresholdPx
                                        )
                                    ) {
                                        hasCollapsedInThisGesture = true
                                        accumulatedUpwardScrollPx = 0f
                                        currentOnToggleDialPad(false)
                                    }
                                }
                            }
                        }
                        .testTag("call_log_list"),
                    contentPadding = PaddingValues(
                        bottom = callHistoryBottomPadding
                    )
                ) {
                    if (dialInput.isNotBlank()) {
                        item(
                            key = "add_to_contacts_$dialInput",
                            contentType = "add_to_contacts_row"
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCreateContactWithNumber(dialInput) }
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                                    .testTag("dialpad_save_number_row"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = "Create contact",
                                    tint = ColorOsGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "Create new contact ($formattedDialInput)",
                                    color = ColorOsGreen,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        items(
                            items = dialPadMatchedContacts,
                            key = { "match_contact_${it.id}" },
                            contentType = { "matched_contact_row" }
                        ) { matchedContact ->
                            val primaryNum = matchedContact.primaryPhone.orEmpty()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (primaryNum.isNotBlank()) {
                                            onPlaceCall(primaryNum)
                                        } else {
                                            onOpenContactDetails(matchedContact.id)
                                        }
                                    }
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ContactCircleAvatar(
                                    name = matchedContact.displayName,
                                    photoUri = matchedContact.photoUri,
                                    size = 38.dp,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = matchedContact.displayName,
                                        color = textPrimary,
                                        fontSize = 16.sp
                                    )
                                    if (primaryNum.isNotBlank()) {
                                        Text(
                                            text = primaryNum,
                                            color = textSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onOpenContactDetails(matchedContact.id) }
                                ) {
                                    OutlinedInfoIcon(tint = textPrimary)
                                }
                            }
                        }
                    }

                    if (filteredCallLogs.isEmpty() && dialPadMatchedContacts.isEmpty()) {
                        item(
                            key = "empty_call_logs",
                            contentType = "empty_state"
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 56.dp, horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when {
                                        dialInput.isNotBlank() || searchQuery.isNotBlank() ->
                                            "No matching calls found"
                                        activeFilter == CallFilter.MISSED ->
                                            "No missed calls"
                                        else ->
                                            "No recent calls on this device"
                                    },
                                    color = textSecondary,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    } else {
                        items(
                            items = filteredCallLogs,
                            key = { "call_log_${it.id}" },
                            contentType = { "call_log_row" }
                        ) { item ->
                            CallLogRow(
                                item = item,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                dividerColor = dividerColor,
                                onCallClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onPlaceCall(item.number)
                                },
                                onLongClick = { selectedLogForAction = item },
                                onInfoClick = { onOpenCallDetails(item) }
                            )
                        }
                    }
                }
            }
        }

        // Expandable OPPO ColorOS 16 Dialpad Sheet
        AnimatedVisibility(
            visible = isDialPadExpanded,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(240)
            ) + expandVertically(
                expandFrom = Alignment.Bottom,
                animationSpec = tween(240)
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(220)
            ) + shrinkVertically(
                shrinkTowards = Alignment.Bottom,
                animationSpec = tween(220)
            ) + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ColorOsDialPadSheet(
                dialInput = dialInput,
                formattedDialInput = formattedDialInput,
                liveMatchCount = dialPadMatchedContacts.size + filteredCallLogs.size,
                onDigitClick = { digit ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDialDigit(digit)
                },
                onPasteText = { pasted ->
                    onSetDialInput(pasted)
                },
                onBackspace = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBackspace()
                },
                onClearAll = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClearDialInput()
                },
                onCollapseDialPad = { onToggleDialPad(false) },
                onCallClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (dialInput.isNotBlank()) {
                        onPlaceCall(dialInput)
                    } else {
                        val lastNumber = callLogs.firstOrNull()?.number.orEmpty()
                        if (lastNumber.isNotBlank()) {
                            onSetDialInput(lastNumber)
                        }
                    }
                }
            )
        }

        // Floating Dialpad Re-Expand Button when collapsed
        if (!isDialPadExpanded) {
            val fabShape = PersonalizationManager.shapeFor(personalization.homeDialer.dialpadButtonShape, 58f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 20.dp)
                    .size(58.dp)
                    .shadow(8.dp, fabShape, spotColor = primaryAccent.copy(alpha = 0.5f))
                    .clip(fabShape)
                    .background(primaryAccent)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleDialPad(true)
                    }
                    .testTag("fab_expand_dialpad"),
                contentAlignment = Alignment.Center
            ) {
                TenDotKeypadIcon(tint = ColorOsWhite)
            }
        }
        }
    }

    // Individual Call Log Action Dialog (Delete entry / Copy number)
    selectedLogForAction?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedLogForAction = null },
            containerColor = surfaceColor,
            shape = RoundedCornerShape(22.dp),
            title = { Text(item.displayTitle, color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Number: ${item.formattedNumber.ifBlank { item.number }}",
                        color = textSecondary
                    )
                    if (item.durationSeconds > 0) {
                        Text(
                            text = "Duration: ${formatShortDuration(item.durationSeconds)}",
                            color = textSecondary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = item.id
                        selectedLogForAction = null
                        onDeleteCallLogItem(id)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = ColorOsRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete entry", color = ColorOsRed)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("Phone", item.number))
                            selectedLogForAction = null
                        }
                    ) {
                        Text("Copy number", color = ColorOsGreen)
                    }
                    TextButton(onClick = { selectedLogForAction = null }) {
                        Text("Close", color = textSecondary)
                    }
                }
            }
        )
    }

    // Clear All Call History Confirmation Dialog
    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            containerColor = surfaceColor,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Clear call history?", color = textPrimary) },
            text = {
                Text(
                    "All call history entries will be removed from this list.",
                    color = textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHistoryConfirm = false
                        onClearAllCallHistory()
                    }
                ) {
                    Text("Clear", color = ColorOsRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }
}

/**
 * OPPO ColorOS 16 Call History Row matching `IMG-20261107-WA0012` & `IMG-20261107-WA0015`:
 * - Left direction arrow (`↖`, `↘`, or `⊘`) aligned with the primary caller title.
 * - Primary title (Contact Name or Phone Number, red when Missed).
 * - Subtitle row with `VoLTE` badge, optional `HD` badge, phone number / geocoded location, and call duration.
 * - Right-aligned timestamp (`9:45 pm` or `04/11/26`) and outlined circle `(i)` info icon.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallLogRow(
    item: CallLogItem,
    textPrimary: Color,
    textSecondary: Color,
    dividerColor: Color,
    onCallClick: () -> Unit,
    onLongClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val personalization = LocalPersonalizationConfig.current
    val historyConfig: CallHistoryAppearanceConfig = personalization.callHistory
    val isMissed = item.direction == CallDirection.MISSED
    val titleColor = if (isMissed) Color(historyConfig.missedCallColorArgb) else textPrimary
    val formattedTime = remember(item.timestamp) { formatCallLogTimestamp(item.timestamp) }
    val durationLabel = remember(item.durationSeconds, historyConfig.showCallDuration) {
        if (historyConfig.showCallDuration && item.durationSeconds > 0L) {
            " · ${formatShortDuration(item.durationSeconds)}"
        } else {
            ""
        }
    }
    val verticalPad = if (historyConfig.layoutStyle == CallHistoryLayoutStyle.COMPACT_DENSE) 8.dp else 13.dp

    val rowBody: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onCallClick,
                    onLongClick = onLongClick
                )
                .padding(start = 20.dp, end = 12.dp, top = verticalPad, bottom = verticalPad)
                .testTag("call_log_row_${item.id}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Direction indicator styled per PersonalizationConfig
            Box(
                modifier = Modifier
                    .padding(end = 14.dp, bottom = 14.dp)
                    .size(22.dp),
                contentAlignment = Alignment.Center
            ) {
                when (historyConfig.directionIconStyle) {
                    CallDirectionIconStyle.CLASSIC_ARROWS -> {
                        CallDirectionArrowIcon(direction = item.direction)
                    }
                    CallDirectionIconStyle.BADGE_PILLS -> {
                        val badgeBg = when (item.direction) {
                            CallDirection.MISSED -> Color(historyConfig.missedCallColorArgb).copy(alpha = 0.16f)
                            CallDirection.OUTGOING -> personalization.primaryColor.copy(alpha = 0.16f)
                            else -> Color(0xFF10B981).copy(alpha = 0.16f)
                        }
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeBg),
                            contentAlignment = Alignment.Center
                        ) {
                            CallDirectionArrowIcon(direction = item.direction)
                        }
                    }
                    CallDirectionIconStyle.MINIMAL_DOTS -> {
                        val dotColor = when (item.direction) {
                            CallDirection.MISSED -> Color(historyConfig.missedCallColorArgb)
                            CallDirection.OUTGOING -> personalization.primaryColor
                            else -> Color(0xFF10B981)
                        }
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
            }

            // Center: Caller Name/Number + VoLTE / HD / Subtitle / Duration
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayTitle,
                    color = titleColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (historyConfig.showVoLteAndHdBadges) {
                        VoLteBadge(tint = textSecondary.copy(alpha = 0.78f))
                        Spacer(modifier = Modifier.width(6.dp))
                        if (item.isHd) {
                            HdBadge()
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                    }
                    Text(
                        text = "${item.displaySubtitle}$durationLabel",
                        color = textSecondary,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: Timestamp + Outlined (i) info button
            Text(
                text = formattedTime,
                color = textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp)
            )

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onInfoClick() }
                    .testTag("call_log_info_${item.id}"),
                contentAlignment = Alignment.Center
            ) {
                OutlinedInfoIcon(tint = textPrimary)
            }
        }
    }

    if (historyConfig.layoutStyle == CallHistoryLayoutStyle.GLASS_CARDS) {
        LiquidGlassCard(
            config = personalization,
            cornerRadius = historyConfig.rowCornerRadiusDp.dp,
            contentPadding = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            rowBody()
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            rowBody()
            HorizontalDivider(
                color = dividerColor.copy(alpha = 0.65f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(start = 50.dp, end = 20.dp)
            )
        }
    }
}

/**
 * OPPO ColorOS 16 Expandable Dialpad Sheet matching `IMG-20261107-WA0014` & `IMG-20261107-WA0020`:
 * - Rounded white/dark-elevated bottom sheet (`RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`)
 * - Top centered grey grab handle bar
 * - Number entry bar with live number formatting, Paste action, and Copy action
 * - Clean, spacious 3x4 numeric buttons with alphabet labels (`مه` under `1`, `ABC` under `2`, ..., `+` under `0`)
 * - Bottom row: 10-dot collapse icon (left), large green circular Call button (center), and grey tag Backspace icon (right)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColorOsDialPadSheet(
    dialInput: String,
    formattedDialInput: String,
    liveMatchCount: Int,
    onDigitClick: (Char) -> Unit,
    onPasteText: (String) -> Unit,
    onBackspace: () -> Unit,
    onClearAll: () -> Unit,
    onCollapseDialPad: () -> Unit,
    onCallClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val personalization = LocalPersonalizationConfig.current
    val isGlassSheet = personalization.homeDialer.dialpadGlassSheet

    val surfaceColor = if (isGlassSheet) {
        MaterialTheme.colorScheme.surface.copy(
            alpha = (1f - personalization.visualEffects.glassTransparency * 0.4f).coerceIn(0.66f, 0.94f)
        )
    } else {
        MaterialTheme.colorScheme.surface
    }
    val pillBgColor = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                ambientColor = Color.Black.copy(alpha = 0.14f),
                spotColor = Color.Black.copy(alpha = 0.22f)
            )
            .border(
                width = if (isGlassSheet) 1.dp else 0.6.dp,
                color = if (isGlassSheet) {
                    Color.White.copy(alpha = 0.42f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
            .testTag("dialpad_sheet"),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = surfaceColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top collapse pill handle
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        onCollapseDialPad()
                    }
                    .padding(vertical = 4.dp, horizontal = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(textSecondary.copy(alpha = 0.36f))
                )
            }

            // Number Entry & Paste / Live Formatting Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .testTag("dialpad_input_field")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Paste Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (dialInput.isNotEmpty()) pillBgColor.copy(alpha = 0.7f) else Color.Transparent
                            )
                            .clickable {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipText = cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                val cleaned = clipText.filter {
                                    it.isDigit() || it == '+' || it == '*' || it == '#'
                                }
                                if (cleaned.isNotEmpty()) {
                                    onPasteText(cleaned)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("dialpad_paste_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste number",
                            tint = textSecondary.copy(alpha = if (dialInput.isNotEmpty()) 0.9f else 0.55f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Paste",
                            color = textSecondary.copy(alpha = if (dialInput.isNotEmpty()) 0.9f else 0.55f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Center: Formatted Dial Input Field
                    val dynamicFontSize = when {
                        formattedDialInput.length > 16 -> 22.sp
                        formattedDialInput.length > 12 -> 26.sp
                        else -> 30.sp
                    }

                    BasicTextField(
                        value = formattedDialInput,
                        onValueChange = { updatedText ->
                            val rawFiltered = updatedText.filter {
                                it.isDigit() || it == '+' || it == '*' || it == '#' || it == ',' || it == ';'
                            }
                            onPasteText(rawFiltered)
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = textPrimary,
                            fontSize = dynamicFontSize,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(ColorOsGreen),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone
                        ),
                        decorationBox = { innerField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                innerField()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                            .testTag("dialpad_input_display")
                    )

                    // Right: Copy button when number is entered
                    if (dialInput.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Phone Number", dialInput))
                            },
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("dialpad_copy_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy number",
                                tint = ColorOsGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(30.dp))
                    }
                }
            }

            if (dialInput.isNotEmpty()) {
                Text(
                    text = if (liveMatchCount > 0) {
                        "$liveMatchCount matching contact/call result${if (liveMatchCount == 1) "" else "s"}"
                    } else {
                        "SIM 1 ready"
                    },
                    color = if (liveMatchCount > 0) ColorOsGreen else textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .testTag("dialpad_live_search_status")
                )
            }

            // 3x4 Numeric Keypad Grid matching IMG-20261107-WA0014
            val rows = listOf(
                listOf(
                    Triple("1", "", true),
                    Triple("2", "ABC", false),
                    Triple("3", "DEF", false)
                ),
                listOf(
                    Triple("4", "GHI", false),
                    Triple("5", "JKL", false),
                    Triple("6", "MNO", false)
                ),
                listOf(
                    Triple("7", "PQRS", false),
                    Triple("8", "TUV", false),
                    Triple("9", "WXYZ", false)
                ),
                listOf(
                    Triple("*", ",", false),
                    Triple("0", "+", false),
                    Triple("#", ";", false)
                )
            )

            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowItems.forEach { (digit, subLabel, isVoicemailKey) ->
                        DialPadDigitKey(
                            digit = digit,
                            subLabel = subLabel,
                            showVoicemailIcon = isVoicemailKey,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            pressHighlightColor = pillBgColor,
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDigitClick(digit.first())
                            },
                            onLongClick = when (digit) {
                                "0" -> {
                                    {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDigitClick('+')
                                    }
                                }
                                "*" -> {
                                    {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDigitClick(',')
                                    }
                                }
                                "#" -> {
                                    {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDigitClick(';')
                                    }
                                }
                                else -> null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Bottom Control Row: 10-Dot Collapse Icon (Left), Uploaded Transparent Call Photo (Center), Grey Tag Backspace (Right)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                val isCompactWidth = maxWidth < 340.dp
                val callPhotoHeight = if (isCompactWidth) 74.dp else 86.dp

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Collapse Keypad Icon
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(callPhotoHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    onCollapseDialPad()
                                }
                                .testTag("dialpad_collapse_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            TenDotKeypadIcon(tint = textPrimary)
                        }
                    }

                    // Center: Uploaded Transparent Cutout Call Image (1.0 -> 1.10 -> 1.0 scale animation on tap)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(callPhotoHeight),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val callInteraction = remember { MutableInteractionSource() }
                        val coroutineScope = rememberCoroutineScope()
                        val callScaleAnim = remember { Animatable(1f) }

                        Box(
                            modifier = Modifier
                                .height(callPhotoHeight)
                                .scale(callScaleAnim.value)
                                .clickable(
                                    interactionSource = callInteraction,
                                    indication = null
                                ) {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                                    coroutineScope.launch {
                                        callScaleAnim.snapTo(1f)
                                        callScaleAnim.animateTo(
                                            targetValue = 1.10f,
                                            animationSpec = tween(durationMillis = 110)
                                        )
                                        callScaleAnim.animateTo(
                                            targetValue = 1.0f,
                                            animationSpec = tween(durationMillis = 130)
                                        )
                                    }
                                    onCallClick()
                                }
                                .testTag("dialpad_call_button"),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.het_phone_call_button),
                                contentDescription = "Call",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.height(callPhotoHeight)
                            )
                        }
                    }

                    // Right: Backspace Tag Button (tap to delete 1 digit, long-press to clear all)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(callPhotoHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                        onBackspace()
                                    },
                                    onLongClick = {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                        onClearAll()
                                    }
                                )
                                .testTag("dialpad_backspace_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedBackspaceIcon(
                                tint = if (dialInput.isNotEmpty()) {
                                    textSecondary.copy(alpha = 0.72f)
                                } else {
                                    textSecondary.copy(alpha = 0.50f)
                                },
                                crossTint = surfaceColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialPadDigitKey(
    digit: String,
    subLabel: String,
    showVoicemailIcon: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    pressHighlightColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val personalization = LocalPersonalizationConfig.current
    val homeDialer = personalization.homeDialer
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && personalization.animations.microInteractionsEnabled) 0.92f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "key_press_scale_$digit"
    )
    val keyHeight = (60f * homeDialer.dialpadButtonSize.multiplier).dp
    val keyShape = remember(homeDialer.dialpadButtonShape, keyHeight) {
        PersonalizationManager.shapeFor(homeDialer.dialpadButtonShape, keyHeight.value)
    }
    val restingBg = when (homeDialer.dialpadButtonStyle) {
        DialpadButtonVisualStyle.CLEAN_SOLID -> Color.Transparent
        DialpadButtonVisualStyle.LIQUID_GLASS -> Color.White.copy(alpha = 0.14f)
        DialpadButtonVisualStyle.OUTLINED_GLOW -> Color.Transparent
        DialpadButtonVisualStyle.SOFT_TINT -> pressHighlightColor.copy(alpha = 0.55f)
    }
    val bgColor by animateColorAsState(
        targetValue = if (isPressed) pressHighlightColor else restingBg,
        animationSpec = tween(90),
        label = "key_press_bg_$digit"
    )
    val accentBorder = if (personalization.useCustomColors) {
        personalization.primaryColor.copy(alpha = 0.55f)
    } else {
        Color(0xFF38BDF8).copy(alpha = 0.55f)
    }

    Column(
        modifier = modifier
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .height(keyHeight)
            .scale(scale)
            .clip(keyShape)
            .background(bgColor)
            .let { base ->
                when (homeDialer.dialpadButtonStyle) {
                    DialpadButtonVisualStyle.LIQUID_GLASS ->
                        base.border(0.8.dp, Color.White.copy(alpha = 0.34f), keyShape)
                    DialpadButtonVisualStyle.OUTLINED_GLOW ->
                        base.border(1.1.dp, accentBorder, keyShape)
                    else -> base
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("dial_key_$digit"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = digit,
            color = textPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 32.sp
        )
        if (showVoicemailIcon) {
            Spacer(modifier = Modifier.height(2.dp))
            VoicemailReelIcon(tint = textSecondary)
        } else if (subLabel.isNotEmpty()) {
            Text(
                text = subLabel,
                color = textSecondary,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.8.sp,
                lineHeight = 12.sp
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private val sameDayTimeFormatter = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
}

private val olderDateFormatter = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
}

private val reusableCalendar = object : ThreadLocal<Calendar>() {
    override fun initialValue(): Calendar = Calendar.getInstance()
}

private fun formatCallLogTimestamp(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return ""
    val cal = reusableCalendar.get() ?: Calendar.getInstance()
    cal.timeInMillis = System.currentTimeMillis()
    val nowYear = cal.get(Calendar.YEAR)
    val nowDay = cal.get(Calendar.DAY_OF_YEAR)

    cal.timeInMillis = timestampMillis
    val sameDay = nowYear == cal.get(Calendar.YEAR) && nowDay == cal.get(Calendar.DAY_OF_YEAR)
    val date = Date(timestampMillis)

    return if (sameDay) {
        (sameDayTimeFormatter.get() ?: SimpleDateFormat("h:mm a", Locale.getDefault()))
            .format(date)
            .lowercase(Locale.getDefault())
    } else {
        (olderDateFormatter.get() ?: SimpleDateFormat("dd/MM/yy", Locale.getDefault()))
            .format(date)
    }
}

private fun formatShortDuration(seconds: Long): String {
    if (seconds <= 0L) return "0s"
    val mins = seconds / 60L
    val secs = seconds % 60L
    return if (mins > 0L) "${mins}m ${secs}s" else "${secs}s"
}

internal fun extractDialSearchChars(query: String): String {
    var allDialChars = true
    for (i in 0 until query.length) {
        val c = query[i]
        if (!c.isDigit() && c != '+' && c != '*' && c != '#') {
            allDialChars = false
            break
        }
    }
    if (allDialChars) return query
    return buildString(query.length) {
        for (i in 0 until query.length) {
            val c = query[i]
            if (c.isDigit() || c == '+' || c == '*' || c == '#') {
                append(c)
            }
        }
    }
}

internal fun containsDialDigits(phoneNumber: String, cleanDigits: String): Boolean {
    if (cleanDigits.isEmpty() || phoneNumber.length < cleanDigits.length) return false
    val maxStart = phoneNumber.length - cleanDigits.length
    var i = 0
    while (i <= maxStart) {
        var pIdx = i
        var dIdx = 0
        while (pIdx < phoneNumber.length && dIdx < cleanDigits.length) {
            val pc = phoneNumber[pIdx]
            if (!pc.isDigit() && pc != '+' && pc != '*' && pc != '#') {
                pIdx++
                continue
            }
            if (pc != cleanDigits[dIdx]) {
                break
            }
            pIdx++
            dIdx++
        }
        if (dIdx == cleanDigits.length) return true
        i++
    }
    return false
}

private fun charToT9Digit(ch: Char): Char {
    return when (ch.lowercaseChar()) {
        'a', 'b', 'c' -> '2'
        'd', 'e', 'f' -> '3'
        'g', 'h', 'i' -> '4'
        'j', 'k', 'l' -> '5'
        'm', 'n', 'o' -> '6'
        'p', 'q', 'r', 's' -> '7'
        't', 'u', 'v' -> '8'
        'w', 'x', 'y', 'z' -> '9'
        else -> '\u0000'
    }
}

internal fun matchesT9Name(name: String, digits: String): Boolean {
    if (digits.isEmpty() || name.length < digits.length) return false
    for (i in 0 until digits.length) {
        if (digits[i] !in '2'..'9') return false
    }
    val maxStart = name.length - digits.length
    var start = 0
    while (start <= maxStart) {
        var nIdx = start
        var dIdx = 0
        while (nIdx < name.length && dIdx < digits.length) {
            val t9 = charToT9Digit(name[nIdx])
            if (t9 == '\u0000') {
                nIdx++
                continue
            }
            if (t9 != digits[dIdx]) {
                break
            }
            nIdx++
            dIdx++
        }
        if (dIdx == digits.length) return true
        start++
    }
    return false
}

/**
 * Pure helper evaluating whether an upward swipe/scroll gesture on the Call History list
 * should auto-hide the expanded Dialpad bottom sheet.
 *
 * - Upward finger drag produces negative delta (`accumulatedUpwardDeltaPx < 0f`) and negative
 *   vertical fling velocity (`flingVelocityY < 0f`).
 * - Downward swipes (`accumulatedUpwardDeltaPx >= 0f` and `flingVelocityY >= 0f`) NEVER trigger
 *   Dialpad state changes.
 * - Tiny accidental touches smaller than `distanceThresholdPx` are ignored so normal taps,
 *   long-presses, and (i) info button clicks are unaffected.
 */
internal fun shouldAutoHideDialPadOnCallHistorySwipe(
    isDialPadExpanded: Boolean,
    accumulatedUpwardDeltaPx: Float,
    flingVelocityY: Float = 0f,
    distanceThresholdPx: Float = 36f,
    velocityThresholdPxPerSec: Float = 380f
): Boolean {
    if (!isDialPadExpanded) return false
    val threshold = kotlin.math.abs(distanceThresholdPx).coerceAtLeast(1f)
    val velocityThreshold = kotlin.math.abs(velocityThresholdPxPerSec).coerceAtLeast(1f)
    val metDistanceThreshold = accumulatedUpwardDeltaPx <= -threshold
    val metVelocityThreshold = flingVelocityY <= -velocityThreshold && accumulatedUpwardDeltaPx < -8f
    return metDistanceThreshold || metVelocityThreshold
}

