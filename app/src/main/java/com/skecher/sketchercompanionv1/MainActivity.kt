package com.skecher.sketchercompanionv1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.skecher.sketchercompanionv1.ui.theme.SketcherCompanionV1Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Asegúrate de que el tema coincida con el nombre de tu proyecto
            // Si te da error en SketcherCompanionV1Theme, usa MaterialTheme directamente
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.White
                ) {
                    // Configuración de Navegación
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "landing") {
                        
                        // PANTALLA 1: LANDING (Selección de cuadernos)
                        composable("landing") {
                            LandingScreen(
                                onOpenNotebook = {
                                    // Al hacer clic, navegamos al lienzo
                                    navController.navigate("canvas")
                                }
                            )
                        }

                        // PANTALLA 2: LIENZO (Tu InkCanvas)
                        composable("canvas") {
                            // Aquí se cargará InkCanvas.kt
                            InkCanvas()
                        }
                    }
                }
            }
        }
    }
}