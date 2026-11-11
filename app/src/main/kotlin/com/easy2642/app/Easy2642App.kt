package com.easy2642.app

import android.app.Application
import android.content.Context
import android.content.Intent
import com.easy2642.app.data.Repository
import com.easy2642.app.data.SecureDatabase
import com.easy2642.app.data.Settings
import com.easy2642.app.pdf.PacketBuilder
import com.easy2642.app.pdf.PdfFormService
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File

class Easy2642App : Application() {
    val settings by lazy { Settings(this) }
    private val secureRepository by lazy { Repository(SecureDatabase.open(this)) }

    /** Tests substitute an unencrypted in-memory repository; SQLCipher cannot load on a desktop JVM. */
    @Volatile
    var repositoryOverride: Repository? = null

    val repository: Repository get() = repositoryOverride ?: secureRepository
    val formService by lazy { PdfFormService { assets.open(TEMPLATE_ASSET) } }
    val packetBuilder = PacketBuilder()

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        // Working copies of packets and attachments never outlive a session.
        WorkFiles.purge(this)
    }

    /** Erases every table, the encryption key, preferences and cached files, then restarts. */
    fun clearAllLocalData() {
        runCatching { repository.close() }
        SecureDatabase.deleteFiles(this)
        settings.clear()
        WorkFiles.purge(this)
        cacheDir.deleteRecursively()
        val restart = Intent.makeRestartActivityTask(
            packageManager.getLaunchIntentForPackage(packageName)!!.component,
        )
        startActivity(restart)
        Runtime.getRuntime().exit(0)
    }

    companion object {
        const val TEMPLATE_ASSET = "forms/dd2642_template.pdf"
    }
}

/** Private cache locations for in-progress claim files. */
object WorkFiles {
    fun packets(context: Context) = File(context.cacheDir, "packets").apply { mkdirs() }
    fun attachments(context: Context) = File(context.cacheDir, "attachments").apply { mkdirs() }

    fun purge(context: Context) {
        File(context.cacheDir, "packets").deleteRecursively()
        File(context.cacheDir, "attachments").deleteRecursively()
        // ML Kit's scanner hands back files under the app cache too.
        File(context.cacheDir, "mlkit_docscan_ui_client").deleteRecursively()
    }
}
