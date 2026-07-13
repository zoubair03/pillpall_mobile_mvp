package com.example.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.OnboardingViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientProfileScreen(viewModel: OnboardingViewModel) {
    val patient by viewModel.patient.collectAsState()
    val isLoading by viewModel.authLoading.collectAsState()
    val error by viewModel.authError.collectAsState()

    // Pre-filled with the caregiver-name placeholder claimDevice() used, so
    // the caregiver can just edit it rather than retype from scratch.
    var fullName by remember(patient?.id) { mutableStateOf(patient?.fullName ?: "") }
    var ageText by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf<LocalDate?>(null) }
    var conditions by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        dateOfBirth = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annuler") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PillPalWarningBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, PillPalPrimary.copy(alpha = 0.2f), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = PillPalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(26.dp)) {
                Text(
                    text = "Profil du Patient",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PillPalTextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ces informations sont affichées sur le tableau de bord et aident au suivi du patient.",
                    fontSize = 13.sp,
                    color = PillPalTextSecondary,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column {
                    Text("NOM COMPLET DU PATIENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PillPalTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        placeholder = { Text("Jeanne Dupont") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PillPalPrimary, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.fillMaxWidth().testTag("patient_profile_name_field"),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PillPalPrimary, unfocusedBorderColor = PillPalDivider)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column {
                    Text("ÂGE (OPTIONNEL)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PillPalTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = ageText,
                        onValueChange = { ageText = it.filter { c -> c.isDigit() }.take(3) },
                        placeholder = { Text("72") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("patient_profile_age_field"),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PillPalPrimary, unfocusedBorderColor = PillPalDivider)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column {
                    Text("DATE DE NAISSANCE (OPTIONNEL)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PillPalTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = dateOfBirth?.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)) ?: "",
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("Sélectionner une date") },
                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, tint = PillPalPrimary, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                            .testTag("patient_profile_dob_field"),
                        enabled = false,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = PillPalDivider,
                            disabledTextColor = PillPalTextPrimary,
                            disabledLeadingIconColor = PillPalPrimary,
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column {
                    Text("MALADIES / CONDITIONS MÉDICALES (OPTIONNEL)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PillPalTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = conditions,
                        onValueChange = { conditions = it },
                        placeholder = { Text("Diabète, hypertension...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp)
                            .testTag("patient_profile_conditions_field"),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PillPalPrimary, unfocusedBorderColor = PillPalDivider)
                    )
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = error!!, color = PillPalAlert, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = {
                        viewModel.savePatientProfile(
                            fullName,
                            ageText.toIntOrNull(),
                            dateOfBirth?.toString(),
                            conditions,
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("patient_profile_submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = PillPalPrimaryDark),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Continuer", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
