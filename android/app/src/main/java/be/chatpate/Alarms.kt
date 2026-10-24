package be.chatpate

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/** Programme une alarme système pour chaque repas à venir. */
object Alarms {
    const val ACTION = "be.chatpate.ALARM"

    fun canScheduleExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(AlarmManager::class.java)
        return am.canScheduleExactAlarms()
    }

    @Synchronized
    fun reschedule(ctx: Context) {
        val app = ctx.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val prefs = app.getSharedPreferences("prefs", Context.MODE_PRIVATE)

        val old = HashSet(prefs.getStringSet("alarms", emptySet()) ?: emptySet())
        for (id in old) am.cancel(pendingIntent(app, id))

        val now = System.currentTimeMillis()
        val upcoming = Store.snapshot().entries
            .filter { !it.deleted && it.status == Status.PENDING && it.at > now }
            .sortedBy { it.at }
            .take(50)

        val exact = canScheduleExact(app)
        for (e in upcoming) {
            val pi = pendingIntent(app, e.id)
            try {
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pi)
            } catch (se: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pi)
            }
        }
        prefs.edit().putStringSet("alarms", upcoming.map { it.id }.toSet()).apply()
    }

    private fun pendingIntent(ctx: Context, entryId: String): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION)
            .setData(Uri.parse("chatpate://entry/$entryId"))
            .putExtra("entryId", entryId)
        return PendingIntent.getBroadcast(
            ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
