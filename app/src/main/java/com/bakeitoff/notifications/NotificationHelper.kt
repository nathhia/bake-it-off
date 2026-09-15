package com.bakeitoff.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bakeitoff.MainActivity
import com.bakeitoff.R

object NotificationHelper {

    const val CHANNEL_ID_PROGRESS = "extraction_progress"
    // Bumped to a new id (was "extraction_result") so this gets created fresh with
    // IMPORTANCE_HIGH — Android locks a channel's importance in at creation time,
    // so reusing the old id would keep silently defaulting to the old DEFAULT
    // importance on phones that already had the app installed, with no heads-up
    // banner or sound, which is exactly why the "finished" notification was easy
    // to miss (it landed in the shade at almost the same moment the ongoing
    // "carregando" notification disappeared, with nothing to signal it was new).
    const val CHANNEL_ID_RESULT = "extraction_result_v2"

    const val PROGRESS_NOTIFICATION_ID = 1001
    private const val RESULT_NOTIFICATION_ID = 1002

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val progressChannel = NotificationChannel(
            CHANNEL_ID_PROGRESS,
            context.getString(R.string.canal_progresso_extracao),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }

        val resultChannel = NotificationChannel(
            CHANNEL_ID_RESULT,
            context.getString(R.string.canal_resultado_extracao),
            NotificationManager.IMPORTANCE_HIGH
        )

        manager.createNotificationChannel(progressChannel)
        manager.createNotificationChannel(resultChannel)
    }

    fun buildProgressNotification(context: Context, contentText: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID_PROGRESS)
            .setContentTitle(context.getString(R.string.notificacao_processando_titulo))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent(context))
            .build()
    }

    fun notifySuccess(context: Context, recipeTitle: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_RESULT)
            .setContentTitle(context.getString(R.string.notificacao_sucesso_titulo))
            .setContentText(recipeTitle)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent(context))
            .build()
        notify(context, notification)
    }

    fun notifyError(context: Context, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_RESULT)
            .setContentTitle(context.getString(R.string.notificacao_erro_titulo))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent(context))
            .build()
        notify(context, notification)
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notify(context: Context, notification: Notification) {
        // On API 33+ this can be denied — the foreground service itself still keeps
        // the pipeline alive either way, this only gates whether she sees the ping.
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(RESULT_NOTIFICATION_ID, notification)
    }
}
