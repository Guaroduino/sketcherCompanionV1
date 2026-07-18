package com.sketcher.sketchercompanionv1.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.sketcher.sketchercompanionv1.R
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color

@Composable
fun LoginScreen(
    theme: UiThemeConfig,
    onLoginSuccess: () -> Unit,
    onSkipLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }
    
    val context = LocalContext.current

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            onLoginSuccess()
        }
    }

    val backgrounds = listOf(R.drawable.fondo, R.drawable.fondo1, R.drawable.fondo2)
    var currentBackgroundIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            currentBackgroundIndex = (currentBackgroundIndex + 1) % backgrounds.size
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = theme.barBackgroundColor,
        contentColor = theme.iconColor
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Crossfade(
                targetState = backgrounds[currentBackgroundIndex],
                animationSpec = tween(durationMillis = 1500),
                label = "background_crossfade"
            ) { bgRes ->
                Image(
                    painter = painterResource(id = bgRes),
                    contentDescription = "Background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.barBackgroundColor.copy(alpha = 0.85f))
            )

            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(0.8f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Logo",
                    modifier = Modifier.size(120.dp)
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Sketcher",
                        fontWeight = FontWeight.Light,
                        style = MaterialTheme.typography.headlineSmall,
                        color = theme.iconColor
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .height(28.dp)
                            .width(1.dp)
                            .background(theme.iconColor.copy(alpha = 0.5f))
                    )
                    Text(
                        text = "Companion",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall,
                        color = theme.iconColor
                    )
                }

                Text(
                    text = if (isRegisterMode) "Crear una cuenta" else "Iniciar Sesión",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.iconColor.copy(alpha = 0.8f)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo Electrónico") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = theme.highlightColor,
                        unfocusedLabelColor = Color.Gray,
                        focusedBorderColor = theme.highlightColor,
                        unfocusedBorderColor = Color.LightGray
                    )
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedLabelColor = theme.highlightColor,
                        unfocusedLabelColor = Color.Gray,
                        focusedBorderColor = theme.highlightColor,
                        unfocusedBorderColor = Color.LightGray
                    )
                )

                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                }

                Button(
                    onClick = {
                        if (isRegisterMode) {
                            viewModel.register(email, password)
                        } else {
                            viewModel.login(email, password)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.buttonColor,
                        contentColor = theme.iconColor,
                        disabledContainerColor = theme.buttonColor.copy(alpha = 0.5f),
                        disabledContentColor = theme.iconColor.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.3f))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = theme.iconColor)
                    } else {
                        Text(if (isRegisterMode) "Registrarse" else "Entrar", color = theme.iconColor)
                    }
                }

                TextButton(
                    onClick = { 
                        isRegisterMode = !isRegisterMode 
                        viewModel.clearError()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text(if (isRegisterMode) "¿Ya tienes cuenta? Inicia sesión" else "¿No tienes cuenta? Regístrate")
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = theme.iconColor.copy(alpha = 0.15f))
                
                OutlinedButton(
                    onClick = { viewModel.signInWithGoogle(context) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Text("Continuar con Google")
                }

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedButton(
                    onClick = onSkipLogin,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Text("Continuar sin cuenta (Offline)")
                }
            }
            
            Text(
                text = "Desarrollado por: Luis F. Corado. Guaroduino-2026",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }
}

