package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.MediaRepository
import com.example.data.PreferencesRepository
import com.example.data.local.MediaItemEntity
import com.example.data.local.SnapStreamDatabase
import com.example.ui.components.SnapStreamBottomBar
import com.example.ui.components.SnapStreamNavigationRail
import com.example.ui.components.SnapStreamTab
import com.example.ui.components.SnapStreamTopBar
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.MediaEditorScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.VideosScreen
import com.example.ui.theme.SnapStreamTheme
import com.example.ui.viewmodel.MediaEditorViewModel
import com.example.ui.viewmodel.SnapStreamViewModel
import com.example.util.MediaFilterEngine
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnapStreamAppRoot()
        }
    }
}

private fun requiredMediaPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun hasMediaAccess(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun shareMediaItems(context: Context, items: List<MediaItemEntity>) {
    if (items.isEmpty()) return
    val shareUris = ArrayList<Uri>(items.size)
    for (item in items) {
        val parsed = MediaFilterEngine.parseUriOrFile(item.contentUri)
        val shareUri = if (parsed.scheme == "content") {
            parsed
        } else {
            val filePath = parsed.path ?: continue
            val file = File(filePath)
            if (!file.exists()) continue
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        shareUris.add(shareUri)
    }
    if (shareUris.isEmpty()) return

    val intent = if (shareUris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = items.first().mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, shareUris.first())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share media"))
    }
}

@Composable
fun SnapStreamAppRoot() {
    val activityContext = LocalContext.current
    val appContext = activityContext.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current

    val preferencesRepository = remember(appContext) { PreferencesRepository(appContext) }
    val mediaRepository = remember(appContext) {
        val db = SnapStreamDatabase.getInstance(appContext)
        MediaRepository(appContext, db.mediaItemDao())
    }

    val mainViewModel: SnapStreamViewModel = viewModel(
        factory = SnapStreamViewModel.Factory(mediaRepository, preferencesRepository)
    )

    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
    val allMedia by mainViewModel.allMedia.collectAsStateWithLifecycle()
    val galleryItems by mainViewModel.filteredGalleryItems.collectAsStateWithLifecycle()
    val videoItems by mainViewModel.filteredVideoItems.collectAsStateWithLifecycle()
    val galleryFolders by mainViewModel.galleryFolders.collectAsStateWithLifecycle()
    val videoFolders by mainViewModel.videoFolders.collectAsStateWithLifecycle()
    val selectedGalleryFilter by mainViewModel.selectedGalleryFilter.collectAsStateWithLifecycle()
    val selectedGalleryFolder by mainViewModel.selectedGalleryFolder.collectAsStateWithLifecycle()
    val selectedVideoFilter by mainViewModel.selectedVideoFilter.collectAsStateWithLifecycle()
    val selectedVideoFolder by mainViewModel.selectedVideoFolder.collectAsStateWithLifecycle()
    val searchQuery by mainViewModel.searchQuery.collectAsStateWithLifecycle()
    val isScanning by mainViewModel.isScanning.collectAsStateWithLifecycle()
    val selectedIds by mainViewModel.selectedItemIds.collectAsStateWithLifecycle()
    val storageInfo by mainViewModel.storageInfo.collectAsStateWithLifecycle()

    var hasMediaPermission by remember { mutableStateOf(hasMediaAccess(appContext)) }
    var promptedFirstLaunch by rememberSaveable { mutableStateOf(false) }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isTopLevelTab = SnapStreamTab.entries.any { it.route == currentRoute }

    var isSearchExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            val granted = hasMediaAccess(appContext)
            hasMediaPermission = granted
            if (granted) {
                mainViewModel.scanDeviceMedia(showToast = true)
            }
        }
    )

    val deleteConfirmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = { result ->
            mainViewModel.onSystemDeleteConfirmed(result.resultCode == Activity.RESULT_OK)
        }
    )

    val manualPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 25),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                mainViewModel.importPickedMedia(uris)
            }
        }
    )

    // Re-check media access when returning from system settings & scan on first launch
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = hasMediaAccess(appContext)
                val transitionedToGranted = !hasMediaPermission && nowGranted
                hasMediaPermission = nowGranted
                if (transitionedToGranted) {
                    mainViewModel.scanDeviceMedia(showToast = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (hasMediaAccess(appContext)) {
            mainViewModel.scanDeviceMedia(showToast = false)
        } else if (!promptedFirstLaunch) {
            promptedFirstLaunch = true
            permissionLauncher.launch(requiredMediaPermissions())
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.deleteConfirmationSender.collect { sender ->
            deleteConfirmLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    BackHandler(enabled = isTopLevelTab && selectedIds.isNotEmpty()) {
        mainViewModel.clearSelection()
    }

    val navigateToTab: (SnapStreamTab) -> Unit = { tab ->
        isSearchExpanded = false
        mainViewModel.updateSearchQuery("")
        mainViewModel.clearSelection()
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    SnapStreamTheme(darkTheme = preferences.darkModeEnabled) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (isTopLevelTab && useNavigationRail) {
                    SnapStreamNavigationRail(
                        currentRoute = currentRoute,
                        onNavigate = navigateToTab
                    )
                }

                Scaffold(
                    modifier = Modifier.weight(1f),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        if (isTopLevelTab) {
                            val isExplorerTab = currentRoute != SnapStreamTab.PROFILE.route
                            val topTitle = when (currentRoute) {
                                SnapStreamTab.VIDEOS.route -> "Videos"
                                SnapStreamTab.PROFILE.route -> "Profile Settings"
                                else -> "SnapStream"
                            }
                            val topSubtitle = when (currentRoute) {
                                SnapStreamTab.VIDEOS.route -> "${videoItems.size} video${if (videoItems.size == 1) "" else "s"} on device"
                                SnapStreamTab.PROFILE.route -> if (preferences.darkModeEnabled) "Studio Dark" else "White Canvas"
                                else -> "${galleryItems.size} file${if (galleryItems.size == 1) "" else "s"} on device"
                            }
                            val visibleList = if (currentRoute == SnapStreamTab.VIDEOS.route) videoItems else galleryItems

                            SnapStreamTopBar(
                                title = topTitle,
                                subtitle = topSubtitle,
                                isDarkMode = preferences.darkModeEnabled,
                                onToggleDarkMode = mainViewModel::toggleDarkMode,
                                showExplorerControls = isExplorerTab,
                                viewMode = preferences.viewMode,
                                onToggleViewMode = mainViewModel::toggleViewMode,
                                sortOrder = preferences.sortOrder,
                                onSelectSortOrder = mainViewModel::setSortOrder,
                                isSearchExpanded = isSearchExpanded,
                                searchQuery = searchQuery,
                                onToggleSearch = {
                                    isSearchExpanded = !isSearchExpanded
                                    if (!isSearchExpanded) mainViewModel.updateSearchQuery("")
                                },
                                onSearchQueryChange = mainViewModel::updateSearchQuery,
                                selectedCount = if (isExplorerTab) selectedIds.size else 0,
                                onClearSelection = mainViewModel::clearSelection,
                                onSelectAll = { mainViewModel.selectAll(visibleList) },
                                onShareSelected = {
                                    val chosen = allMedia.filter { it.id in selectedIds }
                                    shareMediaItems(activityContext, chosen)
                                    mainViewModel.clearSelection()
                                },
                                onFavoriteSelected = mainViewModel::favoriteSelected,
                                onDeleteSelected = {
                                    val chosen = allMedia.filter { it.id in selectedIds }
                                    mainViewModel.deleteItems(chosen)
                                }
                            )
                        }
                    },
                    bottomBar = {
                        if (isTopLevelTab && !useNavigationRail) {
                            SnapStreamBottomBar(
                                currentRoute = currentRoute,
                                onNavigate = navigateToTab
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = innerPadding.calculateTopPadding())
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = SnapStreamTab.GALLERY.route
                        ) {
                            composable(SnapStreamTab.GALLERY.route) {
                                GalleryScreen(
                                    items = galleryItems,
                                    viewMode = preferences.viewMode,
                                    selectedFilter = selectedGalleryFilter,
                                    folders = galleryFolders,
                                    selectedFolder = selectedGalleryFolder,
                                    storageInfo = storageInfo,
                                    hasMediaPermission = hasMediaPermission,
                                    isScanning = isScanning,
                                    selectedIds = selectedIds,
                                    loadVideoThumbnail = mainViewModel::getVideoThumbnailFile,
                                    onSelectFilter = mainViewModel::selectGalleryFilter,
                                    onSelectFolder = mainViewModel::selectGalleryFolder,
                                    onMediaClick = { item ->
                                        if (selectedIds.isNotEmpty()) {
                                            mainViewModel.toggleItemSelection(item.id)
                                        } else {
                                            navController.navigate("viewer/${item.id}")
                                        }
                                    },
                                    onMediaLongClick = { item ->
                                        mainViewModel.toggleItemSelection(item.id)
                                    },
                                    onEditMedia = { item ->
                                        navController.navigate("editor/${item.id}")
                                    },
                                    onToggleFavorite = mainViewModel::toggleFavorite,
                                    onShareMedia = { item ->
                                        shareMediaItems(activityContext, listOf(item))
                                    },
                                    onDeleteMedia = { item ->
                                        mainViewModel.deleteItems(listOf(item))
                                    },
                                    onGrantPermission = {
                                        permissionLauncher.launch(requiredMediaPermissions())
                                    },
                                    onPickFilesManually = {
                                        manualPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                        )
                                    },
                                    onRescanDevice = {
                                        if (hasMediaPermission) {
                                            mainViewModel.scanDeviceMedia(showToast = true)
                                        } else {
                                            permissionLauncher.launch(requiredMediaPermissions())
                                        }
                                    },
                                    onOpenStorageTab = { navigateToTab(SnapStreamTab.PROFILE) },
                                    modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                                )
                            }

                            composable(SnapStreamTab.VIDEOS.route) {
                                VideosScreen(
                                    videos = videoItems,
                                    viewMode = preferences.viewMode,
                                    selectedFilter = selectedVideoFilter,
                                    folders = videoFolders,
                                    selectedFolder = selectedVideoFolder,
                                    hasMediaPermission = hasMediaPermission,
                                    isScanning = isScanning,
                                    selectedIds = selectedIds,
                                    loadVideoThumbnail = mainViewModel::getVideoThumbnailFile,
                                    onSelectFilter = mainViewModel::selectVideoFilter,
                                    onSelectFolder = mainViewModel::selectVideoFolder,
                                    onVideoClick = { item ->
                                        if (selectedIds.isNotEmpty()) {
                                            mainViewModel.toggleItemSelection(item.id)
                                        } else {
                                            navController.navigate("viewer/${item.id}")
                                        }
                                    },
                                    onVideoLongClick = { item ->
                                        mainViewModel.toggleItemSelection(item.id)
                                    },
                                    onEditVideo = { item ->
                                        navController.navigate("editor/${item.id}")
                                    },
                                    onToggleFavorite = mainViewModel::toggleFavorite,
                                    onShareVideo = { item ->
                                        shareMediaItems(activityContext, listOf(item))
                                    },
                                    onDeleteVideo = { item ->
                                        mainViewModel.deleteItems(listOf(item))
                                    },
                                    onGrantPermission = {
                                        permissionLauncher.launch(requiredMediaPermissions())
                                    },
                                    onPickVideosManually = {
                                        manualPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    onRescanDevice = {
                                        if (hasMediaPermission) {
                                            mainViewModel.scanDeviceMedia(showToast = true)
                                        } else {
                                            permissionLauncher.launch(requiredMediaPermissions())
                                        }
                                    },
                                    modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                                )
                            }

                            composable(SnapStreamTab.PROFILE.route) {
                                ProfileSettingsScreen(
                                    preferences = preferences,
                                    storageInfo = storageInfo,
                                    isScanning = isScanning,
                                    onUpdateDeviceLabel = mainViewModel::setDeviceLabel,
                                    onToggleDarkMode = mainViewModel::setDarkMode,
                                    onSelectViewMode = mainViewModel::setViewMode,
                                    onToggleSaveAsCopy = mainViewModel::setSaveEditsAsCopy,
                                    onToggleHighQuality = mainViewModel::setHighQualityExport,
                                    onRescanDevice = {
                                        if (hasMediaPermission) {
                                            mainViewModel.scanDeviceMedia(showToast = true)
                                        } else {
                                            permissionLauncher.launch(requiredMediaPermissions())
                                        }
                                    },
                                    onClearCache = mainViewModel::clearCache,
                                    modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                                )
                            }

                            composable(
                                route = "viewer/{mediaId}",
                                arguments = listOf(navArgument("mediaId") { type = NavType.LongType })
                            ) { entry ->
                                val mediaId = entry.arguments?.getLong("mediaId") ?: -1L
                                val item by mainViewModel.observeItem(mediaId)
                                    .collectAsStateWithLifecycle(initialValue = null)

                                MediaViewerScreen(
                                    item = item,
                                    onBack = { navController.popBackStack() },
                                    onEdit = { id -> navController.navigate("editor/$id") },
                                    onToggleFavorite = mainViewModel::toggleFavorite,
                                    onShare = { target ->
                                        shareMediaItems(activityContext, listOf(target))
                                    },
                                    onDelete = { target ->
                                        mainViewModel.deleteItems(listOf(target))
                                    }
                                )
                            }

                            composable(
                                route = "editor/{mediaId}",
                                arguments = listOf(navArgument("mediaId") { type = NavType.LongType })
                            ) { entry ->
                                val mediaId = entry.arguments?.getLong("mediaId") ?: -1L
                                val editorViewModel: MediaEditorViewModel = viewModel(
                                    key = "editor_$mediaId",
                                    factory = MediaEditorViewModel.Factory(
                                        mediaId = mediaId,
                                        mediaRepository = mediaRepository,
                                        preferencesRepository = preferencesRepository
                                    )
                                )
                                val editorState by editorViewModel.uiState.collectAsStateWithLifecycle()

                                MediaEditorScreen(
                                    state = editorState,
                                    loadVideoThumbnail = mainViewModel::getVideoThumbnailFile,
                                    onBack = { navController.popBackStack() },
                                    onSelectTab = editorViewModel::selectTab,
                                    onSelectFilter = editorViewModel::selectFilter,
                                    onFilterIntensityChange = editorViewModel::updateFilterIntensity,
                                    onBrightnessChange = editorViewModel::updateBrightness,
                                    onContrastChange = editorViewModel::updateContrast,
                                    onSaturationChange = editorViewModel::updateSaturation,
                                    onRotate90 = editorViewModel::rotate90,
                                    onFlipHorizontal = editorViewModel::toggleFlipHorizontal,
                                    onSelectAspectRatio = editorViewModel::selectAspectRatio,
                                    onCropRectChange = editorViewModel::updateCropRect,
                                    onResetCrop = editorViewModel::resetCrop,
                                    onTrimRangeChange = editorViewModel::updateTrimRange,
                                    onTitleChange = editorViewModel::updateTitle,
                                    onToggleFavorite = editorViewModel::toggleFavorite,
                                    onResetAll = editorViewModel::resetAllEdits,
                                    onSave = { forceCopy ->
                                        editorViewModel.saveEdits(
                                            forceSaveAsCopy = if (forceCopy) true else null
                                        ) { success, message ->
                                            scope.launch { snackbarHostState.showSnackbar(message) }
                                            if (success) {
                                                navController.popBackStack()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
