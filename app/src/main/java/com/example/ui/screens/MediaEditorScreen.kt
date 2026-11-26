package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.MediaItemEntity
import com.example.model.CropAspectRatio
import com.example.model.FilterPreset
import com.example.model.MediaType
import com.example.ui.components.CropOverlayCanvas
import com.example.ui.components.formatDuration
import com.example.ui.components.formatFileSize
import com.example.ui.viewmodel.EditorToolTab
import com.example.ui.viewmodel.MediaEditorUiState
import com.example.util.MediaFilterEngine
import java.io.File
import java.util.Locale

@Composable
fun MediaEditorScreen(
    state: MediaEditorUiState,
    loadVideoThumbnail: suspend (MediaItemEntity) -> File?,
    onBack: () -> Unit,
    onSelectTab: (EditorToolTab) -> Unit,
    onSelectFilter: (FilterPreset) -> Unit,
    onFilterIntensityChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onRotate90: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onSelectAspectRatio: (CropAspectRatio) -> Unit,
    onCropRectChange: (Float, Float, Float, Float) -> Unit,
    onResetCrop: () -> Unit,
    onTrimRangeChange: (Long, Long) -> Unit,
    onTitleChange: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onResetAll: () -> Unit,
    onSave: (Boolean) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A12))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D1321))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("editor_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = "SnapStream Studio",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onResetAll,
                modifier = Modifier.testTag("editor_reset_all_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.RestartAlt,
                    contentDescription = "Reset all edits",
                    tint = Color.White.copy(alpha = 0.85f)
                )
            }
            IconButton(
                onClick = { onSave(true) },
                enabled = !state.isSaving && state.originalItem != null,
                modifier = Modifier.testTag("editor_save_copy_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.CopyAll,
                    contentDescription = "Save as copy",
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = { onSave(false) },
                enabled = !state.isSaving && state.originalItem != null,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("editor_save_button")
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Save",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }
            }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }

        val item = state.originalItem
        if (item == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.errorMessage ?: "Unable to load media",
                    color = Color.White
                )
            }
            return@Column
        }

        val previewData by produceState<Any?>(
            initialValue = if (item.typeEnum == MediaType.PHOTO) MediaFilterEngine.parseUriOrFile(item.originalUri) else null,
            key1 = item.id,
            key2 = item.originalUri
        ) {
            value = if (item.typeEnum == MediaType.VIDEO) {
                loadVideoThumbnail(item) ?: MediaFilterEngine.parseUriOrFile(item.originalUri)
            } else {
                MediaFilterEngine.parseUriOrFile(item.originalUri)
            }
        }

        val liveColorFilter = ColorFilter.colorMatrix(
            MediaFilterEngine.toComposeColorMatrix(
                preset = state.filterPreset,
                intensity = state.filterIntensity,
                brightness = state.brightness,
                contrast = state.contrast,
                saturation = state.saturation
            )
        )

        val baseAspect = if (item.widthPx > 0 && item.heightPx > 0) {
            val w = if (state.rotationDegrees % 180 == 0) item.widthPx else item.heightPx
            val h = if (state.rotationDegrees % 180 == 0) item.heightPx else item.widthPx
            (w.toFloat() / h.toFloat()).coerceIn(0.4f, 2.2f)
        } else 1f

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(baseAspect)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0E1422))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(previewData)
                        .crossfade(false)
                        .build(),
                    contentDescription = state.title,
                    contentScale = ContentScale.FillBounds,
                    colorFilter = liveColorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            rotationZ = state.rotationDegrees.toFloat(),
                            scaleX = if (state.flipHorizontal) -1f else 1f
                        )
                )

                val hasCrop = state.cropLeft > 0.005f || state.cropTop > 0.005f ||
                    state.cropRight < 0.995f || state.cropBottom < 0.995f
                if (state.activeTab == EditorToolTab.CROP || hasCrop) {
                    CropOverlayCanvas(
                        cropLeft = state.cropLeft,
                        cropTop = state.cropTop,
                        cropRight = state.cropRight,
                        cropBottom = state.cropBottom,
                        aspectRatio = state.cropAspectRatio.ratio,
                        onCropChange = { l, t, r, b ->
                            if (state.activeTab == EditorToolTab.CROP) {
                                onCropRectChange(l, t, r, b)
                            }
                        }
                    )
                }
            }
        }

        Surface(
            color = Color(0xFF0E1422),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 12.dp, bottom = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp)
                ) {
                    when (state.activeTab) {
                        EditorToolTab.FILTERS -> FiltersToolPanel(
                            previewData = previewData,
                            selectedPreset = state.filterPreset,
                            intensity = state.filterIntensity,
                            onSelectFilter = onSelectFilter,
                            onIntensityChange = onFilterIntensityChange
                        )
                        EditorToolTab.CROP -> CropToolPanel(
                            selectedRatio = state.cropAspectRatio,
                            rotationDegrees = state.rotationDegrees,
                            flipHorizontal = state.flipHorizontal,
                            onSelectRatio = onSelectAspectRatio,
                            onRotate90 = onRotate90,
                            onFlipHorizontal = onFlipHorizontal,
                            onResetCrop = onResetCrop
                        )
                        EditorToolTab.ADJUST -> AdjustToolPanel(
                            isVideo = item.typeEnum == MediaType.VIDEO,
                            durationMs = item.durationMs,
                            brightness = state.brightness,
                            contrast = state.contrast,
                            saturation = state.saturation,
                            trimStartMs = state.trimStartMs,
                            trimEndMs = state.trimEndMs,
                            onBrightnessChange = onBrightnessChange,
                            onContrastChange = onContrastChange,
                            onSaturationChange = onSaturationChange,
                            onTrimRangeChange = onTrimRangeChange
                        )
                        EditorToolTab.DETAILS -> DetailsToolPanel(
                            title = state.title,
                            folderName = state.folderName,
                            fileSizeBytes = item.fileSizeBytes,
                            resolution = "${item.widthPx}×${item.heightPx}",
                            isFavorite = state.isFavorite,
                            onTitleChange = onTitleChange,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EditorToolTabButton(
                        icon = Icons.Filled.AutoFixHigh,
                        label = EditorToolTab.FILTERS.label,
                        selected = state.activeTab == EditorToolTab.FILTERS,
                        onClick = { onSelectTab(EditorToolTab.FILTERS) },
                        testTag = "editor_tab_filters"
                    )
                    EditorToolTabButton(
                        icon = Icons.Filled.Crop,
                        label = EditorToolTab.CROP.label,
                        selected = state.activeTab == EditorToolTab.CROP,
                        onClick = { onSelectTab(EditorToolTab.CROP) },
                        testTag = "editor_tab_crop"
                    )
                    EditorToolTabButton(
                        icon = Icons.Filled.Tune,
                        label = EditorToolTab.ADJUST.label,
                        selected = state.activeTab == EditorToolTab.ADJUST,
                        onClick = { onSelectTab(EditorToolTab.ADJUST) },
                        testTag = "editor_tab_adjust"
                    )
                    EditorToolTabButton(
                        icon = Icons.Filled.Info,
                        label = EditorToolTab.DETAILS.label,
                        selected = state.activeTab == EditorToolTab.DETAILS,
                        onClick = { onSelectTab(EditorToolTab.DETAILS) },
                        testTag = "editor_tab_details"
                    )
                }
            }
        }
    }
}

