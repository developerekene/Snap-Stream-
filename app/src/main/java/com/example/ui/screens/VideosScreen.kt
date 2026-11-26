package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.example.model.ViewMode
import com.example.ui.components.BrowseFilterRow
import com.example.ui.components.EmptyMediaState
import com.example.ui.components.FileInfoDialog
import com.example.ui.components.FolderChipRow
import com.example.ui.components.MediaGridCard
import com.example.ui.components.MediaListRow
import com.example.ui.components.PermissionPromptCard
import java.io.File

@Composable
fun VideosScreen(
    videos: List<MediaItemEntity>,
    viewMode: ViewMode,
    selectedFilter: BrowseFilter,
    folders: List<String>,
    selectedFolder: String?,
    hasMediaPermission: Boolean,
    isScanning: Boolean,
    selectedIds: Set<Long>,
    loadVideoThumbnail: suspend (MediaItemEntity) -> File?,
    onSelectFilter: (BrowseFilter) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onVideoClick: (MediaItemEntity) -> Unit,
    onVideoLongClick: (MediaItemEntity) -> Unit,
    onEditVideo: (MediaItemEntity) -> Unit,
    onToggleFavorite: (MediaItemEntity) -> Unit,
    onShareVideo: (MediaItemEntity) -> Unit,
    onDeleteVideo: (MediaItemEntity) -> Unit,
    onGrantPermission: () -> Unit,
    onPickVideosManually: () -> Unit,
    onRescanDevice: () -> Unit,
    modifier: Modifier = Modifier
) {
    var infoItem by remember { mutableStateOf<MediaItemEntity?>(null) }
    val isSelectionMode = selectedIds.isNotEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!isSelectionMode && (hasMediaPermission || videos.isNotEmpty())) {
                ExtendedFloatingActionButton(
                    onClick = if (hasMediaPermission) onRescanDevice else onPickVideosManually,
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
                    modifier = Modifier.testTag("videos_rescan_fab")
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
                filters = BrowseFilter.videoFilters,
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
                    !hasMediaPermission && videos.isEmpty() -> {
                        PermissionPromptCard(
                            onGrantPermission = onGrantPermission,
                            onPickFilesManually = onPickVideosManually,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    videos.isEmpty() -> {
                        EmptyMediaState(
                            icon = Icons.Filled.VideoLibrary,
                            title = stringResource(R.string.empty_videos_title),
                            subtitle = stringResource(R.string.empty_videos_subtitle),
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
                                .testTag("videos_list")
                        ) {
                            items(videos, key = { it.id }) { item ->
                                MediaListRow(
                                    item = item,
                                    isSelected = item.id in selectedIds,
                                    isSelectionMode = isSelectionMode,
                                    loadVideoThumbnail = loadVideoThumbnail,
                                    onClick = { onVideoClick(item) },
                                    onLongClick = { onVideoLongClick(item) },
                                    onEdit = { onEditVideo(item) },
                                    onToggleFavorite = { onToggleFavorite(item) },
                                    onShowInfo = { infoItem = item },
                                    onShare = { onShareVideo(item) },
                                    onDelete = { onDeleteVideo(item) }
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
                                .testTag("videos_grid")
                        ) {
                            items(videos, key = { it.id }) { item ->
                                MediaGridCard(
                                    item = item,
                                    isSelected = item.id in selectedIds,
                                    isSelectionMode = isSelectionMode,
                                    loadVideoThumbnail = loadVideoThumbnail,
                                    onClick = { onVideoClick(item) },
                                    onLongClick = { onVideoLongClick(item) },
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
