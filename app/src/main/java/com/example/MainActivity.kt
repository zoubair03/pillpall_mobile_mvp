package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.HistoryLog
import com.example.data.ScheduleItem
import com.example.ui.PillPalViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                PillPalApp()
            }
        }
    }
}

@Composable
fun PillPalApp(viewModel: PillPalViewModel = viewModel()) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isOnline by viewModel.isDeviceOnline.collectAsState()

    // Scaffolding for entire layout
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        topBar = {
            if (currentScreen == PillPalViewModel.Screen.MAIN_HUB) {
                PillPalTopStatusBar(
                    isSyncing = isSyncing,
                    isOnline = isOnline,
                    batteryLevel = viewModel.deviceBatteryLevel.collectAsState().value,
                    onSyncClick = { viewModel.triggerSync() }
                )
            }
        },
        bottomBar = {
            if (currentScreen == PillPalViewModel.Screen.MAIN_HUB) {
                val currentTab by viewModel.currentTab.collectAsState()
                PillPalBottomNavBar(
                    selectedTab = currentTab,
                    onTabSelected = { viewModel.selectTab(it) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) {
                        AnimatedContentTransitionScope.SlideDirection.Left
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.Right
                    }
                    slideIntoContainer(
                        towards = direction,
                        animationSpec = tween(450, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(300)) togetherWith slideOutOfContainer(
                        towards = direction,
                        animationSpec = tween(450, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(250))
                },
                label = "ScreenTransitions"
            ) { screen ->
                when (screen) {
                    PillPalViewModel.Screen.SIGN_IN -> CaregiverSignInScreen(viewModel)
                    PillPalViewModel.Screen.CREATE_PROFILE -> CreateProfileScreen(viewModel)
                    PillPalViewModel.Screen.ENTER_CODE -> EnterCodeScreen(viewModel)
                    PillPalViewModel.Screen.NO_DEVICE -> NoDeviceScreen(viewModel)
                    PillPalViewModel.Screen.CONNECT_WIFI -> ConnectWifiScreen(viewModel)
                    PillPalViewModel.Screen.PAIRED_SUCCESS -> PairedSuccessScreen(viewModel)
                    PillPalViewModel.Screen.MAIN_HUB -> MainHubScreen(viewModel)
                }
            }
        }
    }
}

