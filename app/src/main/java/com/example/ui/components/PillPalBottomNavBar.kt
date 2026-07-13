package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.navigation.Routes
import com.example.ui.theme.*

@Composable
fun PillPalBottomNavBar(
    selectedRoute: String?,
    onTabSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        tonalElevation = 8.dp,
        color = if (isSystemInDarkTheme()) Color(0xFF2C312E) else Color(0xFFF3F4EE) // Natural Tones Nav BG
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Triple(Routes.TAB_HOME, Icons.Default.Home, "Accueil"),
                Triple(Routes.TAB_SCHEDULE, Icons.Default.DateRange, "Horaires"),
                Triple(Routes.TAB_HISTORY, Icons.Default.List, "Historique"),
                Triple(Routes.TAB_CONTROLS, Icons.Default.Settings, "Contrôles")
            )

            tabs.forEach { (route, icon, label) ->
                val isActive = selectedRoute == route
                val backgroundAlpha by animateFloatAsState(if (isActive) 1f else 0f, label = "tab_bg")
                val contentColor = if (isActive) PillPalPrimaryDark else PillPalTextSecondary

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onTabSelected(route) }
                        .testTag("nav_tab_${label.lowercase()}")
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .widthIn(min = 68.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(60.dp, 36.dp)
                            .background(
                                color = PillPalSecondary.copy(alpha = backgroundAlpha),
                                shape = RoundedCornerShape(18.dp)
                            )
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) PillPalPrimaryDark else PillPalTextSecondary
                    )
                }
            }
        }
    }
}
