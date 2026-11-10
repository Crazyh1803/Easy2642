package com.easy2642.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.easy2642.core.OutputMode

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

    /** Default for new claims: one combined PDF, or form / bill / receipt as separate files. */
    var outputMode: OutputMode
        get() = runCatching { OutputMode.valueOf(prefs.getString(KEY_OUTPUT, null)!!) }.getOrDefault(OutputMode.COMBINED)
        set(v) = prefs.edit { putString(KEY_OUTPUT, v.name) }

    /** Year the Oktoberfest tip reminder was last shown; 0 if never. */
    var lastTipPromptYear: Int
        get() = prefs.getInt(KEY_TIP_YEAR, 0)
        set(v) = prefs.edit { putInt(KEY_TIP_YEAR, v) }

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
        const val KEY_OUTPUT = "output_mode"
        const val KEY_TIP_YEAR = "tip_prompt_year"
    }
}
