package com.example.ui.screens.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.remote.dto.DoseType
import com.example.navigation.Routes
import com.example.ui.components.MiddaySunIcon
import com.example.ui.components.MorningSunIcon
import com.example.ui.components.NightMoonIcon
import com.example.ui.components.doseLabel
import com.example.ui.theme.*
import com.example.ui.viewmodel.ControlsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ControlsView(viewModel: ControlsViewModel, navController: NavController) {
    val online by viewModel.isDeviceOnline.collectAsState()
    val powerStatus by viewModel.devicePowerStatus.collectAsState()
    val batteryLevel by viewModel.deviceBatteryLevel.collectAsState()
    val patient by viewModel.patient.collectAsState()
    val loggedOut by viewModel.loggedOut.collectAsState()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var activeDispenseProgressMessage by remember { mutableStateOf<String?>(null) }
    var showProfileEditDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(loggedOut) {
        if (loggedOut) {
            navController.navigate(Routes.SIGN_IN) {
                popUpTo(0)
            }
        }
    }

    if (showProfileEditDialog) {
        var editName by remember { mutableStateOf(patient?.fullName ?: "") }

        AlertDialog(
            onDismissRequest = {
                focusManager.clearFocus()
                keyboardController?.hide()
                showProfileEditDialog = false
            },
            confirmButton = {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        viewModel.renamePatient(editName)
                        showProfileEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PillPalPrimary)
                ) {
                    Text("Enregistrer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    showProfileEditDialog = false
                }) {
                    Text("Annuler", color = PillPalTextSecondary)
                }
            },
            title = {
                Text("Modifier les informations", fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Nom") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column {
            Text(
                text = "Centre de Contrôle",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = PillPalTextPrimary,
                modifier = Modifier.testTag("hardware_override_title")
            )
            Text(
                text = "Interface de contrôle à distance pour votre appareil PillPal.",
                fontSize = 14.sp,
                color = PillPalTextSecondary,
                fontWeight = FontWeight.Medium
            )
        }

        // Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PillPalSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, PillPalDivider)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(PillPalPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = patient?.fullName?.ifBlank { null } ?: "—",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = PillPalTextPrimary
                        )
                        Text(
                            text = "Patient suivi",
                            fontSize = 13.sp,
                            color = PillPalTextSecondary
                        )
                    }
                }
                IconButton(onClick = { showProfileEditDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Modifier le profil",
                        tint = PillPalTextSecondary
                    )
                }
            }
        }

        // Live Diagnostics telemetry styled Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PillPalWarningBg),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, PillPalDivider)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(PillPalPrimary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = PillPalPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Appareil",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PillPalTextPrimary
                    )
                }

                HorizontalDivider(color = PillPalDivider.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Wireless card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = PillPalSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, PillPalDivider)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("LIAISON SANS FIL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PillPalTextSecondary)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(if (online) PillPalSuccess else PillPalAlert, CircleShape)
                                )
                                Text(
                                    text = if (online) "En ligne" else "Hors ligne",
                                    fontWeight = FontWeight.Bold,
                                    color = if (online) PillPalSuccess else PillPalAlert,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // Battery card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = PillPalSurface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, PillPalDivider)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("BATTERIE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PillPalTextSecondary)
                            Text(
                                text = "$batteryLevel%",
                                fontWeight = FontWeight.Bold,
                                color = if (batteryLevel in 1..20) PillPalAlert else PillPalTextPrimary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Text(
                    text = powerStatus,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = PillPalTextSecondary
                )
            }
        }

        // Overrides Warning Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = PillPalWarningText)
            Text("Distributions Manuelles", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PillPalTextPrimary)
        }

        // Active drop spinner simulation
        if (activeDispenseProgressMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = PillPalWarningBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PillPalWarningText.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = PillPalPrimary, strokeWidth = 3.dp)
                    Text(activeDispenseProgressMessage!!, fontWeight = FontWeight.Bold, color = PillPalWarningText, fontSize = 13.sp)
                }
            }
        }

        // Three high contrast remote force drop buttons, beautifully themed matching Sun & Moon styles
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val dispenseTriggers = listOf(
                Pair(DoseType.MORNING, if (isSystemInDarkTheme()) CustomPaletteMint else CustomPaletteEmerald),
                Pair(DoseType.MIDDAY, if (isSystemInDarkTheme()) Color(0xFFFFB480) else Color(0xFFEA580C)),
                Pair(DoseType.NIGHT, if (isSystemInDarkTheme()) Color(0xFF90CAF9) else Color(0xFF1F7A6D))
            )

            dispenseTriggers.forEach { (dose, color) ->
                val trLabel = when (dose) {
                    DoseType.MORNING -> "Distribuer Roue du Matin"
                    DoseType.MIDDAY -> "Distribuer Roue du Midi"
                    DoseType.NIGHT -> "Distribuer Roue du Soir"
                }
                Button(
                    onClick = {
                        scope.launch {
                            activeDispenseProgressMessage = "Signal IoT envoyé. Actionnement du moteur rotatif pour le ${doseLabel(dose).lowercase(Locale.FRENCH)}..."
                            delay(1800)
                            viewModel.forceDispense(dose)
                            activeDispenseProgressMessage = null
                        }
                    },
                    enabled = online && activeDispenseProgressMessage == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = color.copy(alpha = 0.08f),
                        contentColor = color,
                        disabledContainerColor = PillPalDisabled,
                        disabledContentColor = PillPalTextSecondary
                    ),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.5.dp, if (online) color.copy(alpha = 0.35f) else PillPalDivider)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            when (dose) {
                                DoseType.MORNING -> MorningSunIcon(color = color, modifier = Modifier.size(24.dp))
                                DoseType.MIDDAY -> MiddaySunIcon(color = color, modifier = Modifier.size(24.dp))
                                DoseType.NIGHT -> NightMoonIcon(color = color, modifier = Modifier.size(24.dp))
                            }
                            Text(trLabel, color = PillPalTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Maintenance rows section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PillPalSurface),
            border = BorderStroke(1.dp, PillPalDivider),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(6.dp)
            ) {
                // Calibrate Row
                ListItem(
                    headlineContent = { Text("Calibrer & Centrer Carrousels", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                    supportingContent = { Text("Réinitialise les rotors rotatifs internes à 0°", fontSize = 12.sp, color = PillPalTextSecondary) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(PillPalPrimary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Build, contentDescription = null, tint = PillPalPrimary, modifier = Modifier.size(18.dp))
                        }
                    },
                    trailingContent = { Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        viewModel.runCalibration()
                    }
                )

                HorizontalDivider(color = PillPalDivider.copy(alpha = 0.5f))

                // Reboot Row
                ListItem(
                    headlineContent = { Text("Redémarrer l'Appareil Physique", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                    supportingContent = { Text("Effectue une réinitialisation complète de la carte WiFi", fontSize = 12.sp, color = PillPalTextSecondary) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(PillPalPrimary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = PillPalPrimary, modifier = Modifier.size(18.dp))
                        }
                    },
                    trailingContent = { Icon(imageVector = Icons.Default.KeyboardArrowRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        viewModel.runRestart()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Large high-contrast logout button session block
        Button(
            onClick = { viewModel.logout() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .height(56.dp)
                .testTag("controls_logout_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFEE2E2), // Material red tint
                contentColor = Color(0xFF991B1B)
            ),
            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "Logout Icon",
                    tint = Color(0xFF991B1B),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Déconnecter la Session Aidant",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
