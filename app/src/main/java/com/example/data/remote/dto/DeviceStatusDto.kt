package com.example.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceStatusDto(
    @SerialName("device_id") val deviceId: String,
    @SerialName("battery_pct") val batteryPct: Int? = null,
    @SerialName("wifi_connected") val wifiConnected: Boolean? = null,
    @SerialName("wifi_ip") val wifiIp: String? = null,
    @SerialName("rtc_ok") val rtcOk: Boolean? = null,
    @SerialName("motor_morning_state") val motorMorningState: String? = null,
    @SerialName("motor_midday_state") val motorMiddayState: String? = null,
    @SerialName("motor_night_state") val motorNightState: String? = null,
    @SerialName("updated_at") val updatedAt: String,
)
