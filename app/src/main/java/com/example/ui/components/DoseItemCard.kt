package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.dto.DoseStatus
import com.example.ui.theme.*

@Composable
fun DoseItemCard(
    title: String,
    timeLabel: String,
    iconContent: @Composable () -> Unit,
    status: DoseStatus?,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val isDone = status == DoseStatus.DISPENSED || status == DoseStatus.ACKNOWLEDGED
    val isMissed = status == DoseStatus.MISSED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.6f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDone -> PillPalSuccessBg
                isMissed -> PillPalAlertBg
                else -> PillPalSurface
            }
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            width = if (isDone || isMissed) 2.dp else 1.dp,
            color = when {
                isDone -> PillPalPrimary
                isMissed -> PillPalAlert
                else -> PillPalDivider
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp), // Bigger padding for more spacious, responsive feel
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Larger frame container for proper icons
                Box(
                    modifier = Modifier
                        .size(56.dp) // Increased size from 48dp to 56dp
                        .background(
                            color = if (isDone) PillPalPrimary.copy(alpha = 0.15f) else PillPalWarningBg,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    iconContent()
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        fontSize = 20.sp, // Increased from 18sp to 20sp
                        fontWeight = FontWeight.Bold,
                        color = PillPalTextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = timeLabel,
                        fontSize = 15.sp, // Increased from 14sp to 15sp
                        color = PillPalTextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Redesigned bigger Status Badge (will not wrap because left side uses weight)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        when {
                            isDone -> PillPalPrimary
                            isMissed -> PillPalAlert
                            else -> PillPalWarningBg
                        }
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            when {
                                isDone -> PillPalPrimary
                                isMissed -> PillPalAlert
                                else -> PillPalDivider
                            }
                        ),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp) // Much wider and taller padding for a solid layout
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when {
                        isDone -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isSystemInDarkTheme()) NaturalDarkGreen else Color.White,
                                modifier = Modifier.size(18.dp) // Larger check icon
                            )
                            Text(
                                text = "Distribué",
                                fontSize = 14.sp, // Bigger status font size
                                color = if (isSystemInDarkTheme()) NaturalDarkGreen else Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        isMissed -> {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Manqué",
                                fontSize = 14.sp,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .size(8.dp) // Larger dot
                                    .background(PillPalTextSecondary, CircleShape)
                            )
                            Text(
                                text = "En attente",
                                fontSize = 14.sp, // Bigger status font size
                                color = PillPalTextSecondary,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
