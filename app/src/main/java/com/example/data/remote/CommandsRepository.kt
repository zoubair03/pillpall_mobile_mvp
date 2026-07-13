package com.example.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

sealed class CommandResult {
    data object Success : CommandResult()
    data class Error(val message: String) : CommandResult()
}

// Wraps the publish-command Edge Function. Only "update_schedule" and
// "ack_dose" are accepted by the deployed function today — see
// backend/supabase/functions/publish-command/index.ts.
@Singleton
class CommandsRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
) {
    suspend fun publish(
        deviceId: String,
        command: String,
        payload: JsonObject = buildJsonObject {},
    ): CommandResult = try {
        val response: HttpResponse = supabaseClient.functions.invoke(
            function = "publish-command",
            body = buildJsonObject {
                put("device_id", deviceId)
                put("command", command)
                put("payload", payload)
            },
        )
        when (response.status) {
            HttpStatusCode.OK -> CommandResult.Success
            HttpStatusCode.Unauthorized -> CommandResult.Error("Session expirée, veuillez vous reconnecter")
            HttpStatusCode.Forbidden -> CommandResult.Error("Vous n'êtes pas autorisé à contrôler cet appareil")
            HttpStatusCode.BadGateway -> CommandResult.Error("L'appareil n'a pas pu être contacté")
            else -> CommandResult.Error("Échec de la commande (${response.status.value})")
        }
    } catch (e: Exception) {
        CommandResult.Error(e.message ?: "Échec de la commande")
    }
}
