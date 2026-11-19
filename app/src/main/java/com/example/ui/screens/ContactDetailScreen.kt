package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.PersonalizationManager
import com.example.model.AnimationMotionStyle
import com.example.model.AppBackgroundStyle
import com.example.model.ButtonShapeStyle
import com.example.model.CallDirection
import com.example.model.CallLogItem
import com.example.model.ContactHeaderStyle
import com.example.model.ContactItem
import com.example.model.PhoneNumberEntry
import com.example.ui.components.CallDirectionArrowIcon
import com.example.ui.components.ContactCircleAvatar
import com.example.ui.components.HdBadge
import com.example.ui.components.VoLteBadge
import com.example.ui.theme.ColorOsGreen
import com.example.ui.theme.ColorOsIncomingGreen
import com.example.ui.theme.ColorOsRed
import com.example.ui.theme.ColorOsStarGold
import com.example.ui.theme.ColorOsWhite
import com.example.ui.theme.LocalPersonalizationConfig
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// HET Phone Smart Contact Profile 3D Palette
private val HetAbyssBlue = Color(0xFF021024)
private val HetOceanBlue = Color(0xFF052659)
private val HetSteelBlue = Color(0xFF5483B3)
private val HetSkyBlue = Color(0xFF7DA0CA)
private val HetIceBlue = Color(0xFFC1E8FF)

