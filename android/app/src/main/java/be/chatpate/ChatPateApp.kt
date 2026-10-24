package be.chatpate

import android.app.Application

class ChatPateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Store.ensureDefaultCat(Prefs.legacyCatName(this))
        Notifier.ensureChannel(this)
    }
}
