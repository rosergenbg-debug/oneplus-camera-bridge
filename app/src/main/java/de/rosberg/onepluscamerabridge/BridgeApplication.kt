package de.rosberg.onepluscamerabridge

import android.app.Application
import java.io.File

class BridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val pending = Prefs.pendingPath(this)
        TempFiles.cleanupStale(this, pending)
        if (pending != null && !File(pending).exists()) Prefs.setPendingPath(this, null)
        LogStore.add(this, "Приложение запущено; выполнена проверка старых временных файлов")
    }
}
