package com.example.lab_week_08

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper // Import untuk Handler di Main Thread
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData // Import untuk LiveData
import androidx.lifecycle.MutableLiveData // Import untuk MutableLiveData
import com.example.lab_week_08.MainActivity

/**
 * Sebuah Service yang berjalan di foreground untuk menampilkan notifikasi persisten.
 * Ini memastikan aplikasi tetap hidup dan notifikasi tidak dapat dihapus.
 */
class NotificationService : Service() {

    // Konstanta untuk Notifikasi, LiveData untuk komunikasi
    companion object {
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "service_channel_001"
        const val CHANNEL_NAME = "Persistent Background Task"

        // Konstanta yang dibutuhkan untuk mengirim ID melalui Intent dari Activity
        const val EXTRA_ID = "Id"

        // LiveData yang merupakan wadah data yang secara otomatis
        // memperbarui UI (MainActivity) berdasarkan apa yang diamati.
        private val mutableID = MutableLiveData<String>()
        val trackingCompletion: LiveData<String> = mutableID
    }

    private lateinit var serviceHandler: Handler

    // Tidak digunakan karena ini adalah 'started service', bukan 'bound service'.
    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // Buat dan mulai HandlerThread untuk menjalankan tugas-tugas di background thread terpisah
        val handlerThread = HandlerThread("BackgroundServiceThread").apply { start() }
        serviceHandler = Handler(handlerThread.looper)

        // Setup notifikasi dan mulai foreground service
        startForeground(NOTIFICATION_ID, createNotification().build())
    }

    // This is a callback and part of a life cycle
    // This callback will be called when the service is started
    // in this case, after the startForeground() method is called
    // in your startForegroundService() custom function
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val returnValue = super.onStartCommand(intent, flags, startId)

        // Gets the channel id passed from the MainActivity through the Intent
        val Id = intent?.getStringExtra(EXTRA_ID) ?: throw IllegalStateException("Channel ID must be provided")

        // Posts the notification task to the handler,
        // which will be executed on a different thread
        serviceHandler.post {
            // Sets up what happens after the notification is posted
            // Here, we're counting down from 10 to 0 in the notification
            // We use createNotification() to get a fresh builder for updates.
            countDownFromTenToZero(createNotification())

            // Here we're notifying the MainActivity that the service process is done
            // by returning the channel ID through LiveData
            notifyCompletion(Id)

            // Stops the foreground service, which closes the notification
            // but the service still goes on
            stopForeground(STOP_FOREGROUND_REMOVE)

            // Stop and destroy the service
            stopSelf()
        }
        return returnValue
    }

    /**
     * Membuat PendingIntent untuk notifikasi. Ketika notifikasi diklik,
     * PendingIntent ini akan mengarahkan pengguna kembali ke MainActivity.
     */
    private fun getPendingIntent(): PendingIntent {
        // Gunakan FLAG_IMMUTABLE untuk API 23+ (wajib untuk API 31+) untuk PendingIntent yang aman.
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }

        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            flag
        )
    }

    /**
     * Membuat dan mendaftarkan NotificationChannel (wajib untuk Android O / API 26 ke atas).
     * Jika di bawah O, fungsi ini hanya mengembalikan ID default.
     */
    private fun createNotificationChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Gunakan LOW untuk Foreground Service agar tidak mengganggu
            ).apply {
                description = "Notification channel for running foreground tasks."
                setShowBadge(false)
            }

            // Daftarkan channel pada Notification Manager
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        return CHANNEL_ID
    }

    /**
     * Membuat NotificationCompat.Builder dengan semua konfigurasi yang diperlukan.
     */
    private fun createNotification(): NotificationCompat.Builder {
        val channelId = createNotificationChannel()
        val pendingIntent = getPendingIntent()

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Service is Running")
            .setContentText("A background task is currently active.")
            .setSmallIcon(android.R.drawable.ic_lock_power_off) // Ganti dengan ikon aplikasi Anda
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Sesuai dengan IMPORTANCE_LOW di channel
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true) // Notifikasi harus ongoing (tidak dapat di-swipe) untuk Foreground Service.
    }

    // A function to update the notification to display a count down from 10 to 0
    private fun countDownFromTenToZero(notificationBuilder: NotificationCompat.Builder) {
        // Gets the notification manager
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Count down from 10 to 0
        for (i in 10 downTo 0) {
            Thread.sleep(1000L)

            // Updates the notification content text
            notificationBuilder.setContentText("$i seconds until last warning")
                .setSilent(true)

            // Notify the notification manager about the content update
            notificationManager.notify(
                NOTIFICATION_ID,
                notificationBuilder.build()
            )
        }
    }

    // Update the LiveData with the returned channel id through the Main Thread
    // the Main Thread is identified by calling the "getMainLooper()" method
    // This function is called after the count down has completed
    private fun notifyCompletion(Id: String) {
        Handler(Looper.getMainLooper()).post {
            mutableID.value = Id
        }
    }
}
