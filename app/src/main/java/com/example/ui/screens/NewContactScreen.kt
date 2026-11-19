package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ContactFormData
import com.example.ui.components.ContactCircleAvatar
import com.example.ui.theme.ColorOsGreen
import com.example.ui.theme.ColorOsTextHint
import com.example.ui.theme.ColorOsWhite
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * OPPO ColorOS 16 New / Edit Contact Screen (matches IMG-20261107-WA0016):
 * - Top bar with green "Cancel" text button (left), centered "New contact" / "Edit contact" title, and "Done" button (right)
 * - Circular contact photo picker with interactive Crop & Adjust + Preview before saving
 * - "Save to Device" subtitle
 * - Rounded cards on soft grey/elevated surface:
 *   Card 1: Name & Phonetic name
 *   Card 2: Number (+ optional second number)
 *   Card 3: Ringtone ("Follow system")
 *   "Show more ⌄" toggle + Email, Company, Job title, Address, Notes cards
 */
@Composable
fun NewContactScreen(
    initialForm: ContactFormData,
    onCancel: () -> Unit,
    onSave: (ContactFormData) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onCancel() }

    val context = LocalContext.current
    val isEditing = initialForm.contactId != null

    var name by remember(initialForm) { mutableStateOf(initialForm.name) }
    var phoneticName by remember(initialForm) { mutableStateOf(initialForm.phoneticName) }
    var number by remember(initialForm) { mutableStateOf(initialForm.number) }
    var secondaryNumber by remember(initialForm) { mutableStateOf(initialForm.secondaryNumber) }
    var email by remember(initialForm) { mutableStateOf(initialForm.email) }
    var company by remember(initialForm) { mutableStateOf(initialForm.company) }
    var jobTitle by remember(initialForm) { mutableStateOf(initialForm.jobTitle) }
    var address by remember(initialForm) { mutableStateOf(initialForm.address) }
    var notes by remember(initialForm) { mutableStateOf(initialForm.notes) }
    var ringtoneLabel by remember { mutableStateOf("Follow system") }
    var showRingtoneChoiceDialog by remember { mutableStateOf(false) }
    var showMoreFields by remember { mutableStateOf(true) }

    var croppedPhotoBytes by remember(initialForm) { mutableStateOf(initialForm.photoBytes) }
    var croppedPreviewBitmap by remember(initialForm) {
        mutableStateOf<Bitmap?>(
            initialForm.photoBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        )
    }
    var pendingCropBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var showSecondaryPhoneField by remember(initialForm) {
        mutableStateOf(initialForm.secondaryNumber.isNotBlank())
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream)
                    if (decoded != null) {
                        pendingCropBitmap = decoded
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    val canSave = name.isNotBlank() || number.isNotBlank()

    val pageBg = MaterialTheme.colorScheme.surfaceVariant
    val cardBg = MaterialTheme.colorScheme.surface
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(pageBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
    ) {
        // Top Bar: "Cancel" (green), "New contact" / "Edit contact" (center), "Done" (right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Cancel",
                color = ColorOsGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCancel() }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .testTag("contact_form_cancel_button")
            )

            Text(
                text = if (isEditing) "Edit contact" else "New contact",
                color = textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("contact_form_title")
            )

            Text(
                text = "Done",
                color = if (canSave) ColorOsGreen else textSecondary.copy(alpha = 0.45f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = canSave) {
                        onSave(
                            initialForm.copy(
                                name = name,
                                phoneticName = phoneticName,
                                number = number,
                                secondaryNumber = secondaryNumber,
                                email = email,
                                company = company,
                                jobTitle = jobTitle,
                                address = address,
                                notes = notes,
                                photoBytes = croppedPhotoBytes
                            )
                        )
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .testTag("contact_form_save_button")
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Circular Contact Photo Picker + "Save to Device" subtitle (matches IMG-20261107-WA0016)
            item(key = "avatar_placeholder") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(textSecondary.copy(alpha = 0.22f))
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("contact_photo_picker_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        val previewBmp = croppedPreviewBitmap
                        if (previewBmp != null) {
                            Image(
                                bitmap = previewBmp.asImageBitmap(),
                                contentDescription = "Cropped contact photo preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else if (!initialForm.photoUri.isNullOrBlank()) {
                            ContactCircleAvatar(
                                name = name.ifBlank { "?" },
                                photoUri = initialForm.photoUri,
                                size = 92.dp,
                                fontSize = 34.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Select contact photo",
                                tint = ColorOsWhite,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Save to Device",
                            color = textSecondary,
                            fontSize = 13.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (croppedPreviewBitmap != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Remove photo",
                            color = ColorOsGreen,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                croppedPreviewBitmap = null
                                croppedPhotoBytes = null
                            }
                        )
                    }
                }
            }

            // Card 1: Name & Phonetic name
            item(key = "card_name_phonetic") {
                FormSectionCard(icon = Icons.Outlined.Person, cardBg = cardBg, iconTint = textPrimary) {
                    ColorOsCleanTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Name",
                        textColor = textPrimary,
                        showTrailingDropdown = true,
                        capitalization = KeyboardCapitalization.Words,
                        testTag = "input_contact_name"
                    )
                    HorizontalDivider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 13.dp)
                    )
                    ColorOsCleanTextField(
                        value = phoneticName,
                        onValueChange = { phoneticName = it },
                        placeholder = "Phonetic name",
                        textColor = textPrimary,
                        showTrailingDropdown = true,
                        capitalization = KeyboardCapitalization.Words,
                        testTag = "input_contact_phonetic"
                    )
                }
            }

            // Card 2: Phone Number
            item(key = "card_number") {
                FormSectionCard(icon = Icons.Outlined.Call, cardBg = cardBg, iconTint = textPrimary) {
                    ColorOsCleanTextField(
                        value = number,
                        onValueChange = { number = it },
                        placeholder = "Number",
                        textColor = textPrimary,
                        keyboardType = KeyboardType.Phone,
                        testTag = "input_contact_number"
                    )
                    if (showSecondaryPhoneField) {
                        HorizontalDivider(
                            color = dividerColor,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 13.dp)
                        )
                        ColorOsCleanTextField(
                            value = secondaryNumber,
                            onValueChange = { secondaryNumber = it },
                            placeholder = "Second phone number",
                            textColor = textPrimary,
                            keyboardType = KeyboardType.Phone,
                            testTag = "input_contact_secondary_number"
                        )
                    } else if (number.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "+ Add another number",
                            color = ColorOsGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { showSecondaryPhoneField = true }
                        )
                    }
                }
            }

            // Card 3: Ringtone ("Follow system" row matching IMG-20261107-WA0016)
            item(key = "card_ringtone") {
                Surface(
                    color = cardBg,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { showRingtoneChoiceDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Ringtone",
                            tint = textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(18.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ringtoneLabel,
                                color = textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Ringtone",
                                color = textSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // "Show more ⌄" toggle row
            item(key = "show_more_toggle") {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showMoreFields = !showMoreFields }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showMoreFields) "Show less" else "Show more",
                        color = textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (showMoreFields) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (showMoreFields) {
                // Card 4: Email
                item(key = "card_email") {
                    FormSectionCard(icon = Icons.Outlined.Email, cardBg = cardBg, iconTint = textPrimary) {
                        ColorOsCleanTextField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = "Email",
                            textColor = textPrimary,
                            keyboardType = KeyboardType.Email,
                            testTag = "input_contact_email"
                        )
                    }
                }

                // Card 5: Company & Job title
                item(key = "card_company_job") {
                    FormSectionCard(icon = Icons.Outlined.Apartment, cardBg = cardBg, iconTint = textPrimary) {
                        ColorOsCleanTextField(
                            value = company,
                            onValueChange = { company = it },
                            placeholder = "Company",
                            textColor = textPrimary,
                            capitalization = KeyboardCapitalization.Words,
                            testTag = "input_contact_company"
                        )
                        HorizontalDivider(
                            color = dividerColor,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 13.dp)
                        )
                        ColorOsCleanTextField(
                            value = jobTitle,
                            onValueChange = { jobTitle = it },
                            placeholder = "Job title",
                            textColor = textPrimary,
                            capitalization = KeyboardCapitalization.Words,
                            testTag = "input_contact_job_title"
                        )
                    }
                }

                // Card 6: Address & Notes
                item(key = "card_address_notes") {
                    FormSectionCard(icon = Icons.Outlined.LocationOn, cardBg = cardBg, iconTint = textPrimary) {
                        ColorOsCleanTextField(
                            value = address,
                            onValueChange = { address = it },
                            placeholder = "Address",
                            textColor = textPrimary,
                            capitalization = KeyboardCapitalization.Words,
                            testTag = "input_contact_address"
                        )
                        HorizontalDivider(
                            color = dividerColor,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 13.dp)
                        )
                        ColorOsCleanTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            placeholder = "Notes",
                            textColor = textPrimary,
                            capitalization = KeyboardCapitalization.Sentences,
                            testTag = "input_contact_notes"
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showRingtoneChoiceDialog) {
        val options = listOf("Follow system", "OPPO Tune", "Calm Dawn", "Pure Bell", "Crystal")
        AlertDialog(
            onDismissRequest = { showRingtoneChoiceDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Contact ringtone", color = textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { option ->
                        Surface(
                            color = if (ringtoneLabel == option) ColorOsGreen.copy(alpha = 0.14f) else pageBg,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ringtoneLabel = option
                                    showRingtoneChoiceDialog = false
                                }
                        ) {
                            Text(
                                text = if (ringtoneLabel == option) "✓ $option" else option,
                                color = textPrimary,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRingtoneChoiceDialog = false }) {
                    Text("Close", color = ColorOsGreen)
                }
            }
        )
    }

    // Interactive Contact Photo Crop, Zoom/Pan Adjust & Preview Dialog
    val sourceBitmap = pendingCropBitmap
    if (sourceBitmap != null) {
        ContactPhotoCropAndPreviewDialog(
            sourceBitmap = sourceBitmap,
            onDismiss = { pendingCropBitmap = null },
            onConfirmCropped = { croppedBmp, jpegBytes ->
                croppedPreviewBitmap = croppedBmp
                croppedPhotoBytes = jpegBytes
                pendingCropBitmap = null
            }
        )
    }
}

