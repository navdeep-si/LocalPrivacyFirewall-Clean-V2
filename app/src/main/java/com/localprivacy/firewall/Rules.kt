package com.localprivacy.firewall

import android.content.Context

/** All configuration is stored locally; no analytics or remote endpoints. */
object Rules {
    private const val FILE = "isolation_rules"
    private const val SELECTED = "selected_package_names"
    private const val DESIRED = "desired_running"
    private const val ERROR = "last_error"

    fun selected(context: Context): Set<String> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getStringSet(SELECTED, emptySet())
            .orEmpty()
            .toSet()

    fun setSelected(context: Context, packages: Set<String>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putStringSet(SELECTED, packages.toSet())
            .apply()
    }

    fun lastError(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(ERROR, "").orEmpty()

    fun setLastError(context: Context, message: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(ERROR, message)
            .apply()
    }

    fun desiredRunning(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(DESIRED, false)

    fun setDesiredRunning(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(DESIRED, enabled)
            .apply()
    }
}
