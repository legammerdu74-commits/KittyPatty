package be.chatpate

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Notifier {
    private const val CHANNEL = "repas"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Repas des chats", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Rappels pour donner la pâtée"
            nm.createNotificationChannel(ch)
        }
    }

    private fun notifId(entryId: String) = entryId.hashCode()

    fun cancel(ctx: Context, entryId: String) {
        NotificationManagerCompat.from(ctx).cancel(notifId(entryId))
    }

    private fun joinNames(names: List<String>): String = when {
        names.isEmpty() -> ""
        names.size == 1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " et " + names.last()
    }

    fun show(ctx: Context, e: Entry) {
        ensureChannel(ctx)
        val state = Store.snapshot()
        val allCats = state.cats.filter { !it.deleted }
        val product = state.products.firstOrNull { it.id == e.productId && !it.deleted }
        val names = allCats.filter { it.id in e.catIds }.map { it.name }
        val who = joinNames(names)
        val pate = product?.name ?: e.foodTypeName.ifEmpty { pateLabel(e.pate) }
        val hour = DateTimeFormatter.ofPattern("HH:mm")
            .format(Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault()))
        val qty = if (e.qty.isBlank()) "" else " · ${e.qty}"
        val title = if (who.isEmpty()) "Heure de la pâtée 🐾" else "Heure de la pâtée de $who 🐾"
        val text = "${if (product == null) "Pâtée " else ""}$pate$qty"
        val big = "Prévu à $hour\n${if (product == null) "Pâtée " else ""}$pate$qty" +
            (if (who.isNotEmpty()) "\nPour : $who" else "") +
            "\nAppuie pour encoder ce que tu as donné."

        val open = PendingIntent.getActivity(
            ctx, notifId(e.id),
            Intent(ctx, MainActivity::class.java)
                .putExtra("entryId", e.id)
                .setData(Uri.parse("chatpate://open/${e.id}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val done = PendingIntent.getBroadcast(
            ctx, notifId(e.id),
            Intent(ctx, ActionReceiver::class.java)
                .setAction(ActionReceiver.ACTION_DONE)
                .setData(Uri.parse("chatpate://done/${e.id}"))
                .putExtra("entryId", e.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_paw)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(hour)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Donné ✓", done)
            .build()

        notify(ctx, notifId(e.id), n)
    }

    fun showTest(ctx: Context) {
        val cats = Store.snapshot().cats.filter { !it.deleted }
        show(
            ctx,
            Entry(
                id = "test_${System.currentTimeMillis()}",
                planId = "test",
                at = System.currentTimeMillis(),
                pate = Pate.POULET.key,
                qty = "1 sachet",
                catIds = cats.map { it.id },
            )
        )
    }

    private fun notify(ctx: Context, id: Int, n: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) {
            // permission refusée : on ne fait rien
        }
    }
}
