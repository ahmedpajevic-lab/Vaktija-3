package ba.vaktija.premium.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import ba.vaktija.premium.service.VaktijaNotificationService

class BootAndVakatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, VaktijaNotificationService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
