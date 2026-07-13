package com.example.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.navigation.Routes
import com.example.ui.theme.*
import com.example.ui.viewmodel.OnboardingViewModel

@Composable
fun VerifyEmailOtpScreen(viewModel: OnboardingViewModel, navController: NavController) {
    val email by viewModel.profileEmail.collectAsState()
    val code by viewModel.emailOtpCode.collectAsState()
    val isLoading by viewModel.authLoading.collectAsState()
    val error by viewModel.authError.collectAsState()
    val resendConfirmation by viewModel.otpResendConfirmation.collectAsState()
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { navController.navigate(Routes.CREATE_PROFILE) }) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Go back", tint = PillPalPrimary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
                    text = "Vérifiez votre email",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = PillPalTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Nous avons envoyé un code à 6 chiffres à $email. Saisissez-le ci-dessous.",
                    fontSize = 15.sp,
                    color = PillPalTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(30.dp))

                // Six display boxes over a single real (invisible) text field —
                // same pattern as EnterPairingCodeScreen's pairing-code entry.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { focusRequester.requestFocus() }
                ) {
                    BasicTextField(
                        value = code,
                        onValueChange = { viewModel.updateEmailOtpCode(it) },
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .size(1.dp)
                            .alpha(0f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (!isLoading) viewModel.verifyEmailOtp() }),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0..5) {
                            val char = code.getOrNull(i)?.toString() ?: ""
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.85f)
                                    .border(
                                        width = if (char.isNotBlank()) 2.dp else 1.dp,
                                        color = if (char.isNotBlank()) PillPalPrimary else PillPalDivider,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PillPalSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = char,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PillPalPrimary
                                )
                            }
                        }
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = error!!,
                        color = PillPalAlert,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                if (resendConfirmation != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = resendConfirmation!!,
                        color = PillPalPrimaryDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = { viewModel.verifyEmailOtp() },
                    enabled = !isLoading && code.length == 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("email_otp_submit_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = PillPalSecondary),
                    shape = RoundedCornerShape(27.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Vérifier & Continuer", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = {
                        viewModel.consumeOtpResendConfirmation()
                        viewModel.resendEmailOtp()
                    },
                    enabled = !isLoading,
                    modifier = Modifier.testTag("email_otp_resend_button")
                ) {
                    Text("Vous n'avez rien reçu ? Renvoyer le code", color = PillPalTextSecondary, fontSize = 13.sp)
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
