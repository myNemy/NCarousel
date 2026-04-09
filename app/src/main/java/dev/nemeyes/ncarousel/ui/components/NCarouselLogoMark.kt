package dev.nemeyes.ncarousel.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nemeyes.ncarousel.R

/**
 * Marchio NCarousel (stesso PNG di [R.drawable.ic_launcher_logo] usato nell’adaptive icon).
 *
 * Il master è in `branding/NCarousel_alpha.svg` (Inkscape: PNG RGBA embedded, non path vettoriali).
 * Rigenera `res/drawable-nodpi/ic_launcher_logo.png` con glifo **bianco** (maschera da master SVG);
 * sull’adaptive icon lo sfondo è [R.color.ic_launcher_background] (= [nc_primary]). In UI il [tint] applica primary/onPrimary OCS.
 */
@Composable
fun NCarouselLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_logo),
        contentDescription = null,
        modifier = modifier.size(size),
        // The PNG has generous transparent padding; Crop makes the mark more present in-app.
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center,
        colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn),
    )
}