@Composable
private fun ContactPhotoCropAndPreviewDialog(
    sourceBitmap: Bitmap,
    onDismiss: () -> Unit,
    onConfirmCropped: (Bitmap, ByteArray) -> Unit
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    val cardBg = MaterialTheme.colorScheme.surface
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardBg,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Crop & preview photo",
                color = textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Drag to reposition and adjust zoom before saving.",
                    color = textSecondary,
                    fontSize = 13.sp
                )

                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(2.dp, ColorOsGreen, CircleShape)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                panX = (panX + dragAmount.x).coerceIn(-160f, 160f)
                                panY = (panY + dragAmount.y).coerceIn(-160f, 160f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = sourceBitmap.asImageBitmap(),
                        contentDescription = "Photo crop preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(panX.roundToInt(), panY.roundToInt()) }
                            .graphicsLayer(
                                scaleX = zoom,
                                scaleY = zoom
                            )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Zoom",
                        color = textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.width(46.dp)
                    )
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it },
                        valueRange = 1f..2.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = ColorOsGreen,
                            activeTrackColor = ColorOsGreen
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cropped = renderCroppedContactBitmap(
                        source = sourceBitmap,
                        zoom = zoom,
                        panX = panX,
                        panY = panY,
                        outputSize = 320
                    )
                    val out = ByteArrayOutputStream()
                    cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    onConfirmCropped(cropped, out.toByteArray())
                },
                colors = ButtonDefaults.buttonColors(containerColor = ColorOsGreen)
            ) {
                Text("Use photo", color = ColorOsWhite)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSecondary)
            }
        }
    )
}

