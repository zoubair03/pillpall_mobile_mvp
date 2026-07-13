package com.example.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.navigation.Routes
import com.example.ui.theme.*
import com.example.ui.viewmodel.OnboardingViewModel

@Composable
fun CaregiverSignInScreen(viewModel: OnboardingViewModel, navController: NavController) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val email by viewModel.signInEmail.collectAsState()
    val password by viewModel.signInPassword.collectAsState()
    val loginError by viewModel.authError.collectAsState()
    val isLoading by viewModel.authLoading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PillPalWarningBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState() as androidx.compose.foundation.ScrollState)
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Space to compensate for removed demo button on top
        Spacer(modifier = Modifier.height(16.dp))

        // Center card with beautiful high-contrast borders and brand identity
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, PillPalPrimary.copy(alpha = 0.25f), RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = PillPalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Interactive Pill/Aesthetic Emblem
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(PillPalPrimary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(PillPalPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "PillPal Logo Icon",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Bienvenue sur PillPal",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PillPalTextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Connectez-vous pour gérer les horaires et surveiller le distributeur IoT physique.",
                    fontSize = 14.sp,
                    color = PillPalTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(26.dp))

                // Email Input field with Lead Icon (single line static size)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "ADRESSE EMAIL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PillPalTextPrimary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { viewModel.updateSignInEmail(it) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = PillPalPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signin_email_field"),
                        placeholder = { Text("aidant@pillpal.com") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        maxLines = 1,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PillPalPrimary,
                            unfocusedBorderColor = PillPalDivider
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Password Input field with Lead Icon (single line static size)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "MOT DE PASSE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PillPalTextPrimary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { viewModel.updateSignInPassword(it) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = PillPalPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signin_password_field"),
                        placeholder = { Text("••••••••") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        maxLines = 1,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PillPalPrimary,
                            unfocusedBorderColor = PillPalDivider
                        )
                    )
                }

                if (loginError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = loginError!!,
                        color = PillPalAlert,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // SIGN IN BUTTON — real Supabase Auth, navigation to the hub (or
                // onboarding) is driven by the graph-level navigationEvent watcher.
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        viewModel.signIn()
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("signin_submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = PillPalPrimaryDark),
                    shape = RoundedCornerShape(28.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Se Connecter", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Arrow right signin", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Elegant HIGH-CONTRAST OR Separator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = PillPalDivider.copy(alpha = 0.5f))
                    Text(
                        text = "  OU  ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PillPalTextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = PillPalDivider.copy(alpha = 0.5f))
                }

                Spacer(modifier = Modifier.height(18.dp))

                // HIGH-CONTRAST REGISTER OUTLINED BUTTON
                OutlinedButton(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        navController.navigate(Routes.CREATE_PROFILE)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("signin_register_redirect_button"),
                    border = BorderStroke(1.5.dp, PillPalPrimary),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PillPalPrimary)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Register Mode Icon",
                            tint = PillPalPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "S'enregistrer / Créer un Profil",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PillPalPrimary
                        )
                    }
                }
            }
        }
    }
}
