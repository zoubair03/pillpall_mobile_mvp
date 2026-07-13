package com.example.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.DoseTimeFormatter
import com.example.data.remote.dto.DoseType
import com.example.ui.components.doseLabel
import com.example.ui.theme.*
import com.example.ui.viewmodel.OnboardingViewModel
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSetupScreen(viewModel: OnboardingViewModel) {
    val schedules by viewModel.realSchedules.collectAsState()
    val isLoading by viewModel.authLoading.collectAsState()
    val error by viewModel.authError.collectAsState()
    var editingDose by remember { mutableStateOf<DoseType?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        Text(
            text = "Configurer les Horaires",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = PillPalTextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Réglez les heures de distribution du matin, midi et soir. Vous pourrez les modifier plus tard.",
            fontSize = 13.sp,
            color = PillPalTextSecondary,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(DoseType.entries, key = { it.name }) { dose ->
                val schedule = schedules.find { it.dose == dose }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PillPalSurface),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, PillPalDivider)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(doseLabel(dose), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PillPalTextPrimary)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingDose = dose }
                                .border(1.dp, PillPalDivider, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            color = PillPalWarningBg
                        ) {
                            Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = schedule?.let { DoseTimeFormatter.toDisplay(it.timeOfDay) } ?: "--:--",
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PillPalPrimaryDark
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Appuyer pour modifier", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PillPalTextSecondary)
                            }
                        }
                    }
                }
            }
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = error!!, color = PillPalAlert, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.completeOnboarding() },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("schedule_setup_finish_button"),
            colors = ButtonDefaults.buttonColors(containerColor = PillPalPrimaryDark),
            shape = RoundedCornerShape(28.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Terminer", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

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
