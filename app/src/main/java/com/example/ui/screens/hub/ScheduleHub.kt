package com.example.ui.screens.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.DoseTimeFormatter
import com.example.data.remote.dto.DoseType
import com.example.ui.components.MiddaySunIcon
import com.example.ui.components.MorningSunIcon
import com.example.ui.components.NightMoonIcon
import com.example.ui.components.doseLabel
import com.example.ui.theme.*
import com.example.ui.viewmodel.ScheduleViewModel
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleHub(viewModel: ScheduleViewModel) {
    val scheduleTab by viewModel.scheduleTab.collectAsState()
    val schedules by viewModel.realSchedules.collectAsState()
    val pillsRemaining by viewModel.pillsRemaining.collectAsState()

    var editingDose by remember { mutableStateOf<DoseType?>(null) }
    var showRefillConfirmationState by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Horaires & Recharge",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = PillPalTextPrimary,
            modifier = Modifier.testTag("schedule_refill_hub_title")
        )

        // Drop Timers vs Refill Blueprint Tab Switches (Image 2 vs 3)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PillPalWarningBg, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            val tabs = listOf("Heures de Distribution", "Plan de Recharge")
            tabs.forEach { tabName ->
                val selected = scheduleTab == tabName
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.setScheduleTab(tabName) }
                        .background(if (selected) PillPalSurface else Color.Transparent)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (tabName == "Heures de Distribution") "Rappels" else "Schéma de Recharge",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) PillPalPrimary else PillPalTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (scheduleTab == "Heures de Distribution") {
            // Wheels configuration
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(DoseType.entries, key = { it.name }) { dose ->
                    val schedule = schedules.find { it.dose == dose }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSystemInDarkTheme()) PillPalSurface else Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, PillPalDivider)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            color = PillPalWarningBg,
                                            shape = RoundedCornerShape(14.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (dose) {
                                        DoseType.MORNING -> {
                                            val iconColor = if (isSystemInDarkTheme()) CustomPaletteMint else CustomPaletteEmerald
                                            MorningSunIcon(color = iconColor, modifier = Modifier.size(28.dp))
                                        }
                                        DoseType.MIDDAY -> {
                                            val iconColor = if (isSystemInDarkTheme()) Color(0xFFFFB480) else Color(0xFFEA580C)
                                            MiddaySunIcon(color = iconColor, modifier = Modifier.size(28.dp))
                                        }
                                        DoseType.NIGHT -> {
                                            val iconColor = if (isSystemInDarkTheme()) Color(0xFF90CAF9) else Color(0xFF1F7A6D)
                                            NightMoonIcon(color = iconColor, modifier = Modifier.size(28.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = doseLabel(dose),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PillPalTextPrimary
                                )
                            }

                            // Large interactive clock edit timing capsule
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingDose = dose }
                                    .border(1.dp, PillPalDivider, RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                color = PillPalWarningBg
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = schedule?.let { DoseTimeFormatter.toDisplay(it.timeOfDay) } ?: "--:--",
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PillPalPrimaryDark,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Heure de distribution (Appuyer pour modifier)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PillPalTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Cartridge Refill Blueprint Diagram - Hosted inside a Box for Floating Refill button overlay
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState() as androidx.compose.foundation.ScrollState)
                        .padding(bottom = 84.dp), // Generous padding to clear the floating action button
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PillPalSurface),
                        border = BorderStroke(1.dp, PillPalDivider),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Cartouches rotatives du distributeur",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = PillPalTextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Représentation physique des 3 roues de dosage intégrées (7 chacune) de PillPal.",
                                fontSize = 13.sp,
                                color = PillPalTextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Drawing physical interactive circle gauge slots
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(200.dp)
                            ) {
                                val gaugeBgColor = PillPalDivider
                                val gaugeActiveColor = PillPalPrimary
                                Canvas(modifier = Modifier.size(180.dp)) {
                                    drawCircle(
                                        color = gaugeBgColor,
                                        style = Stroke(width = 16.dp.toPx())
                                    )
                                    drawArc(
                                        color = gaugeActiveColor,
                                        startAngle = -90f,
                                        sweepAngle = (pillsRemaining / 21f) * 360f,
                                        useCenter = false,
                                        style = Stroke(width = 16.dp.toPx())
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$pillsRemaining",
                                        fontSize = 44.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PillPalPrimaryDark
                                    )
                                    Text(
                                        text = "sur 21 compartiment",
                                        fontSize = 12.sp,
                                        color = PillPalTextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Segmented Tracks divided into 3 physical wheels (7 slots each)
                            val morningPills = pillsRemaining.coerceAtMost(7)
                            val middayPills = (pillsRemaining - 7).coerceIn(0, 7)
                            val nightPills = (pillsRemaining - 14).coerceIn(0, 7)

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // 1. Morning Wheel Row
                                WheelBlueprintItem(
                                    title = "Matin",
                                    filledCount = morningPills,
                                    iconContent = {
                                        val iconColor = if (isSystemInDarkTheme()) CustomPaletteMint else CustomPaletteEmerald
                                        MorningSunIcon(color = iconColor, modifier = Modifier.size(24.dp))
                                    },
                                    activeColor = if (isSystemInDarkTheme()) CustomPaletteMint else CustomPaletteEmerald
                                )

                                // 2. Midday Wheel Row
                                WheelBlueprintItem(
                                    title = "Midi",
                                    filledCount = middayPills,
                                    iconContent = {
                                        val iconColor = if (isSystemInDarkTheme()) Color(0xFFFFB480) else Color(0xFFEA580C)
                                        MiddaySunIcon(color = iconColor, modifier = Modifier.size(24.dp))
                                    },
                                    activeColor = if (isSystemInDarkTheme()) Color(0xFFFFB480) else Color(0xFFEA580C)
                                )

                                // 3. Night Wheel Row
                                WheelBlueprintItem(
                                    title = "Soir",
                                    filledCount = nightPills,
                                    iconContent = {
                                        val iconColor = if (isSystemInDarkTheme()) Color(0xFF90CAF9) else Color(0xFF1F7A6D)
                                        NightMoonIcon(color = iconColor, modifier = Modifier.size(24.dp))
                                    },
                                    activeColor = if (isSystemInDarkTheme()) Color(0xFF90CAF9) else Color(0xFF1F7A6D)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Capacité restante : ${((pillsRemaining / 21f) * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PillPalTextSecondary)
                                Text("Compartiment vides : ${21 - pillsRemaining}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color(0xFFFFB480) else Color(0xFFEA580C))
                            }
                        }
                    }
                }

                // FLOATING REFILL BUTTON
                Button(
                    onClick = { showRefillConfirmationState = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .height(56.dp)
                        .testTag("refill_dispenser_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = PillPalPrimary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 10.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White)
                        Text("Recharger & Réinitialiser", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Verification Dialog for Refill
    if (showRefillConfirmationState) {
        AlertDialog(
            onDismissRequest = { showRefillConfirmationState = false },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.refillDispenser()
                        showRefillConfirmationState = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PillPalPrimary)
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRefillConfirmationState = false }) {
                    Text("Annuler", color = PillPalTextSecondary)
                }
            },
            title = {
                Text(
                    text = "Recharger les cartouches du distributeur",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PillPalTextPrimary
                )
            },
            text = {
                Text(
                    text = "Êtes-vous sûr de vouloir recharger les cartouches rotatives ? Cela réinitialisera les 3 roues intégrées à leur pleine capacité (21 compartiment de pilules actives restantes).",
                    fontSize = 15.sp,
                    color = PillPalTextSecondary
                )
            }
        )
    }

    // Real Material3 TimePicker — replaces the old hand-rolled +/- digit editor.
    val doseBeingEdited = editingDose
    if (doseBeingEdited != null) {
        val currentTime = remember(doseBeingEdited, schedules) {
            schedules.find { it.dose == doseBeingEdited }?.timeOfDay
                ?.let { raw -> runCatching { LocalTime.parse(raw) }.getOrNull() }
                ?: LocalTime.of(8, 0)
        }
        val timePickerState = rememberTimePickerState(
            initialHour = currentTime.hour,
            initialMinute = currentTime.minute,
            is24Hour = false
        )

        AlertDialog(
            onDismissRequest = { editingDose = null },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateScheduleTime(doseBeingEdited, timePickerState.hour, timePickerState.minute)
                        editingDose = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PillPalPrimary)
                ) {
                    Text("Appliquer", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingDose = null }) {
                    Text("Annuler", color = PillPalTextSecondary)
                }
            },
            title = {
                Text(
                    text = "Modifier l'heure du ${doseLabel(doseBeingEdited)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PillPalTextPrimary
                )
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }
}

@Composable
fun WheelBlueprintItem(
    title: String,
    filledCount: Int,
    iconContent: @Composable () -> Unit,
    activeColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = PillPalWarningBg
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, PillPalDivider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                iconContent()
                Text(
                    text = "$title ($filledCount/7 Remplit)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PillPalTextPrimary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (slot in 1..7) {
                    val isFilled = slot <= filledCount
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .background(
                                color = if (isFilled) activeColor else PillPalDisabled,
                                shape = RoundedCornerShape(3.dp)
                            )
                    )
                }
            }
        }
    }
}
