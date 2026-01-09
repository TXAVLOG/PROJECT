package com.txapp.musicplayer.auto

/**
 * Android Auto Media ID Helper
 * Giúp tạo và phân tích Media ID cho browsing trên xe hơi
 */
object AutoMediaIDHelper {
    
    // Media IDs dùng cho browseable items
    const val MEDIA_ID_EMPTY_ROOT = "__EMPTY_ROOT__"
    const val MEDIA_ID_ROOT = "__ROOT__"
    const val MEDIA_ID_RECENT = "__RECENT__"
    
    // Categories
    const val MEDIA_ID_MUSICS_BY_SONGS = "__BY_SONGS__"
    const val MEDIA_ID_MUSICS_BY_ALBUM = "__BY_ALBUM__"
    const val MEDIA_ID_MUSICS_BY_ARTIST = "__BY_ARTIST__"
    const val MEDIA_ID_MUSICS_BY_PLAYLIST = "__BY_PLAYLIST__"
    const val MEDIA_ID_MUSICS_BY_HISTORY = "__BY_HISTORY__"
    const val MEDIA_ID_MUSICS_BY_TOP_TRACKS = "__BY_TOP_TRACKS__"
    const val MEDIA_ID_MUSICS_BY_FAVORITES = "__BY_FAVORITES__"
    const val MEDIA_ID_MUSICS_BY_SHUFFLE = "__BY_SHUFFLE__"
    const val MEDIA_ID_MUSICS_BY_QUEUE = "__BY_QUEUE__"
    
    private const val CATEGORY_SEPARATOR = "__/__"
    private const val LEAF_SEPARATOR = "__|__"
    
    /**
     * Tạo Media ID từ category và item ID
     * Format: category__/__subcategory__|__itemId
     */
    fun createMediaID(musicID: String?, vararg categories: String): String {
        val sb = StringBuilder()
        for (i in categories.indices) {
            require(isValidCategory(categories[i])) { "Invalid category: ${categories[i]}" }
            sb.append(categories[i])
            if (i < categories.size - 1) {
                sb.append(CATEGORY_SEPARATOR)
            }
        }
        if (musicID != null) {
            sb.append(LEAF_SEPARATOR).append(musicID)
        }
        return sb.toString()
    }
    
    /**
     * Lấy category từ Media ID
     */
    fun extractCategory(mediaID: String): String {
        val pos = mediaID.indexOf(LEAF_SEPARATOR)
        return if (pos >= 0) mediaID.substring(0, pos) else mediaID
    }
    
    /**
     * Lấy music ID từ Media ID
     */
    fun extractMusicID(mediaID: String): String? {
        val pos = mediaID.indexOf(LEAF_SEPARATOR)
        return if (pos >= 0) mediaID.substring(pos + LEAF_SEPARATOR.length) else null
    }
    
    /**
     * Kiểm tra xem Media ID có thể browse được không (là folder)
     */
    fun isBrowseable(mediaID: String): Boolean = !mediaID.contains(LEAF_SEPARATOR)
    
    private fun isValidCategory(category: String?): Boolean {
        return category == null || 
               (!category.contains(CATEGORY_SEPARATOR) && !category.contains(LEAF_SEPARATOR))
    }
}
