package dev.nemeyes.ncarousel.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
 * Marchio NCarousel (stesso asset dell’adaptive icon), colorabile con il tema
 * ([MaterialTheme.colorScheme.primary] in superfici chiare, [onPrimary] sulla top bar).
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
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn),
    )
}
