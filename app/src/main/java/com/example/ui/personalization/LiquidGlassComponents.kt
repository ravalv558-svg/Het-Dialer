package com.example.ui.personalization

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.PersonalizationManager
import com.example.model.AppBackgroundStyle
import com.example.model.AppThemeMode
import com.example.model.PersonalizationConfig
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun isPersonalizationDark(config: PersonalizationConfig): Boolean {
    return when (config.themeMode) {
        AppThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
}

/**
 * Apple-inspired atmospheric Liquid Glass canvas with soft blue/cyan depth orbs,
 * optional custom wallpaper photo, and clean contrast-preserving frosted overlays.
 */
@Composable
fun LiquidGlassBackdrop(
    config: PersonalizationConfig,
    modifier: Modifier = Modifier,
    forceStudioAtmosphere: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = isPersonalizationDark(config)
    val primaryAccent = if (config.useCustomColors) config.primaryColor else Color(0xFF0A84FF)
    val cyanAccent = if (config.useCustomColors) config.accentColor else Color(0xFF38BDF8)
    val blurDp = if (config.visualEffects.glassmorphismEnabled) {
        config.visualEffects.blurIntensityDp.coerceIn(0f, 28f)
    } else {
        0f
    }

    val useAtmosphericCanvas = forceStudioAtmosphere ||
        config.homeDialer.backgroundStyle != AppBackgroundStyle.THEME_SOLID

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (isDark) Color(0xFF060B14) else Color(0xFFF2F6FC)
            )
    ) {
        if (useAtmosphericCanvas) {
            val bgModifier = Modifier
                .fillMaxSize()
                .then(
                    if (blurDp > 0.5f) {
                        Modifier.blur(
                            radius = (blurDp * 0.65f).dp,
                            edgeTreatment = BlurredEdgeTreatment.Rectangle
                        )
                    } else {
                        Modifier
                    }
                )

            Box(modifier = bgModifier) {
                if (config.homeDialer.backgroundStyle == AppBackgroundStyle.CUSTOM_PHOTO &&
                    config.homeDialer.customBackgroundPhotoUri.isNotBlank()
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(Uri.parse(config.homeDialer.customBackgroundPhotoUri))
                            .crossfade(true)
                            .build(),
                        contentDescription = "Custom Studio Background",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isDark) Color(0xFF060B14).copy(alpha = 0.56f)
                                else Color(0xFFF4F8FE).copy(alpha = 0.60f)
                            )
                    )
                } else {
                    val baseStops = when (config.homeDialer.backgroundStyle) {
                        AppBackgroundStyle.ARCTIC_CYAN -> if (isDark) {
                            listOf(Color(0xFF04131D), Color(0xFF0A2438), Color(0xFF061624))
                        } else {
                            listOf(Color(0xFFEAF7FF), Color(0xFFDDF1FC), Color(0xFFF5FAFF))
                        }
                        AppBackgroundStyle.DEEP_SAPPHIRE -> if (isDark) {
                            listOf(Color(0xFF040816), Color(0xFF0D1B3E), Color(0xFF071127))
                        } else {
                            listOf(Color(0xFFEAF0FF), Color(0xFFE1EBFC), Color(0xFFF4F7FD))
                        }
                        else -> if (isDark) {
                            listOf(Color(0xFF060C18), Color(0xFF0B192E), Color(0xFF07111F))
                        } else {
                            listOf(Color(0xFFF2F7FF), Color(0xFFE6F1FC), Color(0xFFF8FBFF))
                        }
                    }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(brush = Brush.verticalGradient(colors = baseStops))
                        val w = size.width
                        val h = size.height
                        val orbOpacity = if (isDark) 0.24f else 0.18f

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryAccent.copy(alpha = orbOpacity),
                                    Color.Transparent
                                ),
                                center = Offset(w * 0.18f, h * 0.14f),
                                radius = w * 0.72f
                            ),
                            center = Offset(w * 0.18f, h * 0.14f),
                            radius = w * 0.72f
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    cyanAccent.copy(alpha = orbOpacity * 0.92f),
                                    Color.Transparent
                                ),
                                center = Offset(w * 0.88f, h * 0.38f),
                                radius = w * 0.65f
                            ),
                            center = Offset(w * 0.88f, h * 0.38f),
                            radius = w * 0.65f
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryAccent.copy(alpha = orbOpacity * 0.55f),
                                    Color.Transparent
                                ),
                                center = Offset(w * 0.30f, h * 0.86f),
                                radius = w * 0.70f
                            ),
                            center = Offset(w * 0.30f, h * 0.86f),
                            radius = w * 0.70f
                        )
                    }
                }
            }
        }
        content()
    }
}

