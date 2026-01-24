package com.skecher.sketchercompanionv1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.skecher.sketchercompanionv1.ui.theme.SketcherCompanionV1Theme // Ajusta si el nombre del tema varía

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Si Android Studio le puso otro nombre a tu Theme, cámbialo aquí.
            // Por defecto suele ser NombreDelProyectoTheme
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    InkCanvas()
                }
            }
        }
    }
}