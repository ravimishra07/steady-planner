package com.exam.assistant

import android.app.Application

/**
 * Constructs the container and nothing else.
 *
 * Everything here is on the cold-start critical path, so the budget is under
 * 20ms. AppContainer's fields are lazy, so this is a single allocation.
 */
class SteadylineApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.runMigrationInBackground()
    }
}
