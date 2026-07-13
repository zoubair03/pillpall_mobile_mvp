package com.example.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.local.DeviceWifiConnector
import com.example.navigation.Routes
import com.example.ui.theme.*
import com.example.ui.viewmodel.OnboardingViewModel

@Composable
fun ConnectWifiScreen(viewModel: OnboardingViewModel, navController: NavController) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val selectedSSID by viewModel.selectedSSID.collectAsState()
    val password by viewModel.wifiPassword.collectAsState()
    val isSending by viewModel.wifiSending.collectAsState()
    val error by viewModel.authError.collectAsState()

    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PillPalWarningBg)
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    navController.navigate(Routes.NO_DEVICE)
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(PillPalSurface, CircleShape)
                    .border(1.dp, PillPalDivider, CircleShape)
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Go back", tint = PillPalPrimary)
            }

            // Progress tracker chip
            Surface(
                color = PillPalPrimary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "ÉTAPE 2 SUR 2",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PillPalPrimaryDark,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Device WiFi link confirmation banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PillPalSuccess.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = PillPalSuccessBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(PillPalSuccess.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Device WiFi Linked Icon",
                        tint = PillPalSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "Connecté au réseau de l'appareil",
                        color = PillPalTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Appareil matériel : ${DeviceWifiConnector.DEVICE_AP_SSID}",
                        color = PillPalTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Major form body for Wifi details
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, PillPalPrimary.copy(alpha = 0.2f), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = PillPalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier.padding(26.dp)
            ) {
                Text(
                    text = "Configuration WiFi",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = PillPalTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Entrez votre réseau WiFi domestique pour que le distributeur physique puisse se connecter au cloud.",
                    fontSize = 13.sp,
                    color = PillPalTextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(26.dp))

                // Home WiFi SSID — a plain text field rather than a nearby-
                // network scan: real scanning needs ACCESS_FINE_LOCATION and
                // location services on, real permission friction for a
                // feature that isn't required here.
                Text("NOM DU RÉSEAU WIFI (SSID)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PillPalTextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = selectedSSID,
                    onValueChange = { viewModel.selectSSID(it) },
                    placeholder = { Text("Nom de votre réseau WiFi domestique") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = null,
                            tint = PillPalPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PillPalPrimary,
                        unfocusedBorderColor = PillPalDivider
                    )
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Wifi Password Input
                Text("MOT DE PASSE WIFI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PillPalTextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { viewModel.updateWifiPassword(it) },
                    placeholder = { Text("Entrer le mot de passe réseau") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = PillPalPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Done else Icons.Default.Lock,
                                contentDescription = "toggle password visibility",
                                tint = PillPalPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    singleLine = true,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PillPalPrimary,
                        unfocusedBorderColor = PillPalDivider
                    )
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = error!!,
                        color = PillPalAlert,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        viewModel.sendWifiCredentialsLocal(selectedSSID, password)
                    },
                    enabled = selectedSSID.isNotBlank() && !isSending,
                    colors = ButtonDefaults.buttonColors(containerColor = PillPalPrimaryDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("Connecter le distributeur IoT", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (isSending) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = PillPalPrimary, strokeWidth = 3.dp)
                        Text(
                            text = "Configuration des identifiants WiFi...",
                            color = PillPalPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
