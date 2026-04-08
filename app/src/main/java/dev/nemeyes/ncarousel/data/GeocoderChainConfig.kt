package dev.nemeyes.ncarousel.data

enum class GeocoderBackend {
    NOMINATIM,
    PLATFORM,
    PHOTON,
}

/**
 * Quale backend viene provato per primo; gli altri seguono in ordine ciclico tra quelli abilitati.
 */
enum class GeocoderOrderMode {
    NOMINATIM_FIRST,
    PLATFORM_FIRST,
    PHOTON_FIRST,
}

fun geocoderBackendsInTryOrder(
    orderMode: GeocoderOrderMode,
    nominatimEnabled: Boolean,
    platformEnabled: Boolean,
    photonEnabled: Boolean,
): List<GeocoderBackend> {
    val cycle = when (orderMode) {
        GeocoderOrderMode.NOMINATIM_FIRST ->
            listOf(GeocoderBackend.NOMINATIM, GeocoderBackend.PLATFORM, GeocoderBackend.PHOTON)
        GeocoderOrderMode.PLATFORM_FIRST ->
            listOf(GeocoderBackend.PLATFORM, GeocoderBackend.NOMINATIM, GeocoderBackend.PHOTON)
        GeocoderOrderMode.PHOTON_FIRST ->
            listOf(GeocoderBackend.PHOTON, GeocoderBackend.NOMINATIM, GeocoderBackend.PLATFORM)
    }
    return cycle.filter { b ->
        when (b) {
            GeocoderBackend.NOMINATIM -> nominatimEnabled
            GeocoderBackend.PLATFORM -> platformEnabled
            GeocoderBackend.PHOTON -> photonEnabled
        }
    }
}
