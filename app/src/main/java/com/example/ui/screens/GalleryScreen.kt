package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.MediaItemEntity
import com.example.model.BrowseFilter
import com.example.model.DeviceStorageInfo
import com.example.model.ViewMode
import com.example.ui.components.BrowseFilterRow
import com.example.ui.components.EmptyMediaState
import com.example.ui.components.FileInfoDialog
import com.example.ui.components.FolderChipRow
import com.example.ui.components.MediaGridCard
import com.example.ui.components.MediaListRow
import com.example.ui.components.PermissionPromptCard
import com.example.ui.components.formatFileSize
import java.io.File

@Composable
fun GalleryScreen(
    items: List<MediaItemEntity>,
    viewMode: ViewMode,
    selectedFilter: BrowseFilter,
    folders: List<String>,
    selectedFolder: String?,
    storageInfo: DeviceStorageInfo,
    hasMediaPermission: Boolean,
    isScanning: Boolean,
    selectedIds: Set<Long>,
    loadVideoThumbnail: suspend (MediaItemEntity) -> File?,
    onSelectFilter: (BrowseFilter) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onMediaClick: (MediaItemEntity) -> Unit,
    onMediaLongClick: (MediaItemEntity) -> Unit,
    onEditMedia: (MediaItemEntity) -> Unit,
    onToggleFavorite: (MediaItemEntity) -> Unit,
    onShareMedia: (MediaItemEntity) -> Unit,
    onDeleteMedia: (MediaItemEntity) -> Unit,
    onGrantPermission: () -> Unit,
    onPickFilesManually: () -> Unit,
    onRescanDevice: () -> Unit,
    onOpenStorageTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    var infoItem by remember { mutableStateOf<MediaItemEntity?>(null) }
    val isSelectionMode = selectedIds.isNotEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!isSelectionMode && (hasMediaPermission || items.isNotEmpty())) {
                ExtendedFloatingActionButton(
                    onClick = if (hasMediaPermission) onRescanDevice else onPickFilesManually,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp),
                    icon = {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.action_rescan)
                            )
                        }
                    },
                    text = {
                        Text(
                            if (hasMediaPermission) stringResource(R.string.action_rescan)
                            else stringResource(R.string.action_pick_files)
                        )
                    },
                    modifier = Modifier.testTag("gallery_rescan_fab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            BrowseFilterRow(
                filters = BrowseFilter.galleryFilters,
                selectedFilter = selectedFilter,
                onSelectFilter = onSelectFilter
            )
            FolderChipRow(
                folders = folders,
                selectedFolder = selectedFolder,
                onSelectFolder = onSelectFolder
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columns = when {
                    maxWidth >= 900.dp -> 4
                    maxWidth >= 600.dp -> 3
                    else -> 2
                }

                when {
                    isScanning && items.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Scanning device media...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    !hasMediaPermission && items.isEmpty() -> {
                        PermissionPromptCard(
                            onGrantPermission = onGrantPermission,
                            onPickFilesManually = onPickFilesManually,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    items.isEmpty() -> {
                        EmptyMediaState(
                            icon = Icons.Filled.PhotoLibrary,
                            title = stringResource(R.string.empty_gallery_title),
                            subtitle = stringResource(R.string.empty_gallery_subtitle),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    viewMode == ViewMode.LIST -> {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("gallery_media_list")
                        ) {
                            item(key = "storage_bar") {
                                DeviceStorageBar(
                                    storageInfo = storageInfo,
                                    visibleCount = items.size,
                                    onClick = onOpenStorageTab
                                )
                            }
                            items(items, key = { it.id }) { item ->
                                MediaListRow(
                                    item = item,
                                    isSelected = item.id in selectedIds,
                                    isSelectionMode = isSelectionMode,
                                    loadVideoThumbnail = loadVideoThumbnail,
                                    onClick = { onMediaClick(item) },
                                    onLongClick = { onMediaLongClick(item) },
                                    onEdit = { onEditMedia(item) },
                                    onToggleFavorite = { onToggleFavorite(item) },
                                    onShowInfo = { infoItem = item },
                                    onShare = { onShareMedia(item) },
                                    onDelete = { onDeleteMedia(item) }
                                )
                            }
                        }
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 96.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("gallery_media_grid")
                        ) {
                            item(span = { GridItemSpan(columns) }) {
                                DeviceStorageBar(
                                    storageInfo = storageInfo,
                                    visibleCount = items.size,
                                    onClick = onOpenStorageTab
                                )
                            }

                            items(items, key = { it.id }) { item ->
                                MediaGridCard(
                                    item = item,
                                    isSelected = item.id in selectedIds,
                                    isSelectionMode = isSelectionMode,
                                    loadVideoThumbnail = loadVideoThumbnail,
                                    onClick = { onMediaClick(item) },
                                    onLongClick = { onMediaLongClick(item) },
                                    onToggleFavorite = { onToggleFavorite(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    infoItem?.let { item ->
        FileInfoDialog(item = item, onDismiss = { infoItem = null })
    }
}

@Composable
private fun DeviceStorageBar(
    storageInfo: DeviceStorageInfo,
    visibleCount: Int,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gallery_storage_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$visibleCount Media File${if (visibleCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${formatFileSize(storageInfo.photosBytes + storageInfo.videosBytes)} media • ${formatFileSize(storageInfo.availableBytes)} free",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SdStorage,
                        contentDescription = "Internal storage",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Device",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
