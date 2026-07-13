package com.example.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
fun CreateProfileScreen(viewModel: OnboardingViewModel, navController: NavController) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val name by viewModel.profileName.collectAsState()
    val email by viewModel.profileEmail.collectAsState()
    val password by viewModel.profilePassword.collectAsState()
    val agree by viewModel.agreeTerms.collectAsState()
    val validationError by viewModel.authError.collectAsState()
    val isLoading by viewModel.authLoading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PillPalWarningBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState() as androidx.compose.foundation.ScrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        IconButton(
            onClick = {
                focusManager.clearFocus()
                keyboardController?.hide()
                navController.navigate(Routes.SIGN_IN)
            },
            modifier = Modifier
                .size(48.dp)
                .background(PillPalSurface, CircleShape)
                .border(1.dp, PillPalDivider, CircleShape)
        ) {
            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Go back", tint = PillPalPrimary)
        }

        Spacer(modifier = Modifier.height(18.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, PillPalPrimary.copy(alpha = 0.2f), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = PillPalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
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
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "PillPal Logo Icon",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Inscription",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PillPalTextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Inscrivez-vous pour lancer la liaison sans fil avec votre appareil PillPal.",
                    fontSize = 13.sp,
                    color = PillPalTextSecondary,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Full name
                Column {
                    Text("NOM COMPLET", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PillPalTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { viewModel.updateProfileName(it) },
                        placeholder = { Text("Jeanne Dupont") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PillPalPrimary, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name_field"),
                        singleLine = true,
                        maxLines = 1,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PillPalPrimary,
                            unfocusedBorderColor = PillPalDivider
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Email Address
                Column {
                    Text("ADRESSE EMAIL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PillPalTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { viewModel.updateProfileEmail(it) },
                        placeholder = { Text("nom@exemple.com") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = PillPalPrimary, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_email_field"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        maxLines = 1,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PillPalPrimary,
                            unfocusedBorderColor = PillPalDivider
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Password
                Column {
                    Text("MOT DE PASSE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PillPalTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { viewModel.updateProfilePassword(it) },
                        placeholder = { Text("Créer un mot de passe") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = PillPalPrimary, modifier = Modifier.size(20.dp)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_password_field"),
                        singleLine = true,
                        maxLines = 1,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PillPalPrimary,
                            unfocusedBorderColor = PillPalDivider
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Agree Terms Check
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateAgreeTerms(!agree) }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = agree,
                        onCheckedChange = { viewModel.updateAgreeTerms(it) },
                        colors = CheckboxDefaults.colors(checkedColor = PillPalPrimary)
                    )
                    Text(
                        text = "J'autorise le partage de mes données avec PILLPALL.",
                        fontSize = 13.sp,
                        color = PillPalTextSecondary
                    )
                }

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = validationError!!,
                        color = PillPalAlert,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // SUBMIT BUTTON — real Supabase Auth signUp(); navigation to
                // NO_DEVICE or VERIFY_EMAIL_OTP is driven by the graph-level
                // navigationEvent watcher.
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        viewModel.signUp()
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("profile_submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = PillPalPrimaryDark),
                    shape = RoundedCornerShape(28.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Démarrer la Configuration", fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Proceed link setup", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
