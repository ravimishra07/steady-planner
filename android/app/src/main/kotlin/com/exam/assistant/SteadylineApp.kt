package com.exam.assistant

import android.app.Application
import androidx.work.Configuration

/**
 * Constructs the container and nothing else.
 *
 * Everything here is on the cold-start critical path, so the budget is under
 * 20ms. AppContainer's fields are lazy, so this is a single allocation.
 */
class SteadylineApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.runMigrationInBackground()
    }

    /**
     * Glance renders widgets through WorkManager. Its startup initializer is
     * removed in the manifest so it is built on first use — by a widget update —
     * and never on the cold-start path.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
