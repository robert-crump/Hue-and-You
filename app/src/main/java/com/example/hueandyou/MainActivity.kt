package com.example.hueandyou

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.hueandyou.ui.navigation.HueAndYouNavHost
import com.example.hueandyou.ui.theme.HueAndYouTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HueAndYouTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HueAndYouNavHost()
                }
            }
        }
    }
}