/**
 * Smart Contact Profile 3D:
 * - Layered HET Phone blue atmospheric depth (#021024, #052659, #5483B3, #7DA0CA, #C1E8FF)
 * - Large 3D circular contact photo with subtle radial lighting, rim highlight & spring entrance
 * - Elegant contact name, subtitle, and interactive primary phone number pill
 * - Tactile animated Call (green), Message (ice-blue glass), and Video action buttons
 * - Frosted glassmorphism cards preserving all contact actions, logs, apps, and menu options
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactDetailScreen(
    contact: ContactItem,
    contactCallLogs: List<CallLogItem>,
    isSavedContact: Boolean,
    isBlockedNumber: Boolean = false,
    onBack: () -> Unit,
    onPlaceCall: (String) -> Unit,
    onSendSms: (String) -> Unit,
    onStartVideoCall: (String, (String) -> Unit) -> Unit,
    onToggleFavorite: () -> Unit,
    onEditContact: () -> Unit,
    onSetDefaultNumber: (Long) -> Unit,
    onToggleBlockNumber: (String) -> Unit = {},
    onDeleteContact: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showAllCallLogs by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val primaryNumber = contact.primaryPhone.orEmpty()
    val displayedLogs = remember(contactCallLogs, showAllCallLogs) {
        if (showAllCallLogs) contactCallLogs else contactCallLogs.take(3)
    }

    val personalization = LocalPersonalizationConfig.current
    val cardRadius = personalization.contacts.cardCornerRadiusDp.coerceIn(14f, 28f).dp
    val reducedMotion = personalization.animations.style == AnimationMotionStyle.REDUCED_MINIMAL

    val textPrimary = ColorOsWhite
    val textSecondary = HetIceBlue.copy(alpha = 0.80f)
    val textMuted = HetSkyBlue.copy(alpha = 0.88f)
    val dividerColor = HetSkyBlue.copy(alpha = 0.22f)

    // Smooth single-shot entrance progress for 3D hero depth & staggered reveal
    val entranceProgress = remember(contact.id) { Animatable(if (reducedMotion) 1f else 0f) }
    var sectionsVisible by remember(contact.id) { mutableStateOf(reducedMotion) }

    LaunchedEffect(contact.id, reducedMotion) {
        sectionsVisible = true
        if (reducedMotion) {
            entranceProgress.snapTo(1f)
        } else {
            entranceProgress.snapTo(0f)
            entranceProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = (280f * personalization.animations.speedMultiplier).coerceIn(160f, 520f)
                )
            )
        }
    }

    SmartProfile3DBackdrop(
        customPhotoUri = if (personalization.homeDialer.backgroundStyle == AppBackgroundStyle.CUSTOM_PHOTO) {
            personalization.homeDialer.customBackgroundPhotoUri
        } else {
            ""
        },
        reducedMotion = reducedMotion,
        modifier = modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("contact_detail_scroll"),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 36.dp)
            ) {
                // Top Glass Floating Action Bar
                item(key = "top_action_bar") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 620.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlassTopActionButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = HetIceBlue,
                                onClick = onBack,
                                testTag = "contact_detail_back_button"
                            )
                            if (isBlockedNumber) {
                                Surface(
                                    color = ColorOsRed.copy(alpha = 0.20f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.border(
                                        width = 1.dp,
                                        color = ColorOsRed.copy(alpha = 0.48f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                ) {
                                    Text(
                                        text = "Blocked",
                                        color = Color(0xFFFFB4AB),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            GlassTopActionButton(
                                icon = if (contact.isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = "Favorites",
                                tint = if (contact.isStarred) ColorOsStarGold else HetIceBlue,
                                onClick = {
                                    if (isSavedContact) {
                                        onToggleFavorite()
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Save contact first to add to Favorites.")
                                        }
                                    }
                                },
                                testTag = "detail_action_favorites"
                            )

                            GlassTopActionButton(
                                icon = Icons.Outlined.Edit,
                                contentDescription = "Edit",
                                tint = HetIceBlue,
                                onClick = onEditContact,
                                testTag = "detail_action_edit"
                            )

                            GlassTopActionButton(
                                icon = Icons.Outlined.Share,
                                contentDescription = "Share",
                                tint = HetIceBlue,
                                onClick = { shareContactNative(context, contact) },
                                testTag = "detail_action_share"
                            )

                            Box {
                                GlassTopActionButton(
                                    icon = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = HetIceBlue,
                                    onClick = { showMoreMenu = true },
                                    testTag = "detail_action_learn_more"
                                )

                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                    shape = RoundedCornerShape(18.dp),
                                    containerColor = HetOceanBlue.copy(alpha = 0.96f)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Share contact", color = ColorOsWhite, fontSize = 15.sp) },
                                        onClick = {
                                            showMoreMenu = false
                                            shareContactNative(context, contact)
                                        }
                                    )
                                    if (primaryNumber.isNotBlank()) {
                                        DropdownMenuItem(
                                            text = { Text("Copy phone number", color = ColorOsWhite, fontSize = 15.sp) },
                                            onClick = {
                                                showMoreMenu = false
                                                copyToClipboard(context, primaryNumber)
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Copied $primaryNumber")
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (isBlockedNumber) "Unblock number" else "Block number",
                                                    color = ColorOsWhite,
                                                    fontSize = 15.sp
                                                )
                                            },
                                            onClick = {
                                                showMoreMenu = false
                                                onToggleBlockNumber(primaryNumber)
                                            }
                                        )
                                    }
                                    if (isSavedContact && contact.phones.size > 1) {
                                        contact.phones.forEach { phone ->
                                            if (!phone.isDefault && phone.dataId > 0L) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            "Set ${phone.number} as default",
                                                            color = HetIceBlue,
                                                            fontSize = 15.sp
                                                        )
                                                    },
                                                    onClick = {
                                                        showMoreMenu = false
                                                        onSetDefaultNumber(phone.dataId)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    if (isSavedContact) {
                                        DropdownMenuItem(
                                            text = { Text("Delete contact", color = Color(0xFFFF8A80), fontSize = 15.sp) },
                                            onClick = {
                                                showMoreMenu = false
                                                showDeleteConfirm = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3D Contact Profile Hero Stage (Large Circular Photo + Name + Phone Number)
                item(key = "avatar_header") {
                    SmartProfile3DHeroSection(
                        contact = contact,
                        primaryNumber = primaryNumber,
                        headerStyle = personalization.contacts.profileHeaderStyle,
                        cardRadius = cardRadius,
                        entranceProgress = entranceProgress.value,
                        reducedMotion = reducedMotion,
                        onCallPrimary = {
                            if (primaryNumber.isNotBlank()) {
                                onPlaceCall(primaryNumber)
                            }
                        },
                        onCopyPrimary = {
                            if (primaryNumber.isNotBlank()) {
                                copyToClipboard(context, primaryNumber)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Copied $primaryNumber")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 620.dp)
                    )
                }

                // Animated Action Buttons: Call (Green), Message (Ice Blue Glass), Video (Deep Sky Glass)
                item(key = "quick_action_cards") {
                    AnimatedVisibility(
                        visible = sectionsVisible,
                        enter = fadeIn(
                            animationSpec = tween(durationMillis = if (reducedMotion) 0 else 280)
                        ) + slideInVertically(
                            initialOffsetY = { if (reducedMotion) 0 else it / 5 },
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 340f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 620.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SmartProfileActionButton(
                                icon = Icons.Filled.Call,
                                label = "Call",
                                variant = ProfileActionVariant.CALL_GREEN,
                                cornerRadius = cardRadius,
                                microInteractions = personalization.animations.microInteractionsEnabled && !reducedMotion,
                                onClick = {
                                    if (primaryNumber.isNotBlank()) {
                                        onPlaceCall(primaryNumber)
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("No phone number saved for this contact.")
                                        }
                                    }
                                },
                                testTag = "detail_card_call",
                                modifier = Modifier.weight(1f)
                            )

                            SmartProfileActionButton(
                                icon = Icons.AutoMirrored.Filled.Message,
                                label = "Message",
                                variant = ProfileActionVariant.MESSAGE_BLUE,
                                cornerRadius = cardRadius,
                                microInteractions = personalization.animations.microInteractionsEnabled && !reducedMotion,
                                onClick = {
                                    if (primaryNumber.isNotBlank()) {
                                        onSendSms(primaryNumber)
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("No phone number saved for SMS.")
                                        }
                                    }
                                },
                                testTag = "detail_card_message",
                                modifier = Modifier.weight(1f)
                            )

                            SmartProfileActionButton(
                                icon = Icons.Filled.Videocam,
                                label = "Video",
                                variant = ProfileActionVariant.VIDEO_GLASS,
                                cornerRadius = cardRadius,
                                microInteractions = personalization.animations.microInteractionsEnabled && !reducedMotion,
                                onClick = {
                                    if (primaryNumber.isNotBlank()) {
                                        onStartVideoCall(primaryNumber) { msg ->
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(msg)
                                            }
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("No phone number available for video call.")
                                        }
                                    }
                                },
                                testTag = "detail_card_video",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Phone Numbers + Ringtone Glass Card
                if (contact.phones.isNotEmpty()) {
                    item(key = "phone_numbers_card") {
                        SmartProfileGlassCard(
                            cornerRadius = cardRadius,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 620.dp)
                                .padding(start = 18.dp, end = 18.dp, top = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(HetSteelBlue.copy(alpha = 0.28f))
                                        .border(1.dp, HetIceBlue.copy(alpha = 0.28f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Call,
                                        contentDescription = null,
                                        tint = HetIceBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    contact.phones.forEach { phoneEntry ->
                                        PhoneNumberDetailRow(
                                            entry = phoneEntry,
                                            textPrimary = textPrimary,
                                            textSecondary = textMuted,
                                            onClick = { onPlaceCall(phoneEntry.number) },
                                            onLongClick = {
                                                if (phoneEntry.dataId > 0L) {
                                                    onSetDefaultNumber(phoneEntry.dataId)
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("${phoneEntry.number} set as default")
                                                    }
                                                } else {
                                                    copyToClipboard(context, phoneEntry.number)
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Copied ${phoneEntry.number}")
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                color = dividerColor,
                                thickness = 0.75.dp,
                                modifier = Modifier.padding(start = 48.dp, top = 14.dp, bottom = 14.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(HetSteelBlue.copy(alpha = 0.24f))
                                        .border(1.dp, HetIceBlue.copy(alpha = 0.24f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Notifications,
                                        contentDescription = null,
                                        tint = HetIceBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Follow system",
                                        color = textPrimary,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Ringtone",
                                        color = textMuted,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Call logs section header
                item(key = "call_logs_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 620.dp)
                            .padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Call logs",
                            color = HetIceBlue.copy(alpha = 0.85f),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.3.sp
                        )
                        Text(
                            text = if (showAllCallLogs && contactCallLogs.size > 3) "Collapse" else "View all",
                            color = ColorOsIncomingGreen,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showAllCallLogs = !showAllCallLogs }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("contact_detail_view_all_logs")
                        )
                    }
                }

                // Call logs Glass Card
                item(key = "call_logs_card") {
                    SmartProfileGlassCard(
                        cornerRadius = cardRadius,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 620.dp)
                            .padding(horizontal = 18.dp)
                    ) {
                        if (displayedLogs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No call logs for this contact",
                                    color = textMuted,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                displayedLogs.forEachIndexed { idx, log ->
                                    ContactCallLogItemRow(
                                        log = log,
                                        textPrimary = textPrimary,
                                        textSecondary = textMuted,
                                        onClick = { onPlaceCall(log.number) }
                                    )
                                    if (idx < displayedLogs.lastIndex) {
                                        HorizontalDivider(
                                            color = dividerColor,
                                            thickness = 0.75.dp,
                                            modifier = Modifier.padding(start = 44.dp, end = 18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Extra Contact Fields Card (Email, Address, Notes)
                if (contact.emails.isNotEmpty() || contact.address.isNotBlank() || contact.notes.isNotBlank()) {
                    item(key = "extra_contact_info_card") {
                        SmartProfileGlassCard(
                            cornerRadius = cardRadius,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 620.dp)
                                .padding(start = 18.dp, end = 18.dp, top = 14.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                contact.emails.forEach { email ->
                                    Column {
                                        Text(text = email, color = textPrimary, fontSize = 15.5.sp)
                                        Text(text = "Email", color = textMuted, fontSize = 12.sp)
                                    }
                                }
                                if (contact.address.isNotBlank()) {
                                    Column {
                                        Text(text = contact.address, color = textPrimary, fontSize = 15.5.sp)
                                        Text(text = "Address", color = textMuted, fontSize = 12.sp)
                                    }
                                }
                                if (contact.notes.isNotBlank()) {
                                    Column {
                                        Text(text = contact.notes, color = textSecondary, fontSize = 14.5.sp)
                                        Text(text = "Notes", color = textMuted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Connected Apps Cards: WhatsApp & Google Meet
                if (primaryNumber.isNotBlank()) {
                    item(key = "whatsapp_connected_card") {
                        ConnectedAppCard(
                            badgeColor = Color(0xFF25D366),
                            badgeText = "WA",
                            cornerRadius = cardRadius,
                            rows = listOf(
                                ConnectedAppActionRow(
                                    icon = Icons.AutoMirrored.Filled.Message,
                                    label = "Message $primaryNumber",
                                    onClick = {
                                        launchWhatsAppOrFallback(
                                            context = context,
                                            number = primaryNumber,
                                            onFallbackMessage = { msg ->
                                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                            }
                                        )
                                    }
                                ),
                                ConnectedAppActionRow(
                                    icon = Icons.Outlined.Call,
                                    label = "Voice call $primaryNumber",
                                    onClick = {
                                        launchWhatsAppOrFallback(
                                            context = context,
                                            number = primaryNumber,
                                            onFallbackMessage = { msg ->
                                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                            }
                                        )
                                    }
                                ),
                                ConnectedAppActionRow(
                                    icon = Icons.Outlined.Videocam,
                                    label = "Video call $primaryNumber",
                                    onClick = {
                                        launchWhatsAppOrFallback(
                                            context = context,
                                            number = primaryNumber,
                                            onFallbackMessage = { msg ->
                                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                            }
                                        )
                                    }
                                )
                            ),
                            textPrimary = textPrimary,
                            dividerColor = dividerColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 620.dp)
                                .padding(start = 18.dp, end = 18.dp, top = 14.dp)
                        )
                    }

                    item(key = "meet_connected_card") {
                        ConnectedAppCard(
                            badgeColor = Color(0xFF00897B),
                            badgeText = "M",
                            cornerRadius = cardRadius,
                            rows = listOf(
                                ConnectedAppActionRow(
                                    icon = Icons.Outlined.Call,
                                    label = "Voice call $primaryNumber on Meet",
                                    onClick = {
                                        onStartVideoCall(primaryNumber) { msg ->
                                            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    }
                                ),
                                ConnectedAppActionRow(
                                    icon = Icons.Outlined.Videocam,
                                    label = "Video call $primaryNumber on Meet",
                                    onClick = {
                                        onStartVideoCall(primaryNumber) { msg ->
                                            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                        }
                                    }
                                )
                            ),
                            textPrimary = textPrimary,
                            dividerColor = dividerColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 620.dp)
                                .padding(start = 18.dp, end = 18.dp, top = 14.dp)
                        )
                    }
                }

                // Save location Card
                item(key = "save_location_card") {
                    SmartProfileGlassCard(
                        cornerRadius = cardRadius,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 620.dp)
                            .padding(start = 18.dp, end = 18.dp, top = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(HetSteelBlue.copy(alpha = 0.24f))
                                    .border(1.dp, HetIceBlue.copy(alpha = 0.24f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = HetIceBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = if (isSavedContact) "Device & synced account" else "Unsaved caller",
                                    color = textPrimary,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Save location",
                                    color = textMuted,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor = HetOceanBlue,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        text = "Move contact to Recycle Bin?",
                        color = ColorOsWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Text(
                        text = "${contact.displayName} will be moved to Deleted contacts (Recycle Bin) and can be restored anytime.",
                        color = HetIceBlue.copy(alpha = 0.86f)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            onDeleteContact()
                        }
                    ) {
                        Text("Delete", color = Color(0xFFFF8A80), fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel", color = HetIceBlue)
                    }
                }
            )
        }
    }
}

/**
 * Atmospheric HET Phone blue 3D lighting backdrop (#021024, #052659, #5483B3, #7DA0CA, #C1E8FF).
 * Uses a single lightweight Canvas pass for radial lighting so Android 17 scroll remains silky smooth.
 */
