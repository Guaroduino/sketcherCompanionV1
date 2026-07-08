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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = theme.barBackgroundColor,
        contentColor = theme.iconColor
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(0.8f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isRegisterMode) "Crear una cuenta" else "Iniciar Sesión",
                    style = MaterialTheme.typography.headlineMedium,
                    color = theme.iconColor
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo Electrónico", color = theme.iconColor.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.iconColor,
                        unfocusedTextColor = theme.iconColor,
                        focusedLabelColor = theme.highlightColor,
                        unfocusedLabelColor = theme.iconColor.copy(alpha = 0.7f),
                        focusedBorderColor = theme.highlightColor,
                        unfocusedBorderColor = theme.iconColor.copy(alpha = 0.5f)
                    )
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña", color = theme.iconColor.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.iconColor,
                        unfocusedTextColor = theme.iconColor,
                        focusedLabelColor = theme.highlightColor,
                        unfocusedLabelColor = theme.iconColor.copy(alpha = 0.7f),
                        focusedBorderColor = theme.highlightColor,
                        unfocusedBorderColor = theme.iconColor.copy(alpha = 0.5f)
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
                    )
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
                        contentColor = theme.highlightColor
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
                        contentColor = theme.iconColor
                    ),
                    border = BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.3f))
                ) {
                    Text("Continuar con Google")
                }

                Spacer(modifier = Modifier.height(32.dp))

                TextButton(
                    onClick = onSkipLogin,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = theme.iconColor.copy(alpha = 0.8f)
                    )
                ) {
                    Text("Continuar sin cuenta (Offline)")
                }
            }
        }
    }
}