// ==========================================
// 1. TOP HEADER STATUS BAR (IoT Connectivity)
// ==========================================
@Composable
fun WifiIcon(isConnected: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.85f // Dot center coordinates
        val strokeWidth = 2.dp.toPx()
        
        // Bottom signal dot
        drawCircle(
            color = color,
            radius = 2.5.dp.toPx(),
            center = Offset(cx, cy)
        )
        
        if (isConnected) {
            // Three concentric wave rings
            for (i in 1..3) {
                val r = (i * 4.5).dp.toPx()
                drawArc(
                    color = color,
                    startAngle = -135f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }
        } else {
            // Offline wave outline (faint) with strike-through slash line
            for (i in 1..3) {
                val r = (i * 4.5).dp.toPx()
                drawArc(
                    color = color.copy(alpha = 0.35f),
                    startAngle = -135f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }
            // Crisp strike-through slash lines
            drawLine(
                color = color,
                start = Offset(cx - 7.dp.toPx(), cy - 13.dp.toPx()),
                end = Offset(cx + 7.dp.toPx(), cy + 2.dp.toPx()),
                strokeWidth = 2.5.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
fun BatteryIcon(batteryLevel: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        val rectW = w * 0.82f
        val rectH = h * 0.55f
        
        val rx = (w - rectW) / 2f
        val ry = (h - rectH) / 2f
        
        val strokeWidth = 1.5.dp.toPx()
        val cornerRadius = 2.2.dp.toPx()
        
        // Outer case frame drawing
        drawRoundRect(
            color = color,
            topLeft = Offset(rx, ry),
            size = androidx.compose.ui.geometry.Size(rectW - 2.5.dp.toPx(), rectH),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
        )
        
        // Terminal connection nib drawing
        val nibW = 2.2.dp.toPx()
        val nibH = rectH * 0.4f
        drawRoundRect(
            color = color,
            topLeft = Offset(rx + rectW - 2.5.dp.toPx(), ry + (rectH - nibH) / 2f),
            size = androidx.compose.ui.geometry.Size(nibW, nibH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.8.dp.toPx(), 0.8.dp.toPx())
        )
        
        // Fill gauge level
        val levelPercent = batteryLevel.coerceIn(0, 100) / 100f
        val padding = 2.dp.toPx()
        val maxFillW = rectW - 2.5.dp.toPx() - padding * 2
        val fillW = maxFillW * levelPercent
        val fillH = rectH - padding * 2
        
        if (fillW > 0.5.dp.toPx()) {
            val fillCol = if (batteryLevel <= 20) {
                Color(0xFFBA1A1A) // Alert / Emergency low level colors
            } else if (batteryLevel <= 45) {
                Color(0xFFEA580C) // Warning level colors 
            } else {
                color // standard normal state colors
            }
            drawRoundRect(
                color = fillCol,
                topLeft = Offset(rx + padding, ry + padding),
                size = androidx.compose.ui.geometry.Size(fillW, fillH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.8.dp.toPx(), 0.8.dp.toPx())
            )
        }
    }
}

@Composable
fun PillPalTopStatusBar(
    isSyncing: Boolean,
    isOnline: Boolean,
    batteryLevel: Int,
    onSyncClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PillPal Logo Name
            Text(
                text = "PillPal",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PillPalPrimary,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.testTag("app_logo_title")
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // WiFi Status Chip
                Row(
                    modifier = Modifier
                        .background(
                            if (isOnline) CustomPaletteMint.copy(alpha = 0.22f) else PillPalAlertBg.copy(alpha = 0.22f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val wifiColor = if (isOnline) PillPalPrimary else PillPalAlert
                    WifiIcon(
                        isConnected = isOnline,
                        color = wifiColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isOnline) "WiFi" else "Offline",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOnline) PillPalPrimary else PillPalAlertText
                    )
                }

                // Battery Status Chip
                Row(
                    modifier = Modifier
                        .background(PillPalWarningBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BatteryIcon(
                        batteryLevel = batteryLevel,
                        color = PillPalPrimary,
                        modifier = Modifier.size(24.dp, 16.dp)
                    )
                    Text(
                        text = "$batteryLevel%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PillPalTextSecondary
                    )
                }

                // Rotating Refresh/Sync Sync button
                val infiniteTransition = rememberInfiniteTransition(label = "rotation")
                val rotationAngle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "sync_rotation"
                )

                IconButton(
                    onClick = onSyncClick,
                    modifier = Modifier
                        .testTag("sync_hardware_button")
                        .size(36.dp)
                        .background(
                            if (isSyncing) PillPalPrimary.copy(alpha = 0.1f) else Color.Transparent,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync device databases",
                        tint = PillPalPrimary,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(if (isSyncing) rotationAngle else 0f)
                    )
                }
            }
        }
    }
}

// ==========================================
// 2. BOTTOM NAVIGATION BAR
// ==========================================
@Composable
fun PillPalBottomNavBar(
    selectedTab: PillPalViewModel.Tab,
    onTabSelected: (PillPalViewModel.Tab) -> Unit
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
                Triple(PillPalViewModel.Tab.HOME, Icons.Default.Home, "Home"),
                Triple(PillPalViewModel.Tab.SCHEDULE, Icons.Default.DateRange, "Schedule"),
                Triple(PillPalViewModel.Tab.HISTORY, Icons.Default.List, "History"),
                Triple(PillPalViewModel.Tab.CONTROLS, Icons.Default.Settings, "Controls")
            )

            tabs.forEach { (tab, icon, label) ->
                val isActive = selectedTab == tab
                val backgroundAlpha by animateFloatAsState(if (isActive) 1f else 0f, label = "tab_bg")
                val contentColor = if (isActive) PillPalPrimaryDark else PillPalTextSecondary

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onTabSelected(tab) }
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

// ==========================================
// SCREEN 1: CAREGIVER SIGN IN (Image 7)
// ==========================================
@Composable
fun CaregiverSignInScreen(viewModel: PillPalViewModel) {
    var email by remember { mutableStateOf("caregiver@pillpal.com") }
    var password by remember { mutableStateOf("••••••••") }
    var loginError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PillPalWarningBg)
            .verticalScroll(rememberScrollState() as androidx.compose.foundation.ScrollState)
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Space to compensate for removed demo button on top
        Spacer(modifier = Modifier.height(16.dp))

        // Center card with beautiful high-contrast borders and brand identity
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, PillPalPrimary.copy(alpha = 0.25f), RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = PillPalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Interactive Pill/Aesthetic Emblem
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(PillPalPrimary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(PillPalPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "PillPal Logo Icon",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Welcome to PillPal",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PillPalTextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sign in to manage schedules & monitor the IoT physical dispenser.",
                    fontSize = 14.sp,
                    color = PillPalTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(26.dp))

                // Email Input field with Lead Icon (single line static size)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "EMAIL ADDRESS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PillPalTextPrimary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { 
                            email = it
                            loginError = null
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = PillPalPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signin_email_field"),
                        placeholder = { Text("caregiver@pillpal.com") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        maxLines = 1,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PillPalPrimary,
                            unfocusedBorderColor = PillPalDivider
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Password Input field with Lead Icon (single line static size)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "PASSWORD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PillPalTextPrimary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            password = it
                            loginError = null
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = PillPalPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signin_password_field"),
                        placeholder = { Text("••••••••") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        maxLines = 1,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PillPalPrimary,
                            unfocusedBorderColor = PillPalDivider
                        )
                    )
                }

                if (loginError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = loginError!!,
                        color = PillPalAlert,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // SIGN IN BUTTON (Accesses Dashboard/MAIN_HUB directly)
                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            loginError = "Please enter both Email and Password"
                        } else {
                            viewModel.navigateTo(PillPalViewModel.Screen.MAIN_HUB)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("signin_submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = PillPalPrimaryDark),
                    shape = RoundedCornerShape(28.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Log In Now", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Arrow right signin", modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Elegant HIGH-CONTRAST OR Separator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Divider(modifier = Modifier.weight(1f), color = PillPalDivider.copy(alpha = 0.5f))
                    Text(
                        text = "  OR  ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PillPalTextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Divider(modifier = Modifier.weight(1f), color = PillPalDivider.copy(alpha = 0.5f))
                }

                Spacer(modifier = Modifier.height(18.dp))

                // HIGH-CONTRAST REGISTER OUTLINED BUTTON
                OutlinedButton(
                    onClick = { viewModel.navigateTo(PillPalViewModel.Screen.CREATE_PROFILE) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("signin_register_redirect_button"),
                    border = BorderStroke(1.5.dp, PillPalPrimary),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PillPalPrimary)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Register Mode Icon",
                            tint = PillPalPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Register / Create Profile",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PillPalPrimary
                        )
                    }
                }
            }
        }
    }
}

// Using standard built-in PasswordVisualTransformation

