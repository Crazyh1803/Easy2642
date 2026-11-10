package com.easy2642.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.easy2642.app.ui.AppRoot
import com.easy2642.app.ui.Easy2642Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep PII out of screenshots and the recents thumbnail. Debug builds stay
        // capturable so store screenshots can be taken with synthetic data.
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        setContent {
            Easy2642Theme { AppRoot(application as Easy2642App) }
        }
    }
}
