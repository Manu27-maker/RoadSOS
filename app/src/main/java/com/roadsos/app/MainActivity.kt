package com.roadsos.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.roadsos.app.ui.RoadSosApp
import com.roadsos.app.ui.theme.RoadSosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RoadSosTheme {
                RoadSosApp()
            }
        }
    }
}

fun dialEmergency(context: android.content.Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
    }
}
