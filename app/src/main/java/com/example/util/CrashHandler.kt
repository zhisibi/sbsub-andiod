package com.example.util

import android.content.Context
import android.content.SharedPreferences
import java.io.PrintWriter
import java.io.StringWriter

object CrashHandler {
    private const val PREF_NAME = "app_crash_prefs"
    private const val KEY_CRASH_LOG = "last_crash_log"

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTraceString = sw.toString()

                val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_CRASH_LOG, stackTraceString).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Call default handler to terminate app properly
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun getCrashLog(context: Context): String? {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CRASH_LOG, null)
    }

    fun clearCrashLog(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CRASH_LOG).apply()
    }
}