/**
 * Core translucent Liquid Glass container with specular rim reflection, subtle outer glow,
 * and responsive spring micro-interaction when clickable.
 */
@Composable
fun LiquidGlassCard(
    config: PersonalizationConfig,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    highlightAccent: Color? = null,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isPersonalizationDark(config)
    val effects = config.visualEffects
    val glassEnabled = effects.glassmorphismEnabled
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (onClick != null && isPressed && config.animations.microInteractionsEnabled) 0.975f else 1f,
        animationSpec = PersonalizationManager.resolveAnimationSpec(180, config.animations),
        label = "liquid_glass_card_scale"
    )

    val shape: Shape = RoundedCornerShape(cornerRadius)
    val accent = highlightAccent ?: if (config.useCustomColors) config.primaryColor else Color(0xFF0A84FF)
    val surfaceAlpha = if (glassEnabled) {
        if (isDark) (0.14f + effects.glassTransparency * 0.45f).coerceIn(0.14f, 0.70f)
        else (0.58f + (1f - effects.glassTransparency) * 0.34f).coerceIn(0.50f, 0.92f)
    } else {
        if (isDark) 0.92f else 0.96f
    }

    val fillBrush = if (glassEnabled) {
        val topColor = if (isDark) Color(0xFF17273E).copy(alpha = surfaceAlpha)
        else Color.White.copy(alpha = surfaceAlpha)
        val bottomColor = if (isDark) Color(0xFF0E192A).copy(alpha = (surfaceAlpha * 0.86f).coerceIn(0.12f, 0.65f))
        else Color(0xFFF3F8FE).copy(alpha = (surfaceAlpha * 0.92f).coerceIn(0.45f, 0.90f))
        Brush.linearGradient(colors = listOf(topColor, bottomColor))
    } else {
        val flat = if (isDark) Color(0xFF141B26) else Color.White
        Brush.linearGradient(colors = listOf(flat, flat))
    }

    val reflectionStrength = if (glassEnabled) effects.highlightReflection else 0.15f
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) (0.14f + reflectionStrength * 0.36f) else (0.55f + reflectionStrength * 0.40f)),
            accent.copy(alpha = (0.12f + effects.glowIntensity * 0.35f).coerceIn(0.10f, 0.50f)),
            Color.White.copy(alpha = if (isDark) 0.08f else 0.30f)
        )
    )

    val glowElevation = if (glassEnabled && effects.glowIntensity > 0.05f) {
        (6f + effects.glowIntensity * 12f).dp
    } else {
        2.dp
    }

    Column(
        modifier = modifier
            .scale(pressScale)
            .shadow(
                elevation = glowElevation,
                shape = shape,
                ambientColor = accent.copy(alpha = effects.glowIntensity * 0.32f),
                spotColor = accent.copy(alpha = effects.glowIntensity * 0.40f)
            )
            .clip(shape)
            .background(brush = fillBrush)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun LiquidGlassActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    config: PersonalizationConfig,
    isPrimary: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isPersonalizationDark(config)
    val accent = if (config.useCustomColors) config.primaryColor else Color(0xFF0A84FF)
    val cyan = if (config.useCustomColors) config.accentColor else Color(0xFF38BDF8)
    val bgBrush = if (isPrimary) {
        Brush.horizontalGradient(colors = listOf(accent, cyan))
    } else {
        val alpha = if (isDark) 0.22f else 0.72f
        val tint = if (isDark) Color(0xFF1A2D46).copy(alpha = alpha) else Color.White.copy(alpha = alpha)
        Brush.horizontalGradient(colors = listOf(tint, tint))
    }
    val contentColor = if (isPrimary) {
        if (accent.luminance() > 0.5f) Color(0xFF0A1424) else Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bgBrush)
            .border(
                width = 1.dp,
                color = if (isPrimary) Color.White.copy(alpha = 0.35f)
                else accent.copy(alpha = 0.28f),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> LiquidGlassOptionSelector(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    config: PersonalizationConfig,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "opt"
) {
    val isDark = isPersonalizationDark(config)
    val accent = if (config.useCustomColors) config.primaryColor else Color(0xFF0A84FF)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { idx, item ->
                val isSelected = item == selected
                val bg = when {
                    isSelected -> accent.copy(alpha = if (isDark) 0.30f else 0.16f)
                    isDark -> Color.White.copy(alpha = 0.06f)
                    else -> Color(0xFFE7EFF8).copy(alpha = 0.80f)
                }
                val border = if (isSelected) accent else Color.White.copy(alpha = if (isDark) 0.14f else 0.55f)
                val textColor = if (isSelected) {
                    if (isDark) Color(0xFF93D5FF) else accent
                } else {
                    MaterialTheme.colorScheme.onSurface
                }

                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(bg)
                        .border(1.dp, border, RoundedCornerShape(20.dp))
                        .clickable { onSelect(item) }
                        .padding(horizontal = 14.dp)
                        .testTag("${testTagPrefix}_$idx"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelOf(item),
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun LiquidGlassSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    config: PersonalizationConfig,
    modifier: Modifier = Modifier,
    sliderTestTag: String? = null
) {
    val accent = if (config.useCustomColors) config.primaryColor else Color(0xFF0A84FF)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueLabel,
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = accent.copy(alpha = 0.22f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .let { if (sliderTestTag != null) it.testTag(sliderTestTag) else it }
        )
    }
}

@Composable
fun LiquidGlassToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    config: PersonalizationConfig,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val accent = if (config.useCustomColors) config.primaryColor else Color(0xFF0A84FF)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.5.sp,
                lineHeight = 16.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent
            ),
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}

@Composable
fun ColorSwatchRow(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val hexText = remember(color) {
        String.format(Locale.US, "#%06X", color.toArgb() and 0xFFFFFF)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
            .let { if (testTag != null) it.testTag(testTag) else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = hexText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
            )
        }
    }
}