@Composable
private fun SmartProfile3DBackdrop(
    customPhotoUri: String,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HetAbyssBlue)
    ) {
        if (customPhotoUri.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(Uri.parse(customPhotoUri))
                    .crossfade(true)
                    .build(),
                contentDescription = "Contact profile wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                HetAbyssBlue.copy(alpha = 0.78f),
                                HetOceanBlue.copy(alpha = 0.66f),
                                HetAbyssBlue.copy(alpha = 0.88f)
                            )
                        )
                    )
            )
        } else {
            val ambientDrift = if (reducedMotion) {
                0f
            } else {
                val infiniteTransition = rememberInfiniteTransition(label = "profile_3d_ambient")
                infiniteTransition.animateFloat(
                    initialValue = -1f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 7000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "ambient_orb_drift"
                ).value
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            HetAbyssBlue,
                            HetOceanBlue,
                            Color(0xFF041C42),
                            HetAbyssBlue
                        )
                    )
                )

                // Top radial 3D spotlight behind the contact avatar
                val topOrbCenter = Offset(w * 0.5f + ambientDrift * w * 0.04f, h * 0.18f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            HetIceBlue.copy(alpha = 0.24f),
                            HetSteelBlue.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        center = topOrbCenter,
                        radius = w * 0.72f
                    ),
                    center = topOrbCenter,
                    radius = w * 0.72f
                )

                // Mid-right steel blue depth orb
                val rightOrbCenter = Offset(w * 0.90f, h * 0.44f - ambientDrift * h * 0.02f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            HetSkyBlue.copy(alpha = 0.18f),
                            HetOceanBlue.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = rightOrbCenter,
                        radius = w * 0.58f
                    ),
                    center = rightOrbCenter,
                    radius = w * 0.58f
                )

                // Lower-left subtle cyan mist glow
                val bottomOrbCenter = Offset(w * 0.16f, h * 0.80f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            HetSteelBlue.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = bottomOrbCenter,
                        radius = w * 0.64f
                    ),
                    center = bottomOrbCenter,
                    radius = w * 0.64f
                )
            }
        }

        content()
    }
}

