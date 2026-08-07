package dev.nemeyes.ncarousel.data

/**
 * How the source image is mapped onto the display-sized wallpaper bitmap.
 */
enum class WallpaperCropMode {
    /** Scale to cover the screen, crop overflow (default). */
    COVER,

    /** Scale to fit inside the screen; fill empty edges with a blurred cover of the same image. */
    FIT,

    /** No upscale: center at native size (pad if smaller, crop if larger). */
    CENTER,
}