private val CURATED_LIQUID_SWATCHES = listOf(
    0xFF0A84FFL, // Apple Ocean Blue
    0xFF38BDF8L, // Frost Cyan
    0xFF00D4FFL, // Bright Aqua
    0xFF12C43CL, // HET Emerald
    0xFF6366F1L, // Indigo Glass
    0xFF8B5CF6L, // Soft Violet
    0xFFEC4899L, // Rose Berry
    0xFFF59E0BL, // Warm Amber
    0xFFE53935L, // Crimson
    0xFFFFFFFFL, // Pure Frost White
    0xFF141B26L  // Obsidian Slate
)

/**
 * Full interactive HSV + Spectrum + Hex Custom Color Picker Dialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiquidGlassColorPickerDialog(
    title: String,
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    val currentColor = remember(hue, sat, value) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
    }
    var hexInput by remember(initialColor) {
        mutableStateOf(String.format(Locale.US, "%06X", initialColor.toArgb() and 0xFFFFFF))
    }
    var hexError by remember { mutableStateOf<String?>(null) }

    fun syncHexFromHsv(h: Float, s: Float, v: Float) {
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        hexInput = String.format(Locale.US, "%06X", rgb and 0xFFFFFF)
        hexError = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 420.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(26.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .testTag("color_picker_preview_swatch")
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 2D Saturation / Brightness Panel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val newSat = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val newVal = 1f - (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                sat = newSat
                                value = newVal
                                syncHexFromHsv(hue, newSat, newVal)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val newSat = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val newVal = 1f - (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                sat = newSat
                                value = newVal
                                syncHexFromHsv(hue, newSat, newVal)
                            }
                        }
                        .testTag("color_picker_sv_pad")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val pureHueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                        drawRect(brush = Brush.horizontalGradient(colors = listOf(Color.White, pureHueColor)))
                        drawRect(brush = Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black)))

                        val rx = sat * size.width
                        val ry = (1f - value) * size.height
                        drawCircle(
                            color = Color.White,
                            radius = 9.dp.toPx(),
                            center = Offset(rx, ry),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.45f),
                            radius = 10.5.dp.toPx(),
                            center = Offset(rx, ry),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }

                // Hue Spectrum Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val newHue = ((offset.x / size.width.toFloat()).coerceIn(0f, 1f)) * 360f
                                hue = newHue
                                syncHexFromHsv(newHue, sat, value)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val newHue = ((change.position.x / size.width.toFloat()).coerceIn(0f, 1f)) * 360f
                                hue = newHue
                                syncHexFromHsv(newHue, sat, value)
                            }
                        }
                        .testTag("color_picker_hue_bar")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val hueColors = listOf(
                            Color.Red,
                            Color.Yellow,
                            Color.Green,
                            Color.Cyan,
                            Color.Blue,
                            Color.Magenta,
                            Color.Red
                        )
                        drawRect(brush = Brush.horizontalGradient(colors = hueColors))
                        val hx = (hue / 360f).coerceIn(0f, 1f) * size.width
                        drawCircle(
                            color = Color.White,
                            radius = 10.dp.toPx(),
                            center = Offset(hx, size.height / 2f),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }

                // Hex Code Input
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { raw ->
                        val cleaned = raw.trim().removePrefix("#").uppercase(Locale.US).take(6)
                        hexInput = cleaned
                        if (cleaned.length == 6) {
                            val parsed = cleaned.toLongOrNull(16)
                            if (parsed != null) {
                                val argb = (0xFF000000L or parsed).toInt()
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(argb, hsv)
                                hue = hsv[0]
                                sat = hsv[1]
                                value = hsv[2]
                                hexError = null
                            } else {
                                hexError = "Invalid hex digits (0-9, A-F)"
                            }
                        } else {
                            hexError = "Enter 6-character hex code"
                        }
                    },
                    prefix = { Text("#") },
                    label = { Text("Hex Color") },
                    isError = hexError != null,
                    supportingText = hexError?.let { err -> { Text(err) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("color_picker_hex_input")
                )

                // Quick Swatches
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CURATED_LIQUID_SWATCHES.forEachIndexed { idx, argb ->
                        val swatchColor = Color(argb)
                        val isMatch = (currentColor.toArgb() and 0xFFFFFF) == (argb.toInt() and 0xFFFFFF)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (isMatch) 2.5.dp else 1.dp,
                                    color = if (isMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable {
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(argb.toInt(), hsv)
                                    hue = hsv[0]
                                    sat = hsv[1]
                                    value = hsv[2]
                                    hexInput = String.format(Locale.US, "%06X", argb.toInt() and 0xFFFFFF)
                                    hexError = null
                                }
                                .testTag("color_picker_swatch_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isMatch) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected color",
                                    tint = if (swatchColor.luminance() > 0.5f) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = hexInput.trim()
                    val resolved = if (clean.length == 6 && clean.toLongOrNull(16) != null) {
                        Color((0xFF000000L or clean.toLong(16)).toInt())
                    } else {
                        currentColor
                    }
                    onColorSelected(resolved)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("color_picker_confirm_button")
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
