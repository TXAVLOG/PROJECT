package gc.txa.demo.ui.library

data class LibraryMenuItem(
    val type: Type,
    val title: String,
    val iconResId: Int
) {
    enum class Type {
        SONGS,
        ALBUMS,
        ARTISTS,
        FAVORITES,
        RECENTLY_ADDED,
        MOST_PLAYED
    }
}
