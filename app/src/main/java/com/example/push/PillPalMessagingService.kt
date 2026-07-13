package com.example.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.remote.PushTokenRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

// Registered in AndroidManifest.xml with the MESSAGING_EVENT intent filter.
// onNewToken fires whenever FCM (re)issues this device's registration token
// (fresh install, app data clear, token rotation) — the initial fetch-and-
// register on sign-in is handled separately by DeviceStatusViewModel, since
// this service isn't guaranteed to have fired yet at that point.
@AndroidEntryPoint
class PillPalMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushTokenRepository: PushTokenRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        serviceScope.launch { runCatching { pushTokenRepository.upsertToken(token) } }
    }

    // check-missed-doses sends a "notification" payload, which the OS
    // displays automatically while the app is backgrounded/killed. This
    // callback only fires while the app is in the foreground, in which case
    // FCM does NOT auto-display anything — the app has to build the banner
    // itself, or a caregiver looking at an unrelated screen would miss it.
    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        showNotification(notification.title, notification.body)
    }

    private fun showNotification(title: String?, body: String?) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Missed dose alerts", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "missed_dose_alerts"
    }
}
