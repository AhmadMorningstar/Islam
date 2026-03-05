package com.AhmadMorningstar.islam

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

// ---------------------------------------------------------------------------
// UPDATE CONFIG MODEL
// ---------------------------------------------------------------------------

data class UpdateConfig(
    val min_version: Long,
    val latest_version: Long,
    val force_after: String,
    val message: String,
    val regions_optional: List<String>,
)

// ---------------------------------------------------------------------------
// UPDATE CHECKER
// ---------------------------------------------------------------------------

object AppUpdateChecker {

    fun fetchConfig(onResult: (UpdateConfig?) -> Unit) {
        thread {
            try {
                val jsonStr =
                    URL("https://raw.githubusercontent.com/AhmadMorningstar/Islam/main/update_config.json")
                        .readText()
                val obj = JSONObject(jsonStr)
                val regions = mutableListOf<String>()
                if (obj.has("regions_optional")) {
                    val arr = obj.getJSONArray("regions_optional")
                    for (i in 0 until arr.length()) regions.add(arr.getString(i))
                }
                onResult(
                    UpdateConfig(
                        min_version = obj.getLong("min_version"),
                        latest_version = obj.getLong("latest_version"),
                        force_after = obj.getString("force_after"),
                        message = obj.getString("message"),
                        regions_optional = regions
                    )
                )
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    fun isForceExpired(forceAfter: String): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a VV")
                val deadline = java.time.ZonedDateTime.parse(forceAfter, formatter)
                java.time.ZonedDateTime.now()
                    .isAfter(deadline.withZoneSameInstant(java.time.ZoneId.systemDefault()))
            } else {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.US)
                val deadline = sdf.parse(forceAfter.substringBeforeLast(" "))
                java.util.Date().after(deadline)
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getCountryCode(): String = Locale.getDefault().country.uppercase(Locale.US)
}

// ---------------------------------------------------------------------------
// UPDATE DIALOGS
// ---------------------------------------------------------------------------

fun showForceUpdateDialog(context: Context, message: String) {
    androidx.appcompat.app.AlertDialog.Builder(context)
        .setTitle("Update Required")
        .setMessage(message)
        .setCancelable(false)
        .setPositiveButton("Update") { _, _ ->
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
            )
            if (context is Activity) context.finishAffinity()
            Runtime.getRuntime().exit(0)
        }
        .setOnDismissListener {
            if (context is Activity) {
                context.finishAffinity()
                Runtime.getRuntime().exit(0)
            }
        }
        .show()
}

fun showOptionalUpdateDialog(context: Context, message: String) {
    androidx.appcompat.app.AlertDialog.Builder(context)
        .setTitle("Update Available")
        .setMessage(message)
        .setCancelable(true)
        .setPositiveButton("Update") { _, _ ->
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
            )
        }
        .setNegativeButton("Later", null)
        .show()
}