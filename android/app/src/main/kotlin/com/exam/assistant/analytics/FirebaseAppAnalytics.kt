package com.exam.assistant.analytics

import android.content.Context
import android.os.Bundle
import com.exam.assistant.core.common.Analytics
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Firebase Analytics + Crashlytics behind [Analytics] (PRODUCTION_V1_PLAN §11.3).
 *
 * Built only when FirebaseApp is initialised (google-services.json present at
 * build time); otherwise AppContainer uses [Analytics.NoOp]. Every call is
 * guarded so a Firebase failure can never take the app down. Advertising ID,
 * SSAID and ad-personalisation signals are switched off in the manifest.
 */
class FirebaseAppAnalytics(private val context: Context) : Analytics {

    private val firebase: FirebaseAnalytics by lazy { FirebaseAnalytics.getInstance(context) }

    override fun log(event: String, params: Map<String, Any>) {
        runCatching { firebase.logEvent(event, params.toBundle()) }
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        runCatching { firebase.setAnalyticsCollectionEnabled(enabled) }
        runCatching { FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = enabled }
    }

    companion object {
        /** Firebase when it is configured, otherwise a no-op. Never throws. */
        fun createOrNoOp(context: Context): Analytics =
            if (runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)) {
                FirebaseAppAnalytics(context.applicationContext)
            } else {
                Analytics.NoOp
            }
    }
}

private fun Map<String, Any>.toBundle(): Bundle = Bundle().also { bundle ->
    forEach { (key, value) ->
        when (value) {
            is String -> bundle.putString(key, value)
            is Int -> bundle.putLong(key, value.toLong())
            is Long -> bundle.putLong(key, value)
            is Double -> bundle.putDouble(key, value)
            is Float -> bundle.putDouble(key, value.toDouble())
            // Firebase params are String, Long or Double; flags go as 0/1.
            is Boolean -> bundle.putLong(key, if (value) 1L else 0L)
            else -> bundle.putString(key, value.toString())
        }
    }
}
