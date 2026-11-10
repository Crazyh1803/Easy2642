package com.easy2642.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit

/** Non-sensitive preferences only. No PII is ever written here. */
class Settings(private val context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var disclaimerAccepted: Boolean
        get() = prefs.getBoolean(KEY_DISCLAIMER, false)
        set(v) = prefs.edit { putBoolean(KEY_DISCLAIMER, v) }

    /** Tree URI of the user's designated claims folder, if they picked one. */
    var claimsFolder: Uri?
        get() = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)
        set(v) {
            val old = claimsFolder
            if (old != null && old != v) release(old)
            if (v != null) {
                context.contentResolver.takePersistableUriPermission(
                    v, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            prefs.edit { if (v == null) remove(KEY_FOLDER) else putString(KEY_FOLDER, v.toString()) }
        }

    private fun release(uri: Uri) = runCatching {
        context.contentResolver.releasePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    fun clear() {
        context.contentResolver.persistedUriPermissions.forEach { release(it.uri) }
        prefs.edit(commit = true) { clear() }
    }

    private companion object {
        const val KEY_DISCLAIMER = "disclaimer_accepted"
        const val KEY_FOLDER = "claims_folder"
    }
}
