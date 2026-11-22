package com.localprivacy.firewall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.IOException

/**
 * Per-app packet sink. It never relays packets or creates a remote connection.
 * This is network isolation, NOT an app freezer or a guarantee against delayed
 * telemetry delivered by another package after the VPN is stopped.
 */
class FirewallVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.localprivacy.firewall.START"
        const val ACTION_STOP = "com.localprivacy.firewall.STOP"
        const val ACTION_REFRESH = "com.localprivacy.firewall.REFRESH"
        const val ACTION_STATE = "com.localprivacy.firewall.STATE_CHANGED"

        @Volatile var isActive = false
            private set

        private const val CHANNEL = "firewall_status"
        private const val NOTIFICATION_ID = 101
    }

    @Volatile private var activeTunnel: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL, "VPN firewall status", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when local network isolation is active"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Rules.setDesiredRunning(this, false)
                closeTunnel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                if (!isActive) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            else -> {
                // The system may restart an Always-on VPN with a null intent.
                if (intent == null && !Rules.desiredRunning(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }

        val selected = Rules.selected(this).filter { it != packageName }.toSet()
        if (selected.isEmpty()) {
            Rules.setDesiredRunning(this, false)
            Rules.setLastError(this, "Choose at least one app before starting.")
            closeTunnel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            // Active user-approved VPNs qualify for systemExempted foreground service.
            startForeground(
                NOTIFICATION_ID,
                notification(selected.size),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
            establishFilter(selected)
            Rules.setDesiredRunning(this, true)
            Rules.setLastError(this, "")
            sendStatus()
            return START_STICKY
        } catch (error: Exception) {
            Rules.setLastError(this, "VPN could not start: ${error.message ?: error.javaClass.simpleName}")
            Rules.setDesiredRunning(this, false)
            closeTunnel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
    }

    private fun establishFilter(packages: Set<String>) {
        val builder = Builder()
            .setSession("Local Privacy Firewall")
            .setMtu(1500)
            .setBlocking(true)
            .addAddress("10.251.0.1", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("10.251.0.2")
            .addAddress("fd00:251::1", 128)
            .addRoute("::", 0)
            .addDnsServer("fd00:251::2")

        val configure = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setConfigureIntent(configure)

        var accepted = 0
        for (app in packages) {
            try {
                builder.addAllowedApplication(app)
                accepted++
            } catch (_: PackageManager.NameNotFoundException) {
                // An application may have been uninstalled since selection.
            }
        }
        check(accepted > 0) { "No selected packages are installed or eligible." }

        // This replaces the previously established interface on reconfiguration.
        val next = builder.establish() ?: error("Android did not grant VPN access")
        val previous = activeTunnel
        activeTunnel = next
        isActive = true
        try { previous?.close() } catch (_: IOException) { }

        Thread({ consumeAndDiscard(next) }, "local-packet-sink").start()
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(accepted))
    }

    private fun consumeAndDiscard(fd: ParcelFileDescriptor) {
        try {
            FileInputStream(fd.fileDescriptor).use { input ->
                val packet = ByteArray(65_535)
                while (activeTunnel === fd) {
                    val read = input.read(packet)
                    if (read < 0) break
                    // No packet is parsed, stored, replied to, or forwarded.
                }
            }
        } catch (_: IOException) {
            // Expected when Android revokes or the user closes the tunnel.
        } finally {
            if (activeTunnel === fd) {
                android.os.Handler(mainLooper).post {
                    if (activeTunnel === fd) {
                        Rules.setLastError(this, "VPN tunnel closed unexpectedly.")
                        Rules.setDesiredRunning(this, false)
                        closeTunnel()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun notification(count: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, FirewallVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(com.localprivacy.firewall.R.drawable.ic_shield)
            .setContentTitle("Local Privacy Firewall active")
            .setContentText("Blocking internet for $count selected app(s)")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop firewall", stop)
            .build()
    }

    private fun closeTunnel() {
        val old = activeTunnel
        activeTunnel = null
        isActive = false
        try { old?.close() } catch (_: IOException) { }
        sendStatus()
    }

    private fun sendStatus() {
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName))
    }

    override fun onRevoke() {
        Rules.setDesiredRunning(this, false)
        Rules.setLastError(this, "VPN authorization was revoked.")
        closeTunnel()
        stopSelf()
    }

    override fun onDestroy() {
        closeTunnel()
        super.onDestroy()
    }
}