private fun renderCroppedContactBitmap(
    source: Bitmap,
    zoom: Float,
    panX: Float,
    panY: Float,
    outputSize: Int
): Bitmap {
    val result = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val srcW = source.width.toFloat().coerceAtLeast(1f)
    val srcH = source.height.toFloat().coerceAtLeast(1f)
    val baseScale = max(outputSize / srcW, outputSize / srcH) * zoom

    val scaledW = srcW * baseScale
    val scaledH = srcH * baseScale
    val translateX = (outputSize - scaledW) / 2f + (panX / 200f) * outputSize
    val translateY = (outputSize - scaledH) / 2f + (panY / 200f) * outputSize

    val matrix = Matrix().apply {
        postScale(baseScale, baseScale)
        postTranslate(translateX, translateY)
    }
    canvas.drawBitmap(source, matrix, paint)
    return result
}

@Composable
private fun FormSectionCard(
    icon: ImageVector,
    cardBg: Color,
    iconTint: Color,
    content: @Composable () -> Unit
) {
    Surface(
        color = cardBg,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
            )
            Spacer(modifier = Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    }
}

@Composable
private fun ColorOsCleanTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textColor: Color,
    showTrailingDropdown: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            ),
            cursorBrush = SolidColor(ColorOsGreen),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = capitalization
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = ColorOsTextHint,
                            fontSize = 16.sp
                        )
                    }
                    innerTextField()
                }
            },
            modifier = Modifier
                .weight(1f)
                .testTag(testTag)
        )
        if (showTrailingDropdown) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color(0xFFB0B4BC),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
