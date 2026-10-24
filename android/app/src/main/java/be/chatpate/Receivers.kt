package be.chatpate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/** Sonne à l'heure du repas : affiche la notification. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("entryId") ?: return
        val app = context.applicationContext
        val pending = goAsync()
        thread {
            try {
                val e = Store.get(id)
                if (e != null && !e.deleted && e.status == Status.PENDING) {
                    Notifier.show(app, e)
                }
                // On resynchronise (si tu as déjà donné la pâtée depuis le PC, la notif disparaît).
                Planner.reconcile()
                Alarms.reschedule(app)
                Sync.run(app, timeoutMs = 4000)
                val after = Store.get(id)
                if (after != null && (after.deleted || after.status != Status.PENDING)) {
                    Notifier.cancel(app, id)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Bouton "Donné ✓" directement dans la notification. */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DONE = "be.chatpate.DONE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DONE) return
        val id = intent.getStringExtra("entryId") ?: return
        val app = context.applicationContext
        val e = Store.get(id)
        if (e != null && e.status == Status.PENDING) {
            Store.markDone(id, e.pate, e.qty, "", System.currentTimeMillis())
        }
        Notifier.cancel(app, id)
        val pending = goAsync()
        thread {
            try {
                Alarms.reschedule(app)
                Sync.run(app, timeoutMs = 5000)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Après un redémarrage du téléphone (ou un changement d'heure), on remet les alarmes en place. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        thread {
            try {
                Planner.reconcile()
                Alarms.reschedule(app)
            } finally {
                pending.finish()
            }
        }
    }
}
