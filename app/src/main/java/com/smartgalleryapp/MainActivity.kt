package com.smartgalleryapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.smartgalleryapp.ui.SmartGalleryAppRoot
import com.smartgalleryapp.ui.theme.SmartGalleryTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<SmartGalleryViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SmartGalleryTheme {
                SmartGalleryAppRoot(
                    viewModel = viewModel,
                    activity = this,
                )
            }
        }
    }
}
