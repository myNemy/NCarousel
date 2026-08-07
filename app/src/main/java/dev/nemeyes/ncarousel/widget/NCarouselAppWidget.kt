package dev.nemeyes.ncarousel.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.ExistingWorkPolicy
import dev.nemeyes.ncarousel.R
import dev.nemeyes.ncarousel.data.CarouselPreferences
import dev.nemeyes.ncarousel.data.LastAppliedWallpaperStore
import dev.nemeyes.ncarousel.data.NextWallpaperApplicator
import dev.nemeyes.ncarousel.data.WallpaperDiskCache
import dev.nemeyes.ncarousel.data.accounts.NextcloudAccountStore
import dev.nemeyes.ncarousel.work.WallpaperWorkScheduler
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NCarouselAppWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = withContext(Dispatchers.IO) { WidgetUiSnapshot.load(context) }
        provideContent {
            GlanceTheme {
                WidgetContent(state)
            }
        }
    }

    companion object {
        suspend fun updateAll(context: Context) {
            val app = context.applicationContext
            val manager = GlanceAppWidgetManager(app)
            val widget = NCarouselAppWidget()
            manager.getGlanceIds(NCarouselAppWidget::class.java).forEach { glanceId ->
                widget.update(app, glanceId)
            }
        }
    }
}

class NCarouselAppWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NCarouselAppWidget()
}

private data class WidgetUiSnapshot(
    val title: String,
    val status: String,
    val prevLabel: String,
    val pauseLabel: String,
    val nextLabel: String,
    val thumb: Bitmap?,
) {
    companion object {
        fun load(context: Context): WidgetUiSnapshot {
            val carousel = CarouselPreferences(context)
            val paused = carousel.autoWallpaperPaused
            val autoEnabled = carousel.autoWallpaperEnabled
            return WidgetUiSnapshot(
                title = context.getString(R.string.widget_title),
                status = when {
                    !autoEnabled -> context.getString(R.string.widget_status_auto_off)
                    paused -> context.getString(R.string.widget_status_paused)
                    else -> context.getString(R.string.widget_status_automatic)
                },
                prevLabel = context.getString(R.string.widget_action_previous),
                pauseLabel = context.getString(
                    if (paused) R.string.widget_action_resume else R.string.widget_action_pause,
                ),
                nextLabel = context.getString(R.string.widget_action_next),
                thumb = loadThumb(context),
            )
        }

        private fun loadThumb(context: Context): Bitmap? {
            val account = NextcloudAccountStore(context).getActiveAccount() ?: return null
            val href = LastAppliedWallpaperStore.getHref(context, account.id) ?: return null
            val carousel = CarouselPreferences(context)
            val bytes = WallpaperDiskCache(context, account.id, carousel.maxWallpaperDiskCacheMb).get(href)
                ?: return null
            return runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val maxSide = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                var sample = 1
                while (maxSide / sample > 256) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            }.getOrNull()
        }
    }
}

@Composable
private fun WidgetContent(state: WidgetUiSnapshot) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF1B1B1F))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.title,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.fillMaxWidth(),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        val thumb = state.thumb
        if (thumb != null) {
            Image(
                provider = ImageProvider(thumb),
                contentDescription = state.title,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(72.dp),
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
        }
        Text(
            text = state.status,
            style = TextStyle(
                color = ColorProvider(Color(0xFFCAC4D0)),
                fontSize = 12.sp,
            ),
            modifier = GlanceModifier.fillMaxWidth(),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetButton(
                label = state.prevLabel,
                modifier = GlanceModifier.defaultWeight(),
                action = actionRunCallback<PreviousWallpaperWidgetAction>(),
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            WidgetButton(
                label = state.pauseLabel,
                modifier = GlanceModifier.defaultWeight(),
                action = actionRunCallback<PauseWallpaperWidgetAction>(),
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            WidgetButton(
                label = state.nextLabel,
                modifier = GlanceModifier.defaultWeight(),
                action = actionRunCallback<NextWallpaperWidgetAction>(),
            )
        }
    }
}

@Composable
private fun WidgetButton(
    label: String,
    modifier: GlanceModifier,
    action: Action,
) {
    Text(
        text = label,
        style = TextStyle(
            color = ColorProvider(Color.White),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        ),
        modifier = modifier
            .background(Color(0xFF0082C9))
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .clickable(action),
    )
}

class NextWallpaperWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        withContext(Dispatchers.IO) {
            NextWallpaperApplicator.applyNext(context)
        }
        NCarouselAppWidget().update(context, glanceId)
    }
}

class PreviousWallpaperWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        withContext(Dispatchers.IO) {
            NextWallpaperApplicator.applyPrevious(context)
        }
        NCarouselAppWidget().update(context, glanceId)
    }
}

class PauseWallpaperWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        withContext(Dispatchers.IO) {
            val carousel = CarouselPreferences(context)
            if (carousel.autoWallpaperEnabled) {
                carousel.autoWallpaperPaused = !carousel.autoWallpaperPaused
                WallpaperWorkScheduler.sync(context, ExistingWorkPolicy.REPLACE)
            }
        }
        NCarouselAppWidget().update(context, glanceId)
    }
}
