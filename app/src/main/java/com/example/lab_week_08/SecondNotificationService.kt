package com.example.lab_week_08

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class SecondNotificationService : Service() {

    companion object {
        const val NOTIFICATION_ID = 102
        const val CHANNEL_ID = "service_channel_002"
        const val CHANNEL_NAME = "Second Background Task"
        const val EXTRA_ID = "Id_Second"

        private val mutableID = MutableLiveData<String>()
        val trackingCompletion: LiveData<String> = mutableID
    }

    private lateinit var serviceHandler: Handler

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val handlerThread = HandlerThread("SecondBackgroundServiceThread").apply { start() }
        serviceHandler = Handler(handlerThread.looper)
        startForeground(NOTIFICATION_ID, createNotification().build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val returnValue = super.onStartCommand(intent, flags, startId)
        val id = intent?.getStringExtra(EXTRA_ID) ?: throw IllegalStateException("ID must be provided")

        serviceHandler.post {
            // Using a shorter countdown to avoid overlapping toasts
            countDownFromFiveToZero(createNotification())
            notifyCompletion(id)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return returnValue
    }

    private fun getPendingIntent(): PendingIntent {
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flag)
    }

    private fun createNotificationChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification channel for the second foreground task."
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        return CHANNEL_ID
    }

    private fun createNotification(): NotificationCompat.Builder {
        val channelId = createNotificationChannel()
        val pendingIntent = getPendingIntent()

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Second Service is Running")
            .setContentText("Another background task is active.")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Different icon
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
    }

    /**
     * Counts down from 5 to 0 to avoid toast collisions with the first service.
     */
    private fun countDownFromFiveToZero(notificationBuilder: NotificationCompat.Builder) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (i in 5 downTo 0) {
            Thread.sleep(1000L)
            notificationBuilder.setContentText("$i seconds remaining...")
                .setSilent(true)
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    private fun notifyCompletion(id: String) {
        Handler(Looper.getMainLooper()).post {
            mutableID.value = id
        }
    }
}
