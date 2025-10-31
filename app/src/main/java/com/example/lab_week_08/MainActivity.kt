package com.example.lab_week_08

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.example.lab_week_08.worker.FirstWorker
import com.example.lab_week_08.worker.SecondWorker
import com.example.lab_week_08.worker.ThirdWorker 
class MainActivity : AppCompatActivity() {

    // You cannot initialize WorkManager here because 'this' (Context) is not available yet.
    // It must be initialized inside or after onCreate.
    private lateinit var workManager: WorkManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize WorkManager here
        workManager = WorkManager.getInstance(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // --- Define all three worker requests ---
        val id = "001"
        val firstRequest = OneTimeWorkRequest.Builder(FirstWorker::class.java)
            .setConstraints(networkConstraints)
            .setInputData(getIdInputData(FirstWorker.INPUT_DATA_ID, id))
            .build()

        val secondRequest = OneTimeWorkRequest.Builder(SecondWorker::class.java)
            .setConstraints(networkConstraints)
            .setInputData(getIdInputData(SecondWorker.INPUT_DATA_ID, id))
            .build()

        // Request for the ThirdWorker
        val thirdRequest = OneTimeWorkRequest.Builder(ThirdWorker::class.java)
            .setConstraints(networkConstraints)
            .setInputData(getIdInputData(ThirdWorker.INPUT_DATA_ID, "003")) // Using a different ID for clarity
            .build()

        // --- Chain the workers and enqueue ---
        // FirstWorker -> SecondWorker -> ThirdWorker
        workManager.beginWith(firstRequest)
            .then(secondRequest)
            .then(thirdRequest) // Add ThirdWorker to the chain
            .enqueue()

        // --- Observe the results of each worker ---
        workManager.getWorkInfoByIdLiveData(firstRequest.id).observe(this) { info ->
            if (info != null && info.state.isFinished) {
                showResult("First process is done")
            }
        }

        workManager.getWorkInfoByIdLiveData(secondRequest.id).observe(this) { info ->
            if (info != null && info.state.isFinished) {
                showResult("Second process is done")
                // Launch NotificationService after SecondWorker is finished
                launchFirstNotificationService()
            }
        }

        // Observer for ThirdWorker
        workManager.getWorkInfoByIdLiveData(thirdRequest.id).observe(this) { info ->
            if (info != null && info.state.isFinished) {
                showResult("Third process is done")
                // Launch SecondNotificationService after ThirdWorker is finished
                launchSecondNotificationService()
            }
        }
    }

    private fun getIdInputData(idKey: String, idValue: String): Data =
        Data.Builder()
            .putString(idKey, idValue)
            .build()

    private fun showResult(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun launchFirstNotificationService() {
        NotificationService.trackingCompletion.observe(this) { id ->
            showResult("Process for Notification Channel ID $id is done!")
        }
        val serviceIntent = Intent(this, NotificationService::class.java).apply {
            putExtra(NotificationService.EXTRA_ID, "001")
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    // New function to launch the SecondNotificationService
    private fun launchSecondNotificationService() {
        SecondNotificationService.trackingCompletion.observe(this) { id ->
            showResult("Process for Second Notification Channel ID $id is done!")
        }
        val serviceIntent = Intent(this, SecondNotificationService::class.java).apply {
            putExtra(SecondNotificationService.EXTRA_ID, "002")
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }
}