// ==========================================
// SCREEN 2: CREATE CAREGIVER PROFILE (Image 8)
// ==========================================
@Composable
fun CreateProfileScreen(viewModel: PillPalViewModel) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var agree by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PillPalWarningBg)
            .verticalScroll(rememberScrollState() as androidx.compose.foundation.ScrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        IconButton(
            onClick = { viewModel.navigateTo(PillPalViewModel.Screen.SIGN_IN) },
            modifier = Modifier
                .size(48.dp)
                .background(PillPalSurface, CircleShape)
                .border(1.dp, PillPalDivider, CircleShape)
        ) {
            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Go back", tint = PillPalPrimary)
        }

        Spacer(modifier = Modifier.height(18.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, PillPalPrimary.copy(alpha = 0.2f), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = PillPalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier.padding(26.dp)
            ) {
                Text(
                    text = "Caregiver Registration",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PillPalTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Fill in your profile details below to initiate wireless linking with your PillPal hardware.",
                    fontSize = 13.sp,
                    color = PillPalTextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Full name
                Column {
                    Text("FULL NAME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PillPalTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("e.g. Jane Doe") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PillPalPrimary, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name_field"),
                        singleLine = true,
                        maxLines = 1,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PillPalPrimary,
                            unfocusedBorderColor = PillPalDivider
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Row for Age and DOB
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Age
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PillPalTextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = age,
                            onValueChange = { age = it },
                            placeholder = { Text("e.g. 45") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PillPalPrimary,
                                unfocusedBorderColor = PillPalDivider
                            )
                        )
                    }

                    // DOB (Interactive Date Selector)
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text("DATE OF BIRTH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PillPalTextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDatePicker = true }
                        ) {
                            OutlinedTextField(
                                value = dob.ifBlank { "Select Date" },
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                placeholder = { Text("YYYY-MM-DD") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Show Date Picker calendar dialog icon trigger",
                                        tint = PillPalPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = if (dob.isNotBlank()) PillPalTextPrimary else PillPalTextSecondary,
                                    disabledBorderColor = PillPalDivider,
                                    disabledPlaceholderColor = PillPalTextSecondary,
                                    disabledTrailingIconColor = PillPalPrimary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Email Address
                Column {
                    Text("EMAIL ADDRESS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PillPalTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text("name@example.com") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = PillPalPrimary, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_email_field"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        maxLines = 1,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PillPalPrimary,
                            unfocusedBorderColor = PillPalDivider
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Password
                Column {
                    Text("PASSWORD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PillPalTextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Create password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = PillPalPrimary, modifier = Modifier.size(20.dp)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_password_field"),
                        singleLine = true,
                        maxLines = 1,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PillPalPrimary,
                            unfocusedBorderColor = PillPalDivider
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Agree Terms Check
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { agree = !agree }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = agree,
                        onCheckedChange = { agree = it },
                        colors = CheckboxDefaults.colors(checkedColor = PillPalPrimary)
                    )
                    Text(
                        text = "I approve sharing telehealth records with PillPal Dispenser.",
                        fontSize = 13.sp,
                        color = PillPalTextSecondary
                    )
                }

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = validationError!!,
                        color = PillPalAlert,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // SUBMIT BUTTON -> GOES TO DEVICE SETUP (NO_DEVICE SCREEN)
                Button(
                    onClick = {
                        if (name.isBlank() || email.isBlank() || password.isBlank() || age.isBlank() || dob.isBlank()) {
                            validationError = "Please fill in all requested fields"
                        } else if (!agree) {
                            validationError = "You must approve the telehealth sync authorization"
                        } else {
                            viewModel.navigateTo(PillPalViewModel.Screen.NO_DEVICE)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("profile_submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = PillPalPrimaryDark),
                    shape = RoundedCornerShape(28.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Register & Begin IoT Setup", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Proceed link setup", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // PillPal Premium Native-Style Custom Interactive Date Selector Modal
        if (showDatePicker) {
            var selectedYear by remember { mutableStateOf(1980) }
            var selectedMonth by remember { mutableStateOf(6) } // June
            var selectedDay by remember { mutableStateOf(15) }

            AlertDialog(
                onDismissRequest = { showDatePicker = false },
                title = {
                    Text(
                        text = "Select Date of Birth",
                        fontWeight = FontWeight.Bold,
                        color = PillPalTextPrimary,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Please choose your date of birth. This will be linked to your PillPal caregiver credentials.",
                            fontSize = 12.sp,
                            color = PillPalTextSecondary,
                            lineHeight = 16.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Month selector card
                            Box(modifier = Modifier.weight(1.2f)) {
                                var monthExpanded by remember { mutableStateOf(false) }
                                Card(
                                    onClick = { monthExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = PillPalSuccessBg.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("MONTH", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PillPalPrimary)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[selectedMonth - 1],
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = PillPalTextPrimary
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = monthExpanded,
                                    onDismissRequest = { monthExpanded = false },
                                    modifier = Modifier.background(PillPalSurface)
                                ) {
                                    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec").forEachIndexed { index, name ->
                                        DropdownMenuItem(
                                            text = { Text(name, fontWeight = FontWeight.Bold, color = PillPalTextPrimary) },
                                            onClick = {
                                                selectedMonth = index + 1
                                                monthExpanded = false
                                                val maxDays = when (selectedMonth) {
                                                    2 -> if (selectedYear % 4 == 0) 29 else 28
                                                    4, 6, 9, 11 -> 30
                                                    else -> 31
                                                }
                                                if (selectedDay > maxDays) {
                                                    selectedDay = maxDays
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // Day Selector Card
                            Box(modifier = Modifier.weight(1f)) {
                                var dayExpanded by remember { mutableStateOf(false) }
                                Card(
                                    onClick = { dayExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = PillPalSuccessBg.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("DAY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PillPalPrimary)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = selectedDay.toString(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = PillPalTextPrimary
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = dayExpanded,
                                    onDismissRequest = { dayExpanded = false },
                                    modifier = Modifier.background(PillPalSurface)
                                ) {
                                    val maxDays = when (selectedMonth) {
                                        2 -> if (selectedYear % 4 == 0) 29 else 28
                                        4, 6, 9, 11 -> 30
                                        else -> 31
                                    }
                                    (1..maxDays).forEach { d ->
                                        DropdownMenuItem(
                                            text = { Text(d.toString(), fontWeight = FontWeight.Bold, color = PillPalTextPrimary) },
                                            onClick = {
                                                selectedDay = d
                                                dayExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Year Selector Card
                            Box(modifier = Modifier.weight(1.3f)) {
                                var yearExpanded by remember { mutableStateOf(false) }
                                Card(
                                    onClick = { yearExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = PillPalSuccessBg.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("YEAR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PillPalPrimary)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = selectedYear.toString(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = PillPalTextPrimary
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = yearExpanded,
                                    onDismissRequest = { yearExpanded = false },
                                    modifier = Modifier.background(PillPalSurface)
                                ) {
                                    (2026 downTo 1920).forEach { y ->
                                        DropdownMenuItem(
                                            text = { Text(y.toString(), fontWeight = FontWeight.Bold, color = PillPalTextPrimary) },
                                            onClick = {
                                                selectedYear = y
                                                yearExpanded = false
                                                if (selectedMonth == 2 && selectedDay > 28) {
                                                    val maxDays = if (selectedYear % 4 == 0) 29 else 28
                                                    if (selectedDay > maxDays) {
                                                        selectedDay = maxDays
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val formattedMonth = String.format("%02d", selectedMonth)
                            val formattedDay = String.format("%02d", selectedDay)
                            dob = "$selectedYear-$formattedMonth-$formattedDay"
                            showDatePicker = false
                        }
                    ) {
                        Text("Confirm", fontWeight = FontWeight.Bold, color = PillPalPrimaryDark)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancel", color = PillPalTextSecondary)
                    }
                }
            )
        }
    }
}

// ==========================================
// SCREEN 3: ENTER VERIFICATION CODE (Image 9)
// ==========================================
@Composable
fun EnterCodeScreen(viewModel: PillPalViewModel) {
    val code by viewModel.verificationCode.collectAsState()
    val timer by viewModel.verificationResendTimer.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { viewModel.navigateTo(PillPalViewModel.Screen.CREATE_PROFILE) }) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Go back", tint = PillPalPrimary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Center Box
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PillPalDivider, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Email confirmation graphic
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(PillPalWarningBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = PillPalPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Enter Verification Code",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = PillPalTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "We sent a secure 6-digit code to your email address.",
                    fontSize = 15.sp,
                    color = PillPalTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(30.dp))

                // Six code grid boxes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0..5) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.85f)
                                .border(
                                    width = if (code[i].isNotBlank()) 2.dp else 1.dp,
                                    color = if (code[i].isNotBlank()) PillPalPrimary else PillPalDivider,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .background(PillPalSurface)
                                .clickable {
                                    // Clicking box sets a demo digit
                                    viewModel.updateCodeDigit(i, (1..9).random().toString())
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = code[i],
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = PillPalPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = { viewModel.navigateTo(PillPalViewModel.Screen.NO_DEVICE) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PillPalSecondary),
                    shape = RoundedCornerShape(27.dp)
                ) {
                    Text("Verify & Proceed", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Resend Ticker
                Text(
                    text = if (timer > 0) "Didn't receive code? Resend in 0:${String.format("%02d", timer)}"
                           else "Didn't receive code? Resend Code Now",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (timer > 0) PillPalTextSecondary else PillPalPrimary,
                    modifier = Modifier.clickable(enabled = timer == 0) {
                        viewModel.resendVerificationCode()
                    }
                )
            }
        }
    }
}

// ==========================================
// SCREEN 4: BLUETOOTH PAIRING SETUP (Image 10)
// ==========================================
@Composable
fun NoDeviceScreen(viewModel: PillPalViewModel) {
    val isScanning by viewModel.bluetoothScanning.collectAsState()
    val scannedDevices by viewModel.scannedDevices.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PillPalWarningBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo(PillPalViewModel.Screen.CREATE_PROFILE) },
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
                    text = "STEP 1 OF 2",
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
                // Large bluetooth symbol circle with pulse styling
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
                                contentDescription = "BLE Discovery Radar",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                Text(
                    text = "Find Your PillPal Device",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = PillPalTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Turn on your PillPal physical dispenser so we can detect and register the IoT device via Bluetooth (BLE).",
                    fontSize = 14.sp,
                    color = PillPalTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                if (!isScanning && scannedDevices.isEmpty()) {
                    Button(
                        onClick = { viewModel.startBluetoothScanning() },
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
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Text("Scan for Device (BLE)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (isScanning) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = PillPalPrimary, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                        Text(
                            text = "Scanning nearby frequency bands...", 
                            fontSize = 13.sp, 
                            color = PillPalPrimaryDark, 
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Text(
                        text = "CHOOSE DETECTED HARDWARE:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PillPalTextSecondary,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = 10.dp)
                    )

                    scannedDevices.forEach { device ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clickable { viewModel.navigateTo(PillPalViewModel.Screen.CONNECT_WIFI) }
                                .border(1.dp, PillPalPrimary.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = PillPalSuccessBg.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle, 
                                        contentDescription = "Device found indicator", 
                                        tint = PillPalPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = device, 
                                        fontWeight = FontWeight.Bold, 
                                        fontSize = 15.sp,
                                        color = PillPalTextPrimary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight, 
                                    contentDescription = "Arrow connect",
                                    tint = PillPalPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 5: WIFI PROVISIONING (Image 11)
// ==========================================
@Composable
fun ConnectWifiScreen(viewModel: PillPalViewModel) {
    val selectedSSID by viewModel.selectedSSID.collectAsState()
    val password by viewModel.wifiPassword.collectAsState()
    val isSending by viewModel.wifiSending.collectAsState()

    var showDropdown by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PillPalWarningBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo(PillPalViewModel.Screen.NO_DEVICE) },
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
                    text = "STEP 2 OF 2",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PillPalPrimaryDark,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Bluetooth BLE link confirmation banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PillPalSuccess.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = PillPalSuccessBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(PillPalSuccess.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check, 
                        contentDescription = "Bluetooth Linked Icon", 
                        tint = PillPalSuccess, 
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "Bluetooth Linked Successfully",
                        color = PillPalTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Hardware Device: PillPal-SN8824",
                        color = PillPalTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Major form body for Wifi details
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, PillPalPrimary.copy(alpha = 0.2f), RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = PillPalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier.padding(26.dp)
            ) {
                Text(
                    text = "WiFi Configuration",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = PillPalTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Select your home WiFi network so the physical dispenser can connect to the cloud.",
                    fontSize = 13.sp,
                    color = PillPalTextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(26.dp))

                // Select WiFi Network Dropdown
                Text("CHOOSE WIFI NETWORK (SSID)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PillPalTextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val boxWidth = maxWidth
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDropdown = true }
                    ) {
                        OutlinedTextField(
                            value = selectedSSID.ifBlank { "Choose a network..." },
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Menu, 
                                    contentDescription = null, 
                                    tint = PillPalPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight, 
                                    contentDescription = "SSID list dropdown",
                                    tint = PillPalPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = if (selectedSSID.isNotBlank()) PillPalTextPrimary else PillPalTextSecondary,
                                disabledBorderColor = PillPalDivider,
                                disabledLeadingIconColor = PillPalPrimary,
                                disabledTrailingIconColor = PillPalPrimary,
                                disabledPlaceholderColor = PillPalTextSecondary
                            )
                        )

                        DropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false },
                            modifier = Modifier
                                .width(boxWidth)
                                .background(PillPalSurface)
                                .border(1.5.dp, PillPalPrimary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        ) {
                            listOf("HomeWifi_5G", "SweetHome_Router", "PillPal_Dev_Net", "CareGiver_Hotspot").forEach { ssid ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Menu, 
                                                contentDescription = null, 
                                                tint = PillPalPrimary, 
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(ssid, fontWeight = FontWeight.Bold, color = PillPalTextPrimary, fontSize = 14.sp)
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectSSID(ssid)
                                        showDropdown = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Wifi Password Input
                Text("WIFI PASSWORD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PillPalTextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { viewModel.updateWifiPassword(it) },
                    placeholder = { Text("Enter network password") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock, 
                            contentDescription = null, 
                            tint = PillPalPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Done else Icons.Default.Lock,
                                contentDescription = "toggle password visibility",
                                tint = PillPalPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    singleLine = true,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PillPalPrimary,
                        unfocusedBorderColor = PillPalDivider
                    )
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = { viewModel.sendWifiCredentials() },
                    enabled = selectedSSID.isNotBlank() && !isSending,
                    colors = ButtonDefaults.buttonColors(containerColor = PillPalPrimaryDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("Connect IoT Dispenser", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (isSending) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = PillPalPrimary, strokeWidth = 3.dp)
                        Text(
                            text = "Provisioning WiFi board credentials...",
                            color = PillPalPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 5.5: SUCCESS PAIRED TICK (Screen with tick and paired successfully)
// ==========================================
@Composable
fun PairedSuccessScreen(viewModel: PillPalViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PillPalWarningBg)
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
                    text = "Paired Successfully!",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PillPalTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Your physical PillPal dispenser has been fully registered, synced over your secure home WiFi, and successfully paired with your cloud profile.",
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
                            Text("IoT Device Status:", fontSize = 12.sp, color = PillPalTextSecondary, fontWeight = FontWeight.Bold)
                            Text("ONLINE / SECURED", fontSize = 12.sp, color = PillPalSuccess, fontWeight = FontWeight.ExtraBold)
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Assigned ID:", fontSize = 12.sp, color = PillPalTextSecondary, fontWeight = FontWeight.Bold)
                            Text("PillPal-SN8824", fontSize = 12.sp, color = PillPalTextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                Button(
                    onClick = { viewModel.navigateTo(PillPalViewModel.Screen.MAIN_HUB) },
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
                        Text("Access Dashboard", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

// ==========================================
// CORE HUB CONTAINER
// ==========================================
@Composable
fun MainHubScreen(viewModel: PillPalViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()

    AnimatedContent(
        targetState = currentTab,
        transitionSpec = {
            val direction = if (targetState.ordinal > initialState.ordinal) {
                AnimatedContentTransitionScope.SlideDirection.Left
            } else {
                AnimatedContentTransitionScope.SlideDirection.Right
            }
            slideIntoContainer(
                towards = direction,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(250)) togetherWith slideOutOfContainer(
                towards = direction,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(200))
        },
        label = "TabTransitions"
    ) { tab ->
        when (tab) {
            PillPalViewModel.Tab.HOME -> HomeDashboard(viewModel)
            PillPalViewModel.Tab.SCHEDULE -> ScheduleHub(viewModel)
            PillPalViewModel.Tab.HISTORY -> HistoryView(viewModel)
            PillPalViewModel.Tab.CONTROLS -> ControlsView(viewModel)
        }
    }
}

// ==========================================
// MAIN WEB TAB 1: USER HOME (Image 1)
// ==========================================
@Composable
fun HomeDashboard(viewModel: PillPalViewModel) {
    val countdown by viewModel.countdownTimer.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()

    // Dose states
    val morningDispensed by viewModel.morningDoseDispensed.collectAsState()
    val middayDispensed by viewModel.middayDoseDispensed.collectAsState()
    val nightDispensed by viewModel.nightDoseDispensed.collectAsState()

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
                    text = "Hello, Ahmed",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = PillPalTextPrimary,
                    modifier = Modifier.testTag("home_user_greeting")
                )
                Text(
                    text = "Wednesday, October 25",
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
                    val days = listOf(
                        Pair("M", 23),
                        Pair("T", 24),
                        Pair("W", 25),
                        Pair("T", 26),
                        Pair("F", 27)
                    )

                    days.forEach { (dayName, dayNum) ->
                        val isDayActive = selectedDay == dayNum
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.selectCalendarDay(dayNum) }
                                .background(if (isDayActive) PillPalSecondary else Color.Transparent)
                                .border(
                                    width = if (isDayActive) 1.dp else 0.dp,
                                    color = if (isDayActive) PillPalPrimary else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(vertical = 12.dp, horizontal = 16.dp)
                        ) {
                            Text(
                                text = dayName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDayActive) PillPalPrimaryDark else PillPalTextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = dayNum.toString(),
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
                        text = "Next Dose Approaching",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = PillPalPrimaryDark,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "2 Pills • Dispenser Automated",
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

        // TACTILE DOSE CARDS (Morning, Midday, Night)
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp) // Bigger spacing between cards
            ) {
                // Morning Card
                DoseItemCard(
                    title = "Morning",
                    timeLabel = "08:00 AM",
                    iconContent = {
                        val iconColor = if (isSystemInDarkTheme()) CustomPaletteMint else CustomPaletteEmerald
                        MorningSunIcon(color = iconColor, modifier = Modifier.size(32.dp))
                    },
                    isDispensed = morningDispensed,
                    onClickToggled = { viewModel.toggleMorningDose() }
                )

                // Midday Card
                DoseItemCard(
                    title = "Midday",
                    timeLabel = "01:30 PM",
                    iconContent = {
                        val iconColor = if (isSystemInDarkTheme()) Color(0xFFFFB480) else Color(0xFFEA580C)
                        MiddaySunIcon(color = iconColor, modifier = Modifier.size(32.dp))
                    },
                    isDispensed = middayDispensed,
                    onClickToggled = { viewModel.toggleMiddayDose() }
                )

                // Night Card
                DoseItemCard(
                    title = "Night",
                    timeLabel = "08:00 PM",
                    iconContent = {
                        val iconColor = if (isSystemInDarkTheme()) Color(0xFF90CAF9) else Color(0xFF1F7A6D)
                        NightMoonIcon(color = iconColor, modifier = Modifier.size(32.dp))
                    },
                    isDispensed = nightDispensed,
                    onClickToggled = { viewModel.toggleNightDose() }
                )
            }
        }
    }
}

@Composable
fun MorningSunIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 4.5f
        val cx = size.width / 2f
        val cy = size.height / 2f + r * 0.4f
        
        // Draw Sun circle
        drawCircle(
            color = color,
            radius = r,
            center = Offset(cx, cy - r * 0.6f)
        )
        
        // Draw top rays
        val rayLength = r * 0.9f
        val strokeWidth = 2.5.dp.toPx()
        val angles = listOf(-150f, -120f, -90f, -60f, -30f)
        for (angleDeg in angles) {
            val angle = angleDeg * (Math.PI / 180f)
            val startX = cx + (r * 1.3f) * Math.cos(angle).toFloat()
            val startY = (cy - r * 0.6f) + (r * 1.3f) * Math.sin(angle).toFloat()
            val endX = cx + (r * 1.3f + rayLength) * Math.cos(angle).toFloat()
            val endY = (cy - r * 0.6f) + (r * 1.3f + rayLength) * Math.sin(angle).toFloat()
            
            drawLine(
                color = color,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
        
        // Horizontal horizon/ground line
        drawLine(
            color = color,
            start = Offset(cx - r * 2.2f, cy),
            end = Offset(cx + r * 2.2f, cy),
            strokeWidth = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
fun MiddaySunIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 3.2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        
        // Draw Bright Center Sun
        drawCircle(
            color = color,
            radius = r,
            center = Offset(cx, cy)
        )
        
        // Draw rays all around
        val rayLength = r * 0.6f
        val strokeWidth = 2.5.dp.toPx()
        for (i in 0 until 8) {
            val angle = (i * 45) * (Math.PI / 180f)
            val startX = cx + (r * 1.3f) * Math.cos(angle).toFloat()
            val startY = cy + (r * 1.3f) * Math.sin(angle).toFloat()
            val endX = cx + (r * 1.3f + rayLength) * Math.cos(angle).toFloat()
            val endY = cy + (r * 1.3f + rayLength) * Math.sin(angle).toFloat()
            
            drawLine(
                color = color,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
fun NightMoonIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2.5f
        val cx = size.width / 2f
        val cy = size.height / 2f
        
        // Draw crescent moon path
        val path = androidx.compose.ui.graphics.Path().apply {
            addArc(
                oval = androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 180f
            )
            quadraticTo(
                x1 = cx + r * 0.15f,
                y1 = cy,
                x2 = cx,
                y2 = cy - r
            )
            close()
        }
        
        drawPath(
            path = path,
            color = color
        )
        
        // Mini star
        drawCircle(
            color = color,
            radius = 2.5.dp.toPx(),
            center = Offset(cx - r * 0.5f, cy - r * 0.5f)
        )
    }
}

@Composable
fun DoseItemCard(
    title: String,
    timeLabel: String,
    iconContent: @Composable () -> Unit,
    isDispensed: Boolean,
    onClickToggled: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isDispensed) PillPalSuccessBg else PillPalSurface
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(
            width = if (isDispensed) 2.dp else 1.dp,
            color = if (isDispensed) PillPalPrimary else PillPalDivider
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
                            color = if (isDispensed) PillPalPrimary.copy(alpha = 0.15f) else PillPalWarningBg,
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
                    .background(if (isDispensed) PillPalPrimary else PillPalWarningBg)
                    .border(
                        BorderStroke(
                            1.dp,
                            if (isDispensed) PillPalPrimary else PillPalDivider
                        ),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp) // Much wider and taller padding for a solid layout
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isDispensed) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (isSystemInDarkTheme()) NaturalDarkGreen else Color.White,
                            modifier = Modifier.size(18.dp) // Larger check icon
                        )
                        Text(
                            text = "Dispensed",
                            fontSize = 14.sp, // Bigger status font size
                            color = if (isSystemInDarkTheme()) NaturalDarkGreen else Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp) // Larger dot
                                .background(PillPalTextSecondary, CircleShape)
                        )
                        Text(
                            text = "Pending",
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

// ==========================================
// TAB 2: SCHEDULE & REFILL HUB (Image 2 & 3)
// ==========================================
@Composable
fun ScheduleHub(viewModel: PillPalViewModel) {
    val scheduleTab by viewModel.scheduleTab.collectAsState()
    val schedulesList by viewModel.schedules.collectAsState()
    val pillsRemaining by viewModel.pillsRemaining.collectAsState()

    var showTimeEditDialogForId by remember { mutableStateOf<String?>(null) }
    var showRefillConfirmationState by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Schedule & Refill Hub",
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
            val tabs = listOf("Drop Timers", "Refill Blueprint")
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
                        text = tabName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) PillPalPrimary else PillPalTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (scheduleTab == "Drop Timers") {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Wheels configuration
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 84.dp) // Generous bottom padding to prevent button override
                ) {
                    items(schedulesList) { schedule ->
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
                                        when (schedule.id) {
                                            "morning" -> {
                                                val iconColor = if (isSystemInDarkTheme()) CustomPaletteMint else CustomPaletteEmerald
                                                MorningSunIcon(color = iconColor, modifier = Modifier.size(28.dp))
                                            }
                                            "midday" -> {
                                                val iconColor = if (isSystemInDarkTheme()) Color(0xFFFFB480) else Color(0xFFEA580C)
                                                MiddaySunIcon(color = iconColor, modifier = Modifier.size(28.dp))
                                            }
                                            else -> {
                                                val iconColor = if (isSystemInDarkTheme()) Color(0xFF90CAF9) else Color(0xFF1F7A6D)
                                                NightMoonIcon(color = iconColor, modifier = Modifier.size(28.dp))
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = schedule.label.replace(" Wheel", "").replace(" Batch", "").trim(),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PillPalTextPrimary
                                    )
                                }

                                // Large interactive clock edit timing capsule
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showTimeEditDialogForId = schedule.id }
                                        .border(1.dp, PillPalDivider, RoundedCornerShape(16.dp)),
                                    shape = RoundedCornerShape(16.dp),
                                    color = PillPalWarningBg
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = schedule.time,
                                            fontSize = 34.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = PillPalPrimaryDark,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Master Drop Time (Tap to Customize)",
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

                // Floating SAVE SYNC BUTTON
                Button(
                    onClick = {
                        val morning = schedulesList.find { it.id == "morning" }?.time ?: "08:00 AM"
                        val midday = schedulesList.find { it.id == "midday" }?.time ?: "01:30 PM"
                        val night = schedulesList.find { it.id == "night" }?.time ?: "08:00 PM"
                        viewModel.saveScheduleTime(morning, midday, night)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PillPalPrimaryDark),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .height(56.dp)
                        .testTag("save_sync_schedules_button"),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White)
                        Text("Save Sync To Dispenser", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                                text = "Dispenser Rotary Cartridges",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = PillPalTextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "A physical representation of the 3 integrated dosing wheels (7 slots each) inside PillPal.",
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
                                        text = "out of 21 slots",
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
                                    title = "Morning Wheel",
                                    filledCount = morningPills,
                                    iconContent = {
                                        val iconColor = if (isSystemInDarkTheme()) CustomPaletteMint else CustomPaletteEmerald
                                        MorningSunIcon(color = iconColor, modifier = Modifier.size(24.dp))
                                    },
                                    activeColor = if (isSystemInDarkTheme()) CustomPaletteMint else CustomPaletteEmerald
                                )

                                // 2. Midday Wheel Row
                                WheelBlueprintItem(
                                    title = "Midday Wheel",
                                    filledCount = middayPills,
                                    iconContent = {
                                        val iconColor = if (isSystemInDarkTheme()) Color(0xFFFFB480) else Color(0xFFEA580C)
                                        MiddaySunIcon(color = iconColor, modifier = Modifier.size(24.dp))
                                    },
                                    activeColor = if (isSystemInDarkTheme()) Color(0xFFFFB480) else Color(0xFFEA580C)
                                )

                                // 3. Night Wheel Row
                                WheelBlueprintItem(
                                    title = "Night Wheel",
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
                                Text("Capacity Remaining: ${((pillsRemaining / 21f) * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PillPalTextSecondary)
                                Text("Slots Empty: ${21 - pillsRemaining}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSystemInDarkTheme()) Color(0xFFFFB480) else Color(0xFFEA580C))
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
                        Text("Refill & Reset Cartridge (21 slots)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                    Text("Cancel", color = PillPalTextSecondary)
                }
            },
            title = {
                Text(
                    text = "Refill Dispenser Cartridges",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PillPalTextPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to refill the rotary cartridges? This will reset all 3 integrated wheels back to full capacity (21 total active pill slots remaining).",
                    fontSize = 15.sp,
                    color = PillPalTextSecondary
                )
            }
        )
    }

    // Custom elderly timing editor popup modal
    if (showTimeEditDialogForId != null) {
        val targetId = showTimeEditDialogForId!!
        val label = schedulesList.find { it.id == targetId }?.label ?: "Dose Time Picker"
        var inputHour by remember { mutableStateOf(8) }
        var inputMinute by remember { mutableStateOf(0) }
        var inputPeriod by remember { mutableStateOf("AM") }

        AlertDialog(
            onDismissRequest = { showTimeEditDialogForId = null },
            confirmButton = {
                Button(
                    onClick = {
                        val formattedTime = String.format(Locale.US, "%02d:%02d %s", inputHour, inputMinute, inputPeriod)
                        viewModel.saveScheduleTime(
                            morningTime = if (targetId == "morning") formattedTime else (schedulesList.find { it.id == "morning" }?.time ?: "08:00 AM"),
                            middayTime = if (targetId == "midday") formattedTime else (schedulesList.find { it.id == "midday" }?.time ?: "01:30 PM"),
                            nightTime = if (targetId == "night") formattedTime else (schedulesList.find { it.id == "night" }?.time ?: "08:00 PM")
                        )
                        showTimeEditDialogForId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PillPalPrimary)
                ) {
                    Text("Apply Time", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimeEditDialogForId = null }) {
                    Text("Cancel", color = PillPalTextSecondary)
                }
            },
            title = {
                Text(
                    text = "Edit $label",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PillPalTextPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Press the buttons below for easy modification:",
                        fontSize = 13.sp,
                        color = PillPalTextSecondary,
                        textAlign = TextAlign.Center
                    )

                    // Big digit buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // HOUR BUTTONS
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Hour", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            IconButton(onClick = { if (inputHour < 12) inputHour++ else inputHour = 1 }) {
                                Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Hour up")
                            }
                            Text(
                                text = String.format("%02d", inputHour),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = PillPalPrimary
                            )
                            IconButton(onClick = { if (inputHour > 1) inputHour-- else inputHour = 12 }) {
                                Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Hour down")
                            }
                        }

                        Text(":", fontSize = 28.sp, fontWeight = FontWeight.Bold)

                        // MINUTE BUTTONS
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Minute", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            IconButton(onClick = { if (inputMinute < 55) inputMinute += 5 else inputMinute = 0 }) {
                                Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Minute up")
                            }
                            Text(
                                text = String.format("%02d", inputMinute),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = PillPalPrimary
                            )
                            IconButton(onClick = { if (inputMinute >= 5) inputMinute -= 5 else inputMinute = 55 }) {
                                Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Minute down")
                            }
                        }

                        // AM/PM SWITCHER
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Period", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Button(
                                onClick = { inputPeriod = if (inputPeriod == "AM") "PM" else "AM" },
                                colors = ButtonDefaults.buttonColors(containerColor = PillPalSecondary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(inputPeriod, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
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
                    text = "$title ($filledCount/7 slots filled)",
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

// ==========================================
// TAB 3: ACTIVITY HISTORY (Image 4)
// ==========================================
@Composable
fun HistoryView(viewModel: PillPalViewModel) {
    val filter by viewModel.historyFilter.collectAsState()
    val allLogs by viewModel.historyLogs.collectAsState()

    // Filter local records safely
    val filteredLogs = remember(filter, allLogs) {
        if (filter == "All") allLogs
        else allLogs.filter { it.category == filter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Activity History",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = PillPalTextPrimary,
                modifier = Modifier.testTag("activity_history_title")
            )

            Text(
                text = "Clear All",
                color = PillPalAlert,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable { viewModel.clearHistory() }
                    .padding(8.dp)
            )
        }

        // Horizontal Filters Chips Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filters = listOf("All", "Doses", "Hardware Status")
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
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = PillPalDisabled, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No events found in archives.", fontSize = 16.sp, color = PillPalTextSecondary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredLogs) { log ->
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
                            val leftBarColor = when (log.type) {
                                "dose_missed" -> PillPalAlert
                                "low_battery" -> PillPalAlert
                                "dose_dispensed", "manual_drop" -> PillPalSuccess
                                else -> PillPalAccent
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
                                    val iconBg = when (log.type) {
                                        "dose_missed", "low_battery" -> PillPalAlertBg
                                        "dose_dispensed", "manual_drop" -> PillPalSuccessBg
                                        else -> PillPalWarningBg
                                    }
                                    val iconTint = when (log.type) {
                                        "dose_missed", "low_battery" -> PillPalAlert
                                        "dose_dispensed", "manual_drop" -> PillPalSuccess
                                        else -> PillPalPrimary
                                    }
                                    val icon = when (log.type) {
                                        "dose_missed" -> Icons.Default.Warning
                                        "low_battery" -> Icons.Default.Warning
                                        "dose_dispensed" -> Icons.Default.CheckCircle
                                        "manual_drop" -> Icons.Default.PlayArrow
                                        else -> Icons.Default.Info
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
                                            text = log.title,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PillPalTextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = log.description,
                                            fontSize = 13.sp,
                                            color = PillPalTextSecondary,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                                // Date description labels
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = log.time,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PillPalTextPrimary
                                    )
                                    Text(
                                        text = log.dateLabel,
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

// ==========================================
// TAB 4: CONTROLS & OVERRIDES (Image 5 & 6)
// ==========================================
@Composable
fun ControlsView(viewModel: PillPalViewModel) {
    val online by viewModel.isDeviceOnline.collectAsState()
    val powerSource by viewModel.devicePowerStatus.collectAsState()
    val remainingPills by viewModel.pillsRemaining.collectAsState()

    var activeDispenseProgressMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column {
            Text(
                text = "Hardware Override Center",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = PillPalTextPrimary,
                modifier = Modifier.testTag("hardware_override_title")
            )
            Text(
                text = "Remote control interface for your PillPal device.",
                fontSize = 14.sp,
                color = PillPalTextSecondary,
                fontWeight = FontWeight.Medium
            )
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
                        text = "Live Diagnostics Telemetry",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PillPalTextPrimary
                    )
                }

                Divider(color = PillPalDivider.copy(alpha = 0.5f))

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
                            Text("WIRELESS LINK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PillPalTextSecondary)
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
                                    text = if (online) "Online" else "Offline",
                                    fontWeight = FontWeight.Bold,
                                    color = if (online) PillPalSuccess else PillPalAlert,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // Power card
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
                            Text("POWER SOURCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PillPalTextSecondary)
                            Text(
                                text = powerSource,
                                fontWeight = FontWeight.Bold,
                                color = PillPalTextPrimary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Overrides Warning Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = PillPalWarningText)
            Text("Manual Releases", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PillPalTextPrimary)
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
                Triple("Release Morning Wheel", "morning", if (isSystemInDarkTheme()) CustomPaletteMint else CustomPaletteEmerald),
                Triple("Release Midday Wheel", "midday", if (isSystemInDarkTheme()) Color(0xFFFFB480) else Color(0xFFEA580C)),
                Triple("Release Night Wheel", "night", if (isSystemInDarkTheme()) Color(0xFF90CAF9) else Color(0xFF1F7A6D))
            )

            dispenseTriggers.forEach { (label, batch, color) ->
                Button(
                    onClick = {
                        scope.launch {
                            activeDispenseProgressMessage = "IoT Signal Sent. Actuating rotary motor carousel for $batch drop..."
                            delay(1800)
                            viewModel.forceDispense(batch)
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
                            when (batch) {
                                "morning" -> MorningSunIcon(color = color, modifier = Modifier.size(24.dp))
                                "midday" -> MiddaySunIcon(color = color, modifier = Modifier.size(24.dp))
                                else -> NightMoonIcon(color = color, modifier = Modifier.size(24.dp))
                            }
                            Text(label, color = PillPalTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                    headlineContent = { Text("Calibrate & Home Carousels", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                    supportingContent = { Text("Resets internal rotary rotors to 0° positions", fontSize = 12.sp, color = PillPalTextSecondary) },
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

                Divider(color = PillPalDivider.copy(alpha = 0.5f))

                // Reboot Row
                ListItem(
                    headlineContent = { Text("Restart Physical Device", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                    supportingContent = { Text("Performs hard system power-cycle reset on wifi board", fontSize = 12.sp, color = PillPalTextSecondary) },
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
                    text = "Log Out Caregiver Session",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
