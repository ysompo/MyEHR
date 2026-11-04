package com.myehr.app.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.myehr.app.MainActivity
import com.myehr.app.R
import com.myehr.app.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Background sync: runs after the user has logged in on the Meuhedet page.
 * Uses the session cookies directly, so the phone can be locked meanwhile.
 * Signs out of the site (clears cookies) when finished.
 */
class SyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (SyncState.status.value.running) return START_NOT_STICKY
        channel()
        val n = notification("מתחיל סנכרון…", ongoing = true)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID_PROGRESS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID_PROGRESS, n)
        wake = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "myehr:sync")
            .apply { acquire(45 * 60_000L) }
        SyncState.set(SyncState.Status(running = true, text = "מתחיל סנכרון…"))

        scope.launch {
            val caller = NativeCaller(WebSettings.getDefaultUserAgent(this@SyncService))
            val switcher = MemberSwitcher { guid ->
                val cm = CookieManager.getInstance()
                cm.setCookie("https://online.meuhedet.co.il",
                    if (guid == null) "${Api.MEMBER_COOKIE}=; Domain=.meuhedet.co.il; Path=/; Max-Age=0"
                    else "${Api.MEMBER_COOKIE}=$guid; Domain=.meuhedet.co.il; Path=/")
                cm.flush()
            }
            try {
                val store = Store.get(this@SyncService)
                val engine = SyncEngine(caller, store, onProgress = { progress(it) })
                val people = engine.syncFamily(switcher)
                done("הסנכרון הסתיים: ${people.joinToString(", ") { it.name }}")
                SyncState.set(SyncState.Status(text = "הסנכרון הסתיים", finishedAt = System.currentTimeMillis()))
            } catch (e: SessionExpired) {
                done("החיבור למאוחדת נותק. פתחו את MyEHR והתחברו שוב.")
                SyncState.set(SyncState.Status(text = "יש להתחבר שוב", needsLogin = true))
            } catch (e: Exception) {
                done("הסנכרון נכשל. נסו שוב.")
                SyncState.set(SyncState.Status(text = "הסנכרון נכשל (${e.javaClass.simpleName})"))
            } finally {
                // Sign out of the site: nothing stays logged in on the phone.
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                wake?.let { if (it.isHeld) it.release() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun progress(text: String) {
        SyncState.set(SyncState.Status(running = true, text = text))
        getSystemService(NotificationManager::class.java).notify(ID_PROGRESS, notification(text, ongoing = true))
    }

    private fun done(text: String) =
        getSystemService(NotificationManager::class.java).notify(ID_DONE, notification(text, ongoing = false))

    private fun channel() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "סנכרון ממאוחדת", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(text: String, ongoing: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle("MyEHR")
            .setContentText(text)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setContentIntent(open)
            // No medical details on the lock screen.
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
    }

    companion object {
        private const val CHANNEL = "sync"
        private const val ID_PROGRESS = 1
        private const val ID_DONE = 2
        fun start(context: Context) = context.startForegroundService(Intent(context, SyncService::class.java))
    }
}
