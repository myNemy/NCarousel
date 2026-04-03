package dev.nemeyes.ncarousel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.work.ExistingWorkPolicy
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.nemeyes.ncarousel.ui.HomeScreen
import dev.nemeyes.ncarousel.ui.theme.NCarouselTheme
import dev.nemeyes.ncarousel.work.WallpaperWorkScheduler

/**
 * Entry point. Wallpaper changes use [android.app.WallpaperManager] as documented on
 * [developer.android.com](https://developer.android.com/reference/android/app/WallpaperManager).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WallpaperWorkScheduler.sync(this, ExistingWorkPolicy.KEEP)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val state by vm.ui.collectAsStateWithLifecycle()
            NCarouselTheme(
                darkTheme = isSystemInDarkTheme(),
                instancePrimaryHex = state.instanceThemingPrimaryHex,
                instanceOnPrimaryHex = state.instanceThemingOnPrimaryHex,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HomeScreen(vm)
                }
            }
        }
    }
}
