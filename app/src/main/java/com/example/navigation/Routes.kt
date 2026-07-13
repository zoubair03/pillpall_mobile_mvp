package com.example.navigation

// Centralized route names — avoids stringly-typed duplication between
// PillPalNavGraph's NavHost registration and the ViewModels/screens that
// navigate() to them.
object Routes {
    const val SIGN_IN = "sign_in"
    const val CREATE_PROFILE = "create_profile"
    const val VERIFY_EMAIL_OTP = "verify_email_otp"

    const val ONBOARDING_GRAPH = "onboarding"
    const val NO_DEVICE = "no_device"
    const val CONNECT_WIFI = "connect_wifi"
    const val PAIRED_SUCCESS = "paired_success"
    const val PATIENT_PROFILE = "patient_profile"
    const val SCHEDULE_SETUP = "schedule_setup"

    const val MAIN_HUB = "main_hub"
    const val TAB_HOME = "home"
    const val TAB_SCHEDULE = "schedule"
    const val TAB_HISTORY = "history"
    const val TAB_CONTROLS = "controls"
}
