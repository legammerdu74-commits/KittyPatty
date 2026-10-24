package be.chatpate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    // Id du repas à ouvrir quand on appuie sur une notification.
    private val openEntry = mutableStateOf<String?>(null)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openEntry.value = intent?.getStringExtra("entryId")

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            ChatTheme { App(openEntry) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openEntry.value = intent.getStringExtra("entryId")
    }

    override fun onResume() {
        super.onResume()
        // Met les repas à jour, reprogramme les alarmes et synchronise avec le serveur.
        Planner.afterChange(this)
    }
}