@Composable
private fun FiltersToolPanel(
    previewData: Any?,
    selectedPreset: FilterPreset,
    intensity: Float,
    onSelectFilter: (FilterPreset) -> Unit,
    onIntensityChange: (Float) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(FilterPreset.entries, key = { it.name }) { preset ->
                val selected = preset == selectedPreset
                val thumbFilter = ColorFilter.colorMatrix(
                    MediaFilterEngine.toComposeColorMatrix(preset = preset, intensity = 1f)
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(74.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectFilter(preset) }
                        .padding(4.dp)
                        .testTag("filter_preset_${preset.name.lowercase()}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(previewData)
                                .size(160)
                                .build(),
                            contentDescription = preset.label,
                            contentScale = ContentScale.Crop,
                            colorFilter = thumbFilter,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = preset.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        if (selectedPreset != FilterPreset.ORIGINAL) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Intensity",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.width(68.dp)
                )
                Slider(
                    value = intensity,
                    onValueChange = onIntensityChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("filter_intensity_slider")
                )
                Text(
                    text = "${(intensity * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.width(42.dp)
                )
            }
        } else {
            Text(
                text = "Tap any filter preset above to adjust color tone",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun CropToolPanel(
    selectedRatio: CropAspectRatio,
    rotationDegrees: Int,
    flipHorizontal: Boolean,
    onSelectRatio: (CropAspectRatio) -> Unit,
    onRotate90: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onResetCrop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Drag crop handles on canvas • Aspect Ratio",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.72f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(CropAspectRatio.entries, key = { it.name }) { ratio ->
                val selected = ratio == selectedRatio
                FilterChip(
                    selected = selected,
                    onClick = { onSelectRatio(ratio) },
                    label = { Text(ratio.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF19233B),
                        labelColor = Color.White.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier.testTag("crop_ratio_${ratio.name.lowercase()}")
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onRotate90,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .weight(1f)
                    .testTag("crop_rotate_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                    contentDescription = "Rotate 90 degrees",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("${rotationDegrees}°")
            }

            OutlinedButton(
                onClick = onFlipHorizontal,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (flipHorizontal) MaterialTheme.colorScheme.secondary else Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("crop_flip_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Flip,
                    contentDescription = "Flip horizontal",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Flip")
            }

            TextButton(
                onClick = onResetCrop,
                modifier = Modifier.testTag("crop_reset_button")
            ) {
                Text("Reset", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun AdjustToolPanel(
    isVideo: Boolean,
    durationMs: Long,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    trimStartMs: Long,
    trimEndMs: Long,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onTrimRangeChange: (Long, Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AdjustmentSliderRow(
            label = "Brightness",
            value = brightness,
            valueRange = -0.5f..0.5f,
            displayText = String.format(Locale.US, "%+d", (brightness * 100).toInt()),
            onValueChange = onBrightnessChange,
            testTag = "adjust_brightness_slider"
        )
        AdjustmentSliderRow(
            label = "Contrast",
            value = contrast,
            valueRange = 0.5f..1.8f,
            displayText = String.format(Locale.US, "%.2fx", contrast),
            onValueChange = onContrastChange,
            testTag = "adjust_contrast_slider"
        )
        AdjustmentSliderRow(
            label = "Saturation",
            value = saturation,
            valueRange = 0f..2f,
            displayText = "${(saturation * 100).toInt()}%",
            onValueChange = onSaturationChange,
            testTag = "adjust_saturation_slider"
        )

        if (isVideo && durationMs > 1000L) {
            val safeEnd = trimEndMs.coerceIn(500L, durationMs)
            val safeStart = trimStartMs.coerceIn(0L, safeEnd - 500L)
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Trim Clip",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                    Text(
                        text = "${formatDuration(safeStart)} - ${formatDuration(safeEnd)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                RangeSlider(
                    value = safeStart.toFloat()..safeEnd.toFloat(),
                    onValueChange = { range ->
                        val s = range.start.toLong()
                        val e = range.endInclusive.toLong().coerceAtLeast(s + 500L)
                        onTrimRangeChange(s, e)
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("video_trim_range_slider")
                )
            }
        }
    }
}

@Composable
private fun AdjustmentSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayText: String,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.width(78.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .weight(1f)
                .testTag(testTag)
        )
        Text(
            text = displayText,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.width(46.dp)
        )
    }
}

@Composable
private fun DetailsToolPanel(
    title: String,
    folderName: String,
    fileSizeBytes: Long,
    resolution: String,
    isFavorite: Boolean,
    onTitleChange: (String) -> Unit,
    onToggleFavorite: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("File Name", color = Color.White.copy(alpha = 0.7f)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("editor_title_input")
            )
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF19233B))
                    .testTag("editor_favorite_button")
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) MaterialTheme.colorScheme.secondary else Color.White
                )
            }
        }

        Text(
            text = "Folder: $folderName • $resolution • ${formatFileSize(fileSizeBytes)}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.78f)
        )
        Text(
            text = "Edited photos save locally to Pictures/SnapStream.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.56f)
        )
    }
}

@Composable
private fun EditorToolTabButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.72f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