@Composable
private fun GlassTopActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    ambientColor = HetAbyssBlue.copy(alpha = 0.65f),
                    spotColor = HetSteelBlue.copy(alpha = 0.35f)
                )
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            HetSteelBlue.copy(alpha = 0.34f),
                            HetOceanBlue.copy(alpha = 0.65f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            HetIceBlue.copy(alpha = 0.45f),
                            HetSkyBlue.copy(alpha = 0.15f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmartProfile3DHeroSection(
    contact: ContactItem,
    primaryNumber: String,
    headerStyle: ContactHeaderStyle,
    cardRadius: Dp,
    entranceProgress: Float,
    reducedMotion: Boolean,
    onCallPrimary: () -> Unit,
    onCopyPrimary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val heroScale = 0.88f + 0.12f * entranceProgress
    val heroAlpha = entranceProgress.coerceIn(0f, 1f)
    val heroTiltDeg = if (reducedMotion) 0f else (1f - entranceProgress) * 10f

    val haloPulse = if (reducedMotion) {
        0.52f
    } else {
        val infinite = rememberInfiniteTransition(label = "avatar_3d_halo")
        infinite.animateFloat(
            initialValue = 0.38f,
            targetValue = 0.68f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "avatar_halo_alpha"
        ).value
    }

    val motionModifier = Modifier.graphicsLayer {
        scaleX = heroScale
        scaleY = heroScale
        alpha = heroAlpha
        rotationX = heroTiltDeg
        cameraDistance = 14f * density
    }

    when (headerStyle) {
        ContactHeaderStyle.HERO_GLASS_CARD -> {
            SmartProfileGlassCard(
                cornerRadius = cardRadius,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
                modifier = modifier
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .then(motionModifier)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SmartAvatar3DPedestal(
                        name = contact.displayName,
                        photoUri = contact.photoUri,
                        isStarred = contact.isStarred,
                        avatarSize = 116.dp,
                        fontSizeSp = 46f,
                        haloAlpha = haloPulse
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HeroIdentityBlock(
                        displayName = contact.displayName,
                        subtitle = contact.subtitle,
                        primaryNumber = primaryNumber,
                        centered = true,
                        onCallPrimary = onCallPrimary,
                        onCopyPrimary = onCopyPrimary
                    )
                }
            }
        }

        ContactHeaderStyle.COMPACT_BANNER -> {
            SmartProfileGlassCard(
                cornerRadius = cardRadius,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                modifier = modifier
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .then(motionModifier)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmartAvatar3DPedestal(
                        name = contact.displayName,
                        photoUri = contact.photoUri,
                        isStarred = contact.isStarred,
                        avatarSize = 82.dp,
                        fontSizeSp = 32f,
                        haloAlpha = haloPulse
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    HeroIdentityBlock(
                        displayName = contact.displayName,
                        subtitle = contact.subtitle,
                        primaryNumber = primaryNumber,
                        centered = false,
                        onCallPrimary = onCallPrimary,
                        onCopyPrimary = onCopyPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        ContactHeaderStyle.CENTERED_CLASSIC -> {
            Column(
                modifier = modifier
                    .padding(top = 6.dp, bottom = 18.dp, start = 22.dp, end = 22.dp)
                    .then(motionModifier),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SmartAvatar3DPedestal(
                    name = contact.displayName,
                    photoUri = contact.photoUri,
                    isStarred = contact.isStarred,
                    avatarSize = 120.dp,
                    fontSizeSp = 48f,
                    haloAlpha = haloPulse
                )
                Spacer(modifier = Modifier.height(16.dp))
                HeroIdentityBlock(
                    displayName = contact.displayName,
                    subtitle = contact.subtitle,
                    primaryNumber = primaryNumber,
                    centered = true,
                    onCallPrimary = onCallPrimary,
                    onCopyPrimary = onCopyPrimary
                )
            }
        }
    }
}

/**
 * Large circular contact photo wrapped in a multi-layered 3D glass bezel, specular rim highlight,
 * soft radial glow, and optional favorite badge.
 */
@Composable
private fun SmartAvatar3DPedestal(
    name: String,
    photoUri: String?,
    isStarred: Boolean,
    avatarSize: Dp,
    fontSizeSp: Float,
    haloAlpha: Float
) {
    val personalization = LocalPersonalizationConfig.current
    val circularHeroConfig = remember(personalization) {
        personalization.copy(
            contacts = personalization.contacts.copy(
                avatarShape = ButtonShapeStyle.CIRCLE,
                showAvatarGlowRing = false
            )
        )
    }

    val outerBezelSize = avatarSize + 18.dp
    val auraSize = avatarSize + 44.dp

    Box(
        modifier = Modifier.size(auraSize),
        contentAlignment = Alignment.Center
    ) {
        // Subtle atmospheric 3D halo ring & specular rim
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = haloAlpha }
        ) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        HetIceBlue.copy(alpha = 0.42f),
                        HetSteelBlue.copy(alpha = 0.30f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        HetIceBlue.copy(alpha = 0.75f),
                        HetSkyBlue.copy(alpha = 0.16f),
                        Color.Transparent,
                        HetIceBlue.copy(alpha = 0.55f)
                    )
                ),
                startAngle = -145f,
                sweepAngle = 210f,
                useCenter = false,
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Elevated 3D frosted glass circular bezel
        Box(
            modifier = Modifier
                .size(outerBezelSize)
                .shadow(
                    elevation = 18.dp,
                    shape = CircleShape,
                    ambientColor = HetAbyssBlue.copy(alpha = 0.90f),
                    spotColor = HetIceBlue.copy(alpha = 0.35f)
                )
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            HetIceBlue.copy(alpha = 0.32f),
                            HetOceanBlue.copy(alpha = 0.72f),
                            HetAbyssBlue.copy(alpha = 0.88f)
                        ),
                        start = Offset.Zero,
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            HetIceBlue.copy(alpha = 0.85f),
                            HetSteelBlue.copy(alpha = 0.45f),
                            HetSkyBlue.copy(alpha = 0.18f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Inner recessed 3D shadow rim around the circular photo
            Box(
                modifier = Modifier
                    .size(avatarSize + 4.dp)
                    .clip(CircleShape)
                    .background(HetAbyssBlue.copy(alpha = 0.65f))
                    .border(
                        width = 1.dp,
                        color = HetIceBlue.copy(alpha = 0.25f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(LocalPersonalizationConfig provides circularHeroConfig) {
                    ContactCircleAvatar(
                        name = name,
                        photoUri = photoUri,
                        size = avatarSize,
                        fontSize = fontSizeSp.sp
                    )
                }
            }
        }

        if (isStarred) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-10).dp, y = (-10).dp)
                    .size(28.dp)
                    .shadow(8.dp, CircleShape, ambientColor = HetAbyssBlue, spotColor = ColorOsStarGold)
                    .clip(CircleShape)
                    .background(HetOceanBlue)
                    .border(1.2.dp, HetIceBlue.copy(alpha = 0.70f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Favorite contact",
                    tint = ColorOsStarGold,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroIdentityBlock(
    displayName: String,
    subtitle: String?,
    primaryNumber: String,
    centered: Boolean,
    onCallPrimary: () -> Unit,
    onCopyPrimary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            text = displayName,
            color = ColorOsWhite,
            fontSize = if (centered) 27.sp else 23.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.3).sp,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("contact_detail_name")
        )

        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = HetSkyBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (primaryNumber.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(50),
                        ambientColor = HetAbyssBlue.copy(alpha = 0.75f),
                        spotColor = HetSteelBlue.copy(alpha = 0.35f)
                    )
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                HetSteelBlue.copy(alpha = 0.34f),
                                HetOceanBlue.copy(alpha = 0.76f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                HetIceBlue.copy(alpha = 0.55f),
                                HetSteelBlue.copy(alpha = 0.25f)
                            )
                        ),
                        shape = RoundedCornerShape(50)
                    )
                    .combinedClickable(
                        onClick = onCallPrimary,
                        onLongClick = onCopyPrimary
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Call,
                    contentDescription = null,
                    tint = HetIceBlue,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = primaryNumber,
                    color = HetIceBlue,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private enum class ProfileActionVariant {
    CALL_GREEN,
    MESSAGE_BLUE,
    VIDEO_GLASS
}

@Composable
private fun SmartProfileActionButton(
    icon: ImageVector,
    label: String,
    variant: ProfileActionVariant,
    cornerRadius: Dp,
    microInteractions: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && microInteractions) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "profile_action_press_scale"
    )

    val shape = RoundedCornerShape(cornerRadius.coerceIn(14.dp, 24.dp))

    val fillBrush = when (variant) {
        ProfileActionVariant.CALL_GREEN -> Brush.linearGradient(
            colors = listOf(
                ColorOsIncomingGreen,
                ColorOsGreen,
                Color(0xFF0A8F2A)
            )
        )
        ProfileActionVariant.MESSAGE_BLUE -> Brush.linearGradient(
            colors = listOf(
                HetSteelBlue.copy(alpha = 0.62f),
                HetOceanBlue.copy(alpha = 0.92f)
            )
        )
        ProfileActionVariant.VIDEO_GLASS -> Brush.linearGradient(
            colors = listOf(
                HetOceanBlue.copy(alpha = 0.84f),
                HetAbyssBlue.copy(alpha = 0.94f)
            )
        )
    }

    val borderBrush = when (variant) {
        ProfileActionVariant.CALL_GREEN -> Brush.linearGradient(
            colors = listOf(
                ColorOsWhite.copy(alpha = 0.56f),
                ColorOsIncomingGreen.copy(alpha = 0.40f)
            )
        )
        ProfileActionVariant.MESSAGE_BLUE -> Brush.linearGradient(
            colors = listOf(
                HetIceBlue.copy(alpha = 0.65f),
                HetSteelBlue.copy(alpha = 0.25f)
            )
        )
        ProfileActionVariant.VIDEO_GLASS -> Brush.linearGradient(
            colors = listOf(
                HetIceBlue.copy(alpha = 0.36f),
                HetSkyBlue.copy(alpha = 0.16f)
            )
        )
    }

    val shadowColor = when (variant) {
        ProfileActionVariant.CALL_GREEN -> ColorOsGreen.copy(alpha = 0.45f)
        ProfileActionVariant.MESSAGE_BLUE -> HetSteelBlue.copy(alpha = 0.38f)
        ProfileActionVariant.VIDEO_GLASS -> HetAbyssBlue.copy(alpha = 0.70f)
    }

    val iconTint = when (variant) {
        ProfileActionVariant.CALL_GREEN -> ColorOsWhite
        ProfileActionVariant.MESSAGE_BLUE -> HetIceBlue
        ProfileActionVariant.VIDEO_GLASS -> HetSkyBlue
    }

    val iconChipBg = when (variant) {
        ProfileActionVariant.CALL_GREEN -> ColorOsWhite.copy(alpha = 0.20f)
        ProfileActionVariant.MESSAGE_BLUE -> HetIceBlue.copy(alpha = 0.16f)
        ProfileActionVariant.VIDEO_GLASS -> HetSteelBlue.copy(alpha = 0.22f)
    }

    val labelColor = when (variant) {
        ProfileActionVariant.CALL_GREEN -> ColorOsWhite
        ProfileActionVariant.MESSAGE_BLUE -> ColorOsWhite
        ProfileActionVariant.VIDEO_GLASS -> HetIceBlue
    }

    Box(
        modifier = modifier
            .height(82.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(
                elevation = if (variant == ProfileActionVariant.CALL_GREEN) 14.dp else 10.dp,
                shape = shape,
                ambientColor = shadowColor,
                spotColor = shadowColor
            )
            .clip(shape)
            .background(fillBrush)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = HetIceBlue),
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconChipBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                color = labelColor,
                fontSize = 13.sp,
                fontWeight = if (variant == ProfileActionVariant.CALL_GREEN) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

/**
 * Reusable 3D frosted glass card styled in the HET Phone blue palette (#021024 -> #052659 -> #C1E8FF specular rim).
 */
@Composable
private fun SmartProfileGlassCard(
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = shape,
                ambientColor = HetAbyssBlue.copy(alpha = 0.85f),
                spotColor = HetSteelBlue.copy(alpha = 0.25f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        HetOceanBlue.copy(alpha = 0.54f),
                        Color(0xFF03193B).copy(alpha = 0.72f),
                        HetAbyssBlue.copy(alpha = 0.80f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        HetIceBlue.copy(alpha = 0.38f),
                        HetSteelBlue.copy(alpha = 0.24f),
                        HetSkyBlue.copy(alpha = 0.10f)
                    )
                ),
                shape = shape
            )
            .padding(contentPadding),
        content = content
    )
}

private data class ConnectedAppActionRow(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

@Composable
private fun ConnectedAppCard(
    badgeColor: Color,
    badgeText: String,
    cornerRadius: Dp,
    rows: List<ConnectedAppActionRow>,
    textPrimary: Color,
    dividerColor: Color,
    modifier: Modifier = Modifier
) {
    SmartProfileGlassCard(
        cornerRadius = cornerRadius,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        rows.forEachIndexed { idx, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { row.onClick() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = row.icon,
                    contentDescription = null,
                    tint = HetIceBlue,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(modifier = Modifier.width(15.dp))
                Text(
                    text = row.label,
                    color = textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                        .border(1.dp, ColorOsWhite.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeText,
                        color = ColorOsWhite,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (idx < rows.lastIndex) {
                HorizontalDivider(
                    color = dividerColor,
                    thickness = 0.75.dp,
                    modifier = Modifier.padding(start = 36.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneNumberDetailRow(
    entry: PhoneNumberEntry,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = entry.number,
                color = textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
            if (entry.isDefault) {
                Box(
                    modifier = Modifier
                        .background(HetSteelBlue.copy(alpha = 0.38f), RoundedCornerShape(6.dp))
                        .border(0.75.dp, HetIceBlue.copy(alpha = 0.32f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Default",
                        color = HetIceBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            if (entry.isRecent) {
                Box(
                    modifier = Modifier
                        .background(HetOceanBlue.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                        .border(0.75.dp, HetSkyBlue.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Recent",
                        color = HetIceBlue.copy(alpha = 0.90f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        val subtitleText = if (!entry.location.isNullOrBlank()) {
            "${entry.typeLabel}  |  ${entry.location}"
        } else {
            "${entry.typeLabel}  |  India"
        }
        Text(
            text = subtitleText,
            color = textSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ContactCallLogItemRow(
    log: CallLogItem,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    val dateTimeStr = remember(log.timestamp) {
        SimpleDateFormat("dd/MM/yy h:mm a", Locale.getDefault())
            .format(Date(log.timestamp))
            .lowercase(Locale.getDefault())
    }
    val statusStr = remember(log.direction, log.durationSeconds) {
        val dur = formatDurationLabel(log.durationSeconds)
        when (log.direction) {
            CallDirection.OUTGOING -> if (log.durationSeconds > 0) "Outgoing  $dur" else "Outgoing (Not connected)"
            CallDirection.INCOMING -> if (log.durationSeconds > 0) "Incoming  $dur" else "Incoming"
            CallDirection.MISSED -> "Missed call"
            CallDirection.REJECTED -> "Rejected"
            CallDirection.BLOCKED -> "Blocked"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier.padding(top = 4.dp, end = 10.dp)) {
            CallDirectionArrowIcon(direction = log.direction)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateTimeStr,
                color = if (log.direction == CallDirection.MISSED) Color(0xFFFF8A80) else textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                VoLteBadge()
                Spacer(modifier = Modifier.width(5.dp))
                if (log.isHd) {
                    HdBadge()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = log.formattedNumber.ifBlank { log.number },
                    color = textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = statusStr,
                color = textSecondary,
                fontSize = 13.5.sp
            )
        }
    }
}

private fun formatDurationLabel(seconds: Long): String {
    if (seconds <= 0L) return "0 s"
    val mins = seconds / 60L
    val secs = seconds % 60L
    return if (mins > 0L) {
        "${mins} m ${secs} s"
    } else {
        "${secs} s"
    }
}

private fun launchWhatsAppOrFallback(
    context: Context,
    number: String,
    onFallbackMessage: (String) -> Unit
) {
    val digits = number.filter { it.isDigit() }
    if (digits.isEmpty()) {
        onFallbackMessage("Invalid phone number")
        return
    }
    try {
        val uri = Uri.parse("https://wa.me/$digits")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        }
    } catch (_: Exception) {
        onFallbackMessage("WhatsApp is not installed on this device.")
    }
}

private fun shareContactNative(context: Context, contact: ContactItem) {
    val shareText = buildString {
        appendLine("Name: ${contact.displayName}")
        if (!contact.subtitle.isNullOrBlank()) {
            appendLine("Role: ${contact.subtitle}")
        }
        contact.phones.forEach { phone ->
            appendLine("${phone.typeLabel}: ${phone.number}")
        }
        contact.emails.forEach { email ->
            appendLine("Email: $email")
        }
        if (contact.address.isNotBlank()) {
            appendLine("Address: ${contact.address}")
        }
    }.trim()

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, contact.displayName)
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share contact"))
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Phone Number", text))
}
