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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.navigation.Routes
import com.example.ui.theme.*
import com.example.ui.viewmodel.OnboardingViewModel

@Composable
fun PairedSuccessScreen(viewModel: OnboardingViewModel, navController: NavController) {
    val deviceUid by viewModel.claimedDeviceUid.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PillPalWarningBg)
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, PillPalSuccess.copy(alpha = 0.3f), RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = PillPalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Large elegant green success tick emblem block
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(PillPalSuccessBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(PillPalSuccess.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(PillPalSuccess, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Device Paired Icon Indicator",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "Appairé avec succès !",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PillPalTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Votre distributeur physique PillPal a été entièrement enregistré, synchronisé sur votre WiFi domestique sécurisé et appairé avec succès à votre profil cloud.",
                    fontSize = 14.sp,
                    color = PillPalTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(26.dp))

                // Hardware information specs block
                Surface(
                    color = PillPalSuccessBg.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Statut de l'appareil IoT :", fontSize = 12.sp, color = PillPalTextSecondary, fontWeight = FontWeight.Bold)
                            Text("EN LIGNE / SÉCURISÉ", fontSize = 12.sp, color = PillPalSuccess, fontWeight = FontWeight.ExtraBold)
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Identifiant assigné :", fontSize = 12.sp, color = PillPalTextSecondary, fontWeight = FontWeight.Bold)
                            Text(deviceUid ?: "—", fontSize = 12.sp, color = PillPalTextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                Button(
                    onClick = { navController.navigate(Routes.PATIENT_PROFILE) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("success_dashboard_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = PillPalPrimaryDark),
                    shape = RoundedCornerShape(28.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Continuer", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Navigate to dashboard",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
