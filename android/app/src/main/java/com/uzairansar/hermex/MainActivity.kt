package com.uzairansar.hermex

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.uzairansar.hermex.ui.HermexApp
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    private val shortcutIntents = MutableSharedFlow<Intent>(replay = 1, extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as HermexApplication).container
        setContent {
            HermexApp(container = container, shortcutIntents = shortcutIntents)
        }
        if (savedInstanceState == null) {
            shortcutIntents.tryEmit(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shortcutIntents.tryEmit(intent)
    }
}
