package com.AhmadMorningstar.islam.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object SignatureVerifier {
    private const val RELEASE_SIGNATURE_SHA256 =
        "REPLACE_WITH_YOUR_RELEASE_SHA256"

    init {
        require(
            RELEASE_SIGNATURE_SHA256 != "REPLACE_WITH_YOUR_RELEASE_SHA256"
        ) {
            "Release SHA-256 not configured"
        }
    }


    fun isSignatureValid(context: Context): Boolean {
        return try {
            val signatures: Array<android.content.pm.Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                info.signingInfo?.apkContentsSigners  // safe call
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                ).signatures
            }

            val md = MessageDigest.getInstance("SHA-256")

            return signatures?.any { sig ->
                val hash = md.digest(sig.toByteArray())
                    .joinToString("") { "%02X".format(it) }
                hash == RELEASE_SIGNATURE_SHA256
            } ?: false
        }
        catch (e: Exception) {
            false
        }
    }
}
