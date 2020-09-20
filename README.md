# aicactus-sdk-android
mobile tag for android platforms



```
        // Create an client with the given context and  write key.
        AicactusSDK config = new AicactusSDK.Builder(this, AICACTUS_WRITE_KEY)
                // Enable this to record certain application events automatically!
                .withStagingServer()
                .trackApplicationLifecycleEvents()
                // Enable this to record screen views automatically!
                .recordScreenViews()
                .build();

        // Set the initialized instance as a globally accessible instance.
        AicactusSDK.setup(config);

```
