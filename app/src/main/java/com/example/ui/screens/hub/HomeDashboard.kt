package com.example.ui.screens.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.dto.DoseType
import com.example.ui.components.DoseItemCard
import com.example.ui.components.MiddaySunIcon
import com.example.ui.components.MorningSunIcon
import com.example.ui.components.NightMoonIcon
import com.example.ui.theme.*
import com.example.ui.viewmodel.HomeViewModel
import androidx.compose.runtime.collectAsState
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeDashboard(viewModel: HomeViewModel) {
    val countdown by viewModel.countdownTimer.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val isSelectedDayToday by viewModel.isSelectedDayToday.collectAsState()
    val weekDays by viewModel.weekDays.collectAsState()
    val todayLabel by viewModel.todayLabel.collectAsState()
    val patient by viewModel.patient.collectAsState()
    val doseCards by viewModel.doseCards.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Welcoming Card Info
        item {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = "Bonjour, ${patient?.fullName?.trim()?.substringBefore(" ")?.ifBlank { null } ?: "—"}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = PillPalTextPrimary,
                    modifier = Modifier.testTag("home_user_greeting")
                )
                Text(
                    text = todayLabel,
                    fontSize = 16.sp,
                    color = PillPalTextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Calendar Date Row Selector
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PillPalWarningBg, // Clay light olive-grey background
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    weekDays.forEach { day ->
                        val isDayActive = selectedDay == day.date
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.selectCalendarDay(day.date) }
                                .background(if (isDayActive) PillPalSecondary else Color.Transparent)
                                .border(
                                    width = if (isDayActive) 1.dp else 0.dp,
                                    color = if (isDayActive) PillPalPrimary else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(vertical = 12.dp, horizontal = 16.dp)
                        ) {
                            Text(
                                text = day.label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDayActive) PillPalPrimaryDark else PillPalTextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = day.dayOfMonth.toString(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDayActive) PillPalPrimaryDark else PillPalTextPrimary
                            )
                        }
                    }
                }
            }
        }

        // MONOSPACED NEXT DOSE COUNTDOWN CHIP (Natural Tones Revamp)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = PillPalSuccessBg), // Soft green-sage background
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, PillPalSecondary) // Light sage border
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Prochaine Dose",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = PillPalPrimaryDark,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Distributeur automatique",
                        fontSize = 15.sp,
                        color = PillPalTextSecondary,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Large digital monospaced real-time ticker
                    Text(
                        text = countdown,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        color = PillPalPrimary,
                        modifier = Modifier.testTag("next_dose_countdown_timer")
                    )
                }
            }
        }

        // Non-today selections show that day's history instead of today's
        // actionable list — this label is the only cue besides the disabled
        // card styling, so it needs to say which day is being viewed.
        if (!isSelectedDayToday) {
            item {
                Text(
                    text = "Historique du ${selectedDay.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH))}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PillPalTextSecondary,
                )
            }
        }

        // TACTILE DOSE CARDS (Morning, Midday, Night) — status derived from
        // real dose_events; tapping sends ack_dose rather than toggling
        // local state, and only today's cards are actionable (see
        // isSelectedDayToday) — other days are read-only history.
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                doseCards.forEach { card ->
                    val iconContent: @Composable () -> Unit = when (card.dose) {
                        DoseType.MORNING -> {
                            {
                                val iconColor = if (isSystemInDarkTheme()) CustomPaletteMint else CustomPaletteEmerald
                                MorningSunIcon(color = iconColor, modifier = Modifier.size(32.dp))
                            }
                        }
                        DoseType.MIDDAY -> {
                            {
                                val iconColor = if (isSystemInDarkTheme()) Color(0xFFFFB480) else Color(0xFFEA580C)
                                MiddaySunIcon(color = iconColor, modifier = Modifier.size(32.dp))
                            }
                        }
                        DoseType.NIGHT -> {
                            {
                                val iconColor = if (isSystemInDarkTheme()) Color(0xFF90CAF9) else Color(0xFF1F7A6D)
                                NightMoonIcon(color = iconColor, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                    DoseItemCard(
                        title = card.label,
                        timeLabel = card.timeLabel,
                        iconContent = iconContent,
                        status = card.status,
                        enabled = isSelectedDayToday,
                        onClick = { viewModel.acknowledgeDose(card.dose) }
                    )
                }
            }
        }
    }
}
