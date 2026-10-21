package com.stuf.remotemouse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.stuf.remotemouse.app.RemoteMouseNavHost
import com.stuf.remotemouse.data.remote.ConnectionOrchestrator
import com.stuf.remotemouse.ui.theme.RemoteMouseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var connectionOrchestrator: ConnectionOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RemoteMouseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RemoteMouseNavHost(connectionOrchestrator)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connectionOrchestrator.setInForeground(true)
    }

    override fun onStop() {
        connectionOrchestrator.setInForeground(false)
        super.onStop()
    }
}
