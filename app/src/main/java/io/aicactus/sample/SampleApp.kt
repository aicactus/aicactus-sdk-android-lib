package io.aicactus.sample

import android.app.Application
import io.aicactus.sdk.AicactusSDK

class SampleApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Create an client with the given context and  write key.
        val config = AicactusSDK.Builder(this, AICACTUS_WRITE_KEY)
            // Enable this to record certain application events automatically!
            .trackApplicationLifecycleEvents()
            // Enable this to record screen views automatically!
            .recordScreenViews()
            .logLevel(AicactusSDK.LogLevel.VERBOSE)
            .build()

        // Set the initialized instance as a globally accessible instance.
        AicactusSDK.setup(config)
    }

    companion object {
        private const val AICACTUS_WRITE_KEY = "d152290b-ba08-4a15-a66e-fce748116a0f"
    }
}
