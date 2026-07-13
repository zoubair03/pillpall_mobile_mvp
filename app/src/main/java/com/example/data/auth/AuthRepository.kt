package com.example.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.OtpVerifyResult
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    data object SignedIn : AuthResult()
    data object AwaitingEmailConfirmation : AuthResult()
    data class Error(val message: String) : AuthResult()
}

@Singleton
class AuthRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
) {
    val sessionStatus: StateFlow<SessionStatus>
        get() = supabaseClient.auth.sessionStatus

    val currentUserId: String?
        get() = supabaseClient.auth.currentUserOrNull()?.id

    suspend fun signIn(email: String, password: String): AuthResult = try {
        supabaseClient.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        AuthResult.SignedIn
    } catch (e: Exception) {
        AuthResult.Error(mapAuthError(e))
    }

    suspend fun signUp(email: String, password: String): AuthResult = try {
        val user = supabaseClient.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        // signUpWith returns null when email confirmation is disabled and the
        // caller is already logged in; a non-null user means confirmation is
        // required and there's no session yet.
        if (user == null) AuthResult.SignedIn else AuthResult.AwaitingEmailConfirmation
    } catch (e: Exception) {
        AuthResult.Error(mapAuthError(e))
    }

    suspend fun signOut() {
        supabaseClient.auth.signOut()
    }

    // Confirms signup with the 6-digit code from the "Confirm signup" email
    // instead of the caregiver clicking a link — establishes a real session
    // on success (handled automatically by the SDK).
    suspend fun verifyEmailOtp(email: String, token: String): AuthResult = try {
        when (supabaseClient.auth.verifyEmailOtp(OtpType.Email.SIGNUP, email, token)) {
            is OtpVerifyResult.Authenticated -> AuthResult.SignedIn
            is OtpVerifyResult.VerifiedNoSession -> AuthResult.Error("La vérification a échoué. Veuillez réessayer.")
        }
    } catch (e: Exception) {
        AuthResult.Error(mapAuthError(e))
    }

    suspend fun resendSignupOtp(email: String): AuthResult = try {
        supabaseClient.auth.resendEmail(OtpType.Email.SIGNUP, email)
        AuthResult.AwaitingEmailConfirmation
    } catch (e: Exception) {
        AuthResult.Error(mapAuthError(e))
    }

    // Maps known Supabase Auth error codes to plain-language French messages.
    // Anything unrecognized (including plain network failures) gets a generic
    // message rather than a raw exception dump.
    private fun mapAuthError(e: Exception): String {
        if (e is AuthRestException) {
            return when (e.errorCode) {
                AuthErrorCode.InvalidCredentials -> "Adresse email ou mot de passe incorrect"
                AuthErrorCode.EmailNotConfirmed -> "Veuillez confirmer votre adresse email avant de vous connecter"
                AuthErrorCode.EmailAddressInvalid -> "Cette adresse email n'est pas valide"
                AuthErrorCode.UserAlreadyExists -> "Un compte existe déjà avec cette adresse email"
                AuthErrorCode.WeakPassword -> "Le mot de passe est trop faible (8 caractères minimum recommandé)"
                AuthErrorCode.OtpExpired -> "Ce code a expiré ou est invalide. Demandez-en un nouveau."
                else -> e.errorDescription
            }
        }
        if (e is IOException) {
            return "Impossible de contacter le serveur. Vérifiez votre connexion internet."
        }
        return e.message ?: "Une erreur inattendue s'est produite"
    }
}
