package com.example.telecom

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import com.google.firebase.initialize
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Configures Firebase App Check for Firebase AI Logic (`Firebase.ai`) using either the
 * DebugAppCheckProviderFactory (when launched in the AI Studio emulator with `app_check_debug_token`)
 * or RecaptchaAppCheckProviderFactory (`R.string.recaptcha_site_key`).
 *
 * Guarded with an [AtomicBoolean] and defensive error handling so calling it from both
 * [com.example.MainActivity] and [InCallActivity] is idempotent and safe in unit tests.
 */
object FirebaseAppCheckHelper {
    private const val TAG = "FirebaseAppCheckHelper"
    private val isConfigured = AtomicBoolean(false)

    fun configureAppCheck(context: Context, intent: Intent?) {
        val token = intent?.getStringExtra("app_check_debug_token")
        if (!token.isNullOrEmpty()) {
            val prefs = context.getSharedPreferences("app_check", Context.MODE_PRIVATE)
            prefs.edit().putString("debug_token", token).apply()
        }

        if (!isConfigured.compareAndSet(false, true)) {
            return
        }

        try {
            val appContext = context.applicationContext
            Firebase.initialize(context = appContext)

            val prefs = appContext.getSharedPreferences("app_check", Context.MODE_PRIVATE)
            val debugToken = prefs.getString("debug_token", null)

            if (!debugToken.isNullOrEmpty()) {
                try {
                    val factory = DebugAppCheckProviderFactory.getInstance()
                    val f = factory.javaClass.getDeclaredField("debugSecret")
                    f.isAccessible = true
                    f.set(factory, debugToken)
                    Firebase.appCheck.installAppCheckProviderFactory(factory)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to install DebugAppCheckProviderFactory", e)
                }
            } else {
                val siteKey = appContext.getString(R.string.recaptcha_site_key)
                Firebase.appCheck.installAppCheckProviderFactory(
                    RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase / AppCheck initialization skipped or unavailable in current environment: ${t.message}")
        }
    }
}
