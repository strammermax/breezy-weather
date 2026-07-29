package com.liveweatherwallpaperapp.domain.settings

import android.content.Context
import androidx.core.content.edit

/**
 * Tracks whether the first-run setup wizard (location(s), weather sources, live wallpaper,
 * main screen, widget -- see SetupWizardActivity) has been completed, so it's only shown
 * proactively once. Re-run manually from Settings -> "Redo setup wizard" doesn't touch this
 * flag; it just starts the wizard again regardless.
 */
class SetupWizardStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    var completed: Boolean
        get() = preferences.getBoolean(KEY_COMPLETED, false)
        set(value) {
            preferences.edit { putBoolean(KEY_COMPLETED, value) }
        }

    companion object {
        private const val PREFERENCES = "setup_wizard"
        private const val KEY_COMPLETED = "completed"
    }
}
