package com.example.ui.personalization

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CallScreenWallpaperManager
import com.example.data.PersonalizationManager
import com.example.model.PersonalizationCategory
import com.example.model.PersonalizationConfig
import com.example.model.SavedCustomTheme
import com.example.ui.theme.ColorOsRed
import com.example.ui.theme.resolveComposeFontFamily
import kotlinx.coroutines.launch

@Composable
fun PersonalizationStudioHost(
    initialCategory: PersonalizationCategory? = null,
    onBackToApp: () -> Unit,
    onOpenSettings: () -> Unit,
    onAppliedEverywhere: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(context) {
        PersonalizationManager.ensureInitialized(context)
        CallScreenWallpaperManager.ensureInitialized(context)
        PersonalizationManager.syncDraftToApplied()
    }

    val draftConfig by PersonalizationManager.draftConfig.collectAsStateWithLifecycle()
    val savedThemes by PersonalizationManager.savedThemes.collectAsStateWithLifecycle()

    var selectedCategoryId by rememberSaveable(initialCategory) {
        mutableStateOf(initialCategory?.id)
    }
    val selectedCategory = remember(selectedCategoryId) {
        PersonalizationCategory.fromId(selectedCategoryId)
    }

    var showSaveThemeDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    val showMessage: (String) -> Unit = { msg ->
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    BackHandler(enabled = true) {
        if (selectedCategory != null) {
            selectedCategoryId = null
        } else {
            onBackToApp()
        }
    }

    LiquidGlassBackdrop(
        config = draftConfig,
        forceStudioAtmosphere = true,
        modifier = modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        ) { innerPadding ->
            val animDuration = PersonalizationManager.resolveDurationMs(260, draftConfig.animations)
            AnimatedContent(
                targetState = selectedCategory,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally(
                            initialOffsetX = { it / 4 },
                            animationSpec = PersonalizationManager.resolveAnimationSpec(animDuration, draftConfig.animations)
                        ) + fadeIn()) togetherWith fadeOut()
                    } else {
                        fadeIn() togetherWith (slideOutHorizontally(
                            targetOffsetX = { it / 5 },
                            animationSpec = PersonalizationManager.resolveAnimationSpec(animDuration, draftConfig.animations)
                        ) + fadeOut())
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                label = "studio_category_transition"
            ) { category ->
                if (category == null) {
                    StudioDashboardContent(
                        config = draftConfig,
                        onBackToApp = onBackToApp,
                        onOpenSettings = onOpenSettings,
                        onSelectCategory = { cat -> selectedCategoryId = cat.id },
                        onSaveThemeClick = { showSaveThemeDialog = true },
                        onResetClick = { showResetConfirmDialog = true },
                        onApplyEverywhereClick = {
                            PersonalizationManager.applyEverywhere(context)
                            onAppliedEverywhere()
                            showMessage("Theme applied everywhere across HET Phone")
                        }
                    )
                } else {
                    StudioCategoryDetailScaffold(
                        category = category,
                        config = draftConfig,
                        savedThemes = savedThemes,
                        onBackToDashboard = { selectedCategoryId = null },
                        onApplyEverywhereClick = {
                            PersonalizationManager.applyEverywhere(context)
                            onAppliedEverywhere()
                            showMessage("Applied ${category.title} changes everywhere")
                        },
                        onOpenSaveDialog = { showSaveThemeDialog = true },
                        onShowMessage = showMessage
                    )
                }
            }
        }
    }

    if (showSaveThemeDialog) {
        SaveCustomThemeDialog(
            suggestedName = draftConfig.activeThemeName.takeIf {
                it.isNotBlank() && it != "HET Liquid Glass"
            } ?: "My Liquid Glass ${savedThemes.size + 1}",
            onDismiss = { showSaveThemeDialog = false },
            onConfirmSave = { themeName ->
                val result = PersonalizationManager.saveCustomTheme(
                    context = context,
                    themeName = themeName
                )
                result.onSuccess { saved ->
                    PersonalizationManager.applyEverywhere(context)
                    showSaveThemeDialog = false
                    onAppliedEverywhere()
                    showMessage("Saved & applied \"${saved.name}\"")
                }.onFailure { err ->
                    showMessage(err.localizedMessage ?: "Please enter a valid theme name")
                }
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 420.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Reset to Default?") },
            text = {
                Text(
                    "Restore all HET Personalization Studio colors, typography, glass effects, and call styles back to factory defaults? Saved themes will be kept."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        PersonalizationManager.resetToDefault(context)
                        CallScreenWallpaperManager.resetToDefault(context)
                        showResetConfirmDialog = false
                        onAppliedEverywhere()
                        showMessage("Personalization reset to HET Default")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOsRed),
                    modifier = Modifier.testTag("reset_default_confirm_btn")
                ) {
                    Text("Reset", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StudioDashboardContent(
    config: PersonalizationConfig,
    onBackToApp: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectCategory: (PersonalizationCategory) -> Unit,
    onSaveThemeClick: () -> Unit,
    onResetClick: () -> Unit,
    onApplyEverywhereClick: () -> Unit
) {
    val isDark = isPersonalizationDark(config)
    val fontFamily = remember(config.typography.fontFamily) {
        resolveComposeFontFamily(config.typography.fontFamily)
    }
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = if (config.useCustomColors) config.primaryColor else Color(0xFF0A84FF)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        val columns = when {
            maxWidth >= 840.dp -> 4
            maxWidth >= 560.dp -> 3
            else -> 2
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1040.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Premium Liquid Glass Header
            LiquidGlassCard(
                config = config,
                cornerRadius = 26.dp,
                highlightAccent = accent,
                contentPadding = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("studio_header_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackToApp,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("studio_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to HET Phone",
                            tint = textPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "HET Personalization Studio",
                            color = textPrimary,
                            fontFamily = fontFamily,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .semantics { heading() }
                                .testTag("studio_header_title")
                        )
                        Text(
                            text = "Make Every Call Yours.",
                            color = if (isDark) Color(0xFF8CE4FF) else Color(0xFF0077CC),
                            fontFamily = fontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("studio_header_subtitle")
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("studio_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Phone Settings",
                            tint = textPrimary
                        )
                    }
                }
            }

            // 2. Live Theme Preview Card
            PersonalizationLiveThemePreviewCard(
                config = config,
                initialMode = PreviewMode.DIALER,
                showCustomizeButton = true,
                onCustomizeClick = onSelectCategory
            )

            // 3. Quick Actions
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Quick Actions",
                    color = textSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("studio_quick_actions_row"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidGlassActionChip(
                        label = "Save Theme",
                        icon = Icons.Default.BookmarkAdd,
                        config = config,
                        onClick = onSaveThemeClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_action_save_theme")
                    )
                    LiquidGlassActionChip(
                        label = "Reset Default",
                        icon = Icons.Default.RestartAlt,
                        config = config,
                        onClick = onResetClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_action_reset_default")
                    )
                }
                LiquidGlassActionChip(
                    label = "Apply Everywhere",
                    icon = Icons.Default.CheckCircle,
                    config = config,
                    isPrimary = true,
                    onClick = onApplyEverywhereClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_action_apply_everywhere")
                )
            }

            // 4. Customization Categories Dashboard Grid
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Customization Categories",
                        color = textPrimary,
                        fontFamily = fontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "${PersonalizationCategory.entries.size} Studios",
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                }

                val rows = remember(columns) {
                    PersonalizationCategory.entries.chunked(columns)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("studio_categories_grid"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { category ->
                                CategoryDashboardGlassTile(
                                    category = category,
                                    config = config,
                                    onClick = { onSelectCategory(category) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CategoryDashboardGlassTile(
    category: PersonalizationCategory,
    config: PersonalizationConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = categoryAccentColor(category, config)
    val icon = categoryIcon(category)
    val fontFamily = remember(config.typography.fontFamily) {
        resolveComposeFontFamily(config.typography.fontFamily)
    }

    LiquidGlassCard(
        config = config,
        cornerRadius = 24.dp,
        highlightAccent = accent,
        onClick = onClick,
        contentPadding = 15.dp,
        modifier = modifier
            .height(138.dp)
            .testTag(category.testTag)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.12f))
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Column {
                Text(
                    text = category.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = fontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = category.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    lineHeight = 14.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StudioCategoryDetailScaffold(
    category: PersonalizationCategory,
    config: PersonalizationConfig,
    savedThemes: List<SavedCustomTheme>,
    onBackToDashboard: () -> Unit,
    onApplyEverywhereClick: () -> Unit,
    onOpenSaveDialog: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val accent = categoryAccentColor(category, config)
    val fontFamily = remember(config.typography.fontFamily) {
        resolveComposeFontFamily(config.typography.fontFamily)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 920.dp)
                .fillMaxSize()
        ) {
            LiquidGlassCard(
                config = config,
                cornerRadius = 24.dp,
                highlightAccent = accent,
                contentPadding = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackToDashboard,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("category_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Studio Dashboard",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = category.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = fontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("category_screen_title")
                        )
                        Text(
                            text = category.subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    LiquidGlassActionChip(
                        label = "Apply",
                        icon = Icons.Default.CheckCircle,
                        config = config,
                        isPrimary = true,
                        onClick = onApplyEverywhereClick,
                        modifier = Modifier.testTag("category_apply_everywhere_btn")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                when (category) {
                    PersonalizationCategory.HOME_DIALER ->
                        HomeDialerCategoryContent(config = config, onShowMessage = onShowMessage)
                    PersonalizationCategory.CONTACTS ->
                        ContactsCategoryContent(config = config)
                    PersonalizationCategory.CALL_HISTORY ->
                        CallHistoryCategoryContent(config = config)
                    PersonalizationCategory.INCOMING_CALL ->
                        IncomingCallCategoryContent(config = config, onShowMessage = onShowMessage)
                    PersonalizationCategory.OUTGOING_CALL ->
                        OutgoingCallCategoryContent(config = config, onShowMessage = onShowMessage)
                    PersonalizationCategory.ONGOING_CALL ->
                        OngoingCallCategoryContent(config = config, onShowMessage = onShowMessage)
                    PersonalizationCategory.TYPOGRAPHY ->
                        TypographyCategoryContent(config = config)
                    PersonalizationCategory.ANIMATIONS ->
                        AnimationsCategoryContent(config = config)
                    PersonalizationCategory.VISUAL_EFFECTS ->
                        VisualEffectsCategoryContent(config = config)
                    PersonalizationCategory.THEMES ->
                        ThemesCategoryContent(
                            config = config,
                            savedThemes = savedThemes,
                            onOpenSaveDialog = onOpenSaveDialog,
                            onShowMessage = onShowMessage
                        )
                }
            }
        }
    }
}

@Composable
private fun SaveCustomThemeDialog(
    suggestedName: String,
    onDismiss: () -> Unit,
    onConfirmSave: (String) -> Unit
) {
    var nameDraft by remember(suggestedName) { mutableStateOf(suggestedName) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 420.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Save Custom Theme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Save your current colors, typography, glass effects, and call screen customizations.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = {
                        nameDraft = it
                        errorMsg = null
                    },
                    label = { Text("Theme name") },
                    singleLine = true,
                    isError = errorMsg != null,
                    supportingText = errorMsg?.let { err -> { Text(err) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_theme_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = nameDraft.trim()
                    if (trimmed.isEmpty()) {
                        errorMsg = "Theme name cannot be empty"
                    } else {
                        onConfirmSave(trimmed)
                    }
                },
                modifier = Modifier.testTag("save_theme_confirm_btn")
            ) {
                Text("Save & Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun categoryIcon(category: PersonalizationCategory): ImageVector = when (category) {
    PersonalizationCategory.HOME_DIALER -> Icons.Default.Dialpad
    PersonalizationCategory.CONTACTS -> Icons.Default.Contacts
    PersonalizationCategory.CALL_HISTORY -> Icons.Default.History
    PersonalizationCategory.INCOMING_CALL -> Icons.Default.CallReceived
    PersonalizationCategory.OUTGOING_CALL -> Icons.Default.CallMade
    PersonalizationCategory.ONGOING_CALL -> Icons.Default.Call
    PersonalizationCategory.TYPOGRAPHY -> Icons.Default.TextFields
    PersonalizationCategory.ANIMATIONS -> Icons.Default.Animation
    PersonalizationCategory.VISUAL_EFFECTS -> Icons.Default.AutoAwesome
    PersonalizationCategory.THEMES -> Icons.Default.Palette
}

private fun categoryAccentColor(
    category: PersonalizationCategory,
    config: PersonalizationConfig
): Color {
    val basePrimary = if (config.useCustomColors) config.primaryColor else Color(0xFF0A84FF)
    val baseCyan = if (config.useCustomColors) config.accentColor else Color(0xFF00C2FF)
    return when (category) {
        PersonalizationCategory.HOME_DIALER -> basePrimary
        PersonalizationCategory.CONTACTS -> Color(0xFF28B5F4)
        PersonalizationCategory.CALL_HISTORY -> Color(0xFF0EA5E9)
        PersonalizationCategory.INCOMING_CALL -> Color(0xFF10B981)
        PersonalizationCategory.OUTGOING_CALL -> Color(0xFF38BDF8)
        PersonalizationCategory.ONGOING_CALL -> baseCyan
        PersonalizationCategory.TYPOGRAPHY -> Color(0xFF3B82F6)
        PersonalizationCategory.ANIMATIONS -> Color(0xFF06B6D4)
        PersonalizationCategory.VISUAL_EFFECTS -> Color(0xFF60A5FA)
        PersonalizationCategory.THEMES -> basePrimary
    }
}
