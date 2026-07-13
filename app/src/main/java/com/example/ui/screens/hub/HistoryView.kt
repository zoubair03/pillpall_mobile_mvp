package com.example.ui.screens.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.dto.DoseStatus
import com.example.ui.components.doseLabel
import com.example.ui.theme.*
import com.example.ui.viewmodel.HistoryViewModel
import java.util.Locale

private enum class HistoryTone { SUCCESS, ALERT, NEUTRAL }

private data class HistoryRow(
    val id: String,
    val occurredAt: String,
    val title: String,
    val description: String,
    val tone: HistoryTone,
)

// dose_events is the source of truth for missed doses too (status = missed);
// alerts.kind == "missed_dose" would just be the same real event shown
// twice, so it's excluded here rather than merged.
private fun alertTitle(kind: String): String = when (kind) {
    "low_battery" -> "Batterie Faible"
    "motor_fault" -> "Anomalie Moteur"
    "offline" -> "Distributeur Hors Ligne"
    else -> kind.replace('_', ' ').replaceFirstChar { c -> c.titlecase(Locale.FRENCH) }
}

private fun doseRowTitleAndTone(status: DoseStatus): Pair<String, HistoryTone> = when (status) {
    DoseStatus.DISPENSED -> "Dose Distribuée" to HistoryTone.SUCCESS
    DoseStatus.ACKNOWLEDGED -> "Dose Confirmée" to HistoryTone.SUCCESS
    DoseStatus.MISSED -> "Dose Manquée" to HistoryTone.ALERT
    DoseStatus.PENDING -> "Dose en Attente" to HistoryTone.NEUTRAL
}

// "08:02" / "Aujourd'hui" style split, formatted in the device's local zone
// (display-only — not the medically-relevant zone used for dose logic).
private fun formatHistoryTimestamp(iso: String): Pair<String, String> {
    val instant = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return "--:--" to ""
    val zoned = instant.atZone(java.time.ZoneId.systemDefault())
    val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
    val time = zoned.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    val dateLabel = when (zoned.toLocalDate()) {
        today -> "Aujourd'hui"
        today.minusDays(1) -> "Hier"
        else -> zoned.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
    }
    return time to dateLabel
}

@Composable
fun HistoryView(viewModel: HistoryViewModel) {
    val filter by viewModel.historyFilter.collectAsState()
    val doseEvents by viewModel.doseEvents.collectAsState()
    val alerts by viewModel.alerts.collectAsState()

    val rows = remember(filter, doseEvents, alerts) {
        val doseRows = if (filter != "Statut Matériel") {
            doseEvents.map { event ->
                val (title, tone) = doseRowTitleAndTone(event.status)
                HistoryRow(event.id, event.occurredAt, title, doseLabel(event.dose), tone)
            }
        } else emptyList()

        val alertRows = if (filter != "Doses") {
            alerts.filter { it.kind != "missed_dose" }.map { alert ->
                HistoryRow(alert.id, alert.createdAt, alertTitle(alert.kind), alert.message ?: "—", HistoryTone.ALERT)
            }
        } else emptyList()

        (doseRows + alertRows).sortedByDescending { it.occurredAt }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Historique",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = PillPalTextPrimary,
            modifier = Modifier.testTag("activity_history_title")
        )

        // Horizontal Filters Chips Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filters = listOf("Tout", "Doses", "Statut Matériel")
            filters.forEach { filterName ->
                val active = filter == filterName
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { viewModel.setHistoryFilter(filterName) }
                        .background(if (active) PillPalPrimary else PillPalWarningBg)
                        .border(
                            1.dp,
                            if (active) PillPalPrimary else PillPalDivider,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = filterName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) Color.White else PillPalTextSecondary
                    )
                }
            }
        }

        // Vertical logs scrolling list
        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = PillPalDisabled, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Aucun événement trouvé dans les archives.", fontSize = 16.sp, color = PillPalTextSecondary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(rows, key = { it.id }) { row ->
                    val (time, dateLabel) = formatHistoryTimestamp(row.occurredAt)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PillPalSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, PillPalDivider)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left alert color stripe
                            val leftBarColor = when (row.tone) {
                                HistoryTone.ALERT -> PillPalAlert
                                HistoryTone.SUCCESS -> PillPalSuccess
                                HistoryTone.NEUTRAL -> PillPalAccent
                            }

                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(90.dp)
                                    .background(leftBarColor)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Circular badge icon wrapper
                                    val iconBg = when (row.tone) {
                                        HistoryTone.ALERT -> PillPalAlertBg
                                        HistoryTone.SUCCESS -> PillPalSuccessBg
                                        HistoryTone.NEUTRAL -> PillPalWarningBg
                                    }
                                    val iconTint = when (row.tone) {
                                        HistoryTone.ALERT -> PillPalAlert
                                        HistoryTone.SUCCESS -> PillPalSuccess
                                        HistoryTone.NEUTRAL -> PillPalPrimary
                                    }
                                    val icon = when (row.tone) {
                                        HistoryTone.ALERT -> Icons.Default.Warning
                                        HistoryTone.SUCCESS -> Icons.Default.CheckCircle
                                        HistoryTone.NEUTRAL -> Icons.Default.Info
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(iconBg, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = iconTint,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.widthIn(max = 180.dp)) {
                                        Text(
                                            text = row.title,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PillPalTextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = row.description,
                                            fontSize = 13.sp,
                                            color = PillPalTextSecondary,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                                // Date description labels
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = time,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PillPalTextPrimary
                                    )
                                    Text(
                                        text = dateLabel,
                                        fontSize = 11.sp,
                                        color = PillPalTextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
