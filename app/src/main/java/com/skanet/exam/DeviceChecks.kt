package com.skanet.exam

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

enum class CheckId { INTERNET, BATTERY, NOTIF, TIME }

data class CheckItem(
    val id: CheckId,
    val title: String,
    val detail: String,
    val passed: Boolean,
    val fixable: Boolean
)

object DeviceChecks {

    fun run(ctx: Context): List<CheckItem> {
        val online = isOnline(ctx)
        val (pct, charging) = battery(ctx)
        val batteryOk = charging || pct >= 30
        val notifOk = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        val timeOk = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AUTO_TIME, 0) == 1

        return listOf(
            CheckItem(
                CheckId.INTERNET, "Koneksi Internet",
                if (online) "Terhubung ke internet" else "Tidak ada koneksi internet",
                online, true
            ),
            CheckItem(
                CheckId.BATTERY, "Baterai",
                when {
                    pct < 0 -> "Level baterai tidak terbaca"
                    charging -> "Baterai $pct% (sedang mengisi)"
                    batteryOk -> "Baterai $pct%"
                    else -> "Baterai $pct%, minimal 30% atau colok charger"
                },
                batteryOk, false
            ),
            CheckItem(
                CheckId.NOTIF, "Izin Notifikasi",
                if (notifOk) "Notifikasi diizinkan" else "Aktifkan notifikasi untuk aplikasi ini",
                notifOk, true
            ),
            CheckItem(
                CheckId.TIME, "Waktu Otomatis",
                if (timeOk) "Waktu mengikuti jaringan" else "Aktifkan tanggal dan waktu otomatis",
                timeOk, true
            )
        )
    }

    private fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun battery(ctx: Context): Pair<Int, Boolean> {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        return Pair(pct, charging)
    }
}