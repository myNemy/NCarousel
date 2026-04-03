package dev.nemeyes.ncarousel.data

/**
 * Ordering behaviour aligned with the KDE [Nextcloud-Carousel](https://forgejo.it/Nemeyes/Nextcloud-Carousel) plugin.
 */
enum class OrderMode {
    /** Lexicographic order, cycles the list. */
    SEQUENTIAL,

    /** Uniform random on every pick. */
    RANDOM,

    /** Shuffle once when the library fingerprint changes, then walk that order. */
    SHUFFLE_ONCE,

    /** Random, avoiding the last few picks when possible. */
    SMART_RANDOM,

    /** Permutation of the library consumed without repeat until all shown, then reshuffle. */
    NO_REPEAT_SHUFFLE,
}
