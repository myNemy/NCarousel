package dev.nemeyes.ncarousel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.nemeyes.ncarousel.ui.HomeScreen
import dev.nemeyes.ncarousel.work.WallpaperWorkScheduler

/**
 * Entry point. Wallpaper changes use [android.app.WallpaperManager] as documented on
 * [developer.android.com](https://developer.android.com/reference/android/app/WallpaperManager).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WallpaperWorkScheduler.sync(this)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val vm: MainViewModel = viewModel()
                    HomeScreen(vm)
                }
            }
        }
    }
}
