package com.example.ui.screens.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.navigation.Routes
import com.example.ui.theme.*
import com.example.ui.viewmodel.OnboardingViewModel

@Composable
fun NoDeviceScreen(viewModel: OnboardingViewModel, navController: NavController) {
    val isConnecting by viewModel.deviceConnecting.collectAsState()
    val error by viewModel.authError.collectAsState()

    // NEARBY_WIFI_DEVICES (API 33+) is a runtime permission, not just a
    // manifest declaration — required to bring up a WifiNetworkSpecifier
    // connection. Requested lazily, right when the caregiver actually
    // wants to connect, rather than earlier in onboarding.
    val context = LocalContext.current
    val nearbyWifiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.connectToDevice() }

    fun requestConnection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            nearbyWifiPermissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            viewModel.connectToDevice()
        }
    }

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
                onClick = { navController.navigate(Routes.CREATE_PROFILE) },
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
                    text = "ÉTAPE 1 SUR 2",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PillPalPrimaryDark,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, PillPalPrimary.copy(alpha = 0.2f), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = PillPalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large WiFi symbol circle with pulse styling
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(PillPalPrimary.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .background(PillPalPrimary.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .background(PillPalPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "WiFi Discovery Radar",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                Text(
                    text = "Trouver Votre Appareil PillPal",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = PillPalTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Allumez votre distributeur physique PillPal — il crée son propre réseau Wi-Fi le temps de la configuration. Appuyez ci-dessous pour vous y connecter directement.",
                    fontSize = 14.sp,
                    color = PillPalTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                if (isConnecting) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = PillPalPrimary, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                        Text(
                            text = "Connexion à l'appareil PillPal...",
                            fontSize = 13.sp,
                            color = PillPalPrimaryDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Button(
                        onClick = { requestConnection() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PillPalPrimaryDark),
                        shape = RoundedCornerShape(27.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Text("Se Connecter à Mon Appareil", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error!!,
                        color = PillPalAlert,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
