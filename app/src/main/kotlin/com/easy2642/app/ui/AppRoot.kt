package com.easy2642.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.easy2642.core.Oktoberfest
import java.time.LocalDate
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.easy2642.app.BuildConfig
import com.easy2642.app.Easy2642App
import com.easy2642.app.R
import com.easy2642.app.data.SecureDatabase

/** Set to false to remove the yearly tip reminder (the Settings link stays). */
private const val TIP_REMINDER_ENABLED = true

private object Routes {
    const val CLAIMS = "claims"
    const val FAMILY = "family"
    const val SETTINGS = "settings"
    const val NEW_CLAIM = "new"
    const val HARNESS = "harness"
}

@Composable
fun AppRoot(app: Easy2642App) {
    var accepted by remember { mutableStateOf(app.settings.disclaimerAccepted) }
    var resetNotice by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Shown once a year during Oktoberfest, never in the session where the first-run notice appears.
    var tipPrompt by remember {
        mutableStateOf(
            TIP_REMINDER_ENABLED && app.settings.disclaimerAccepted &&
                Oktoberfest.shouldPrompt(LocalDate.now(), app.settings.lastTipPromptYear),
        )
    }
    val nav = rememberNavController()
    LaunchedEffect(Unit) {
        // Opening the database up front surfaces a key-loss reset right away.
        withContext(Dispatchers.IO) { app.repository }
        resetNotice = SecureDatabase.wasReset
    }
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    val topLevel = listOf(
        Triple(Routes.CLAIMS, "Claims", Icons.AutoMirrored.Filled.ListAlt),
        Triple(Routes.FAMILY, "Family", Icons.Filled.Groups),
        Triple(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                // The disclaimer is visible on every screen of the app.
                val showBar = topLevel.any { it.first == current }
                DisclaimerFooter(if (showBar) Modifier else Modifier.navigationBarsPadding())
                if (showBar) {
                    NavigationBar {
                        topLevel.forEach { (route, label, icon) ->
                            NavigationBarItem(
                                selected = current == route,
                                onClick = {
                                    nav.navigate(route) {
                                        popUpTo(Routes.CLAIMS) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(icon, contentDescription = null) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.CLAIMS, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable(Routes.CLAIMS) { DashboardScreen(app, onNewClaim = { nav.navigate(Routes.NEW_CLAIM) }) }
            composable(Routes.FAMILY) { FamilyScreen(app) }
            composable(Routes.SETTINGS) {
                SettingsScreen(app, onOpenHarness = if (BuildConfig.DEBUG) ({ nav.navigate(Routes.HARNESS) }) else null)
            }
            composable(Routes.NEW_CLAIM) {
                NewClaimScreen(app, onClose = { nav.popBackStack() }, onOpenFamily = {
                    nav.popBackStack()
                    nav.navigate(Routes.FAMILY)
                })
            }
            if (BuildConfig.DEBUG) composable(Routes.HARNESS) { HarnessScreen(app, onClose = { nav.popBackStack() }) }
        }
    }

    if (!accepted) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Before you start") },
            text = {
                Column {
                    Text(stringResource(R.string.disclaimer))
                    Text("\n" + stringResource(R.string.privacy_summary))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    app.settings.disclaimerAccepted = true
                    accepted = true
                }) { Text("I understand") }
            },
        )
    }
    if (tipPrompt) {
        val dismiss = {
            app.settings.lastTipPromptYear = LocalDate.now().year
            tipPrompt = false
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("O'zapft is!") },
            text = { Text("Oktoberfest has opened. If 2642 Easy has saved you some paperwork this year, you can buy the developer a beer. Entirely optional; you'll see this at most once a year.") },
            confirmButton = {
                TextButton(onClick = {
                    dismiss()
                    openUrl(context, Links.TIP)
                }) { Text("Leave a tip") }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text("Not now") } },
        )
    }
    if (resetNotice) {
        AlertDialog(
            onDismissRequest = { resetNotice = false },
            title = { Text("Saved data could not be unlocked") },
            text = { Text("This device's encryption key for 2642 Easy is no longer available, so previously saved profiles and claims could not be decrypted and were removed. You can restore them from a backup file in Settings.") },
            confirmButton = { TextButton(onClick = { resetNotice = false }) { Text("OK") } },
        )
    }
}
