package com.example.model

enum class MediaType {
    PHOTO,
    VIDEO
}

enum class ViewMode(val label: String) {
    GRID("Grid"),
    LIST("List")
}

enum class SortOrder(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    LARGEST("Largest first"),
    SMALLEST("Smallest first"),
    NAME_ASC("Name (A–Z)")
}

enum class BrowseFilter(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    FAVORITES("Favorites"),
    EDITED("Edited"),
    LARGE("Large (>10 MB)");

    companion object {
        val galleryFilters = listOf(ALL, PHOTOS, VIDEOS, FAVORITES, EDITED, LARGE)
        val videoFilters = listOf(ALL, FAVORITES, EDITED, LARGE)
    }
}

enum class FilterPreset(val label: String) {
    ORIGINAL("Original"),
    ELECTRIC("Electric"),
    VIVID("Vivid"),
    NOIR("Noir"),
    WARMTH("Warmth"),
    CYBER("Cyber"),
    MATTE("Matte"),
    CINEMA("Cinema");

    companion object {
        fun fromName(name: String): FilterPreset =
            entries.firstOrNull { it.name == name } ?: ORIGINAL
    }
}

enum class CropAspectRatio(val label: String, val ratio: Float?) {
    FREE("Free", null),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    SIXTEEN_NINE("16:9", 16f / 9f),
    NINE_SIXTEEN("9:16", 9f / 16f),
    THREE_FOUR("3:4", 3f / 4f);

    companion object {
        fun fromName(name: String): CropAspectRatio =
            entries.firstOrNull { it.name == name } ?: FREE
    }
}

data class AppPreferences(
    val deviceLabel: String = "Local Storage",
    val darkModeEnabled: Boolean = false,
    val viewMode: ViewMode = ViewMode.GRID,
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val saveEditsAsCopy: Boolean = true,
    val highQualityExport: Boolean = true
)

data class DeviceStorageInfo(
    val totalBytes: Long = 0L,
    val availableBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val photosBytes: Long = 0L,
    val videosBytes: Long = 0L,
    val editedCount: Int = 0,
    val largeFilesCount: Int = 0
)
