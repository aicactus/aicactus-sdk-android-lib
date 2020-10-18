# AicactusSDK ANDROID

AicactusSDK is an ANDROID client for AiCactus platform.

Analytics helps you measure your users, product, and business. It unlocks insights into your app's funnel, core business metrics, and whether you have product-market fit.


## Installation

AicactusSDK is available through maven central

### Modify build.gradle at the root project in this way:

```gradle
allprojects {
        repositories {
            mavenCentral()
            maven {
                url 'https://oss.sonatype.org/content/groups/public'
            }
}
```

then in the build.gradle (at the module level) you add the new dependency:

```gradle
implementation 'io.aicactus.sdk:aicactusSDK:1.0.0'
```

## Quickstart

In your MainApplication or MainActivity, `onCreate` method , set up the SDK like so:

```kotlin

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
    
```

And of course, import the SDK in the files that you use it by adding the following line:

```kotlin
import io.aicactus.sdk.AicactusSDK
```

## Identify Users

The identify method is how you tell who the current user is. It takes a unique User ID, and any optional traits you know about them. You can read more about it in the identify reference.

Here’s what a basic call to identify might look like:

```kotlin
    AicactusSDK.shared().identify("f4ca124298", Traits().putName("Jack London")
                                                        .putEmail("jack@aicactus.ai")
                                                        .putPhone("555-444-3333"), null)
```

Or simply just id:

```kotlin
    AicactusSDK.shared().identify("f4ca124298")
```

That call identifies Jack by his unique User ID (f4ca124298, which is the one you know him by in your database) and labels him with name and email, phone traits.

Once you’ve added an identify call, you’re ready to move on to tracking!

## Track Actions

To get started, AicactusSDK can automatically track a few important common events, such as Application Installed, Application Updated and Application Opened. You can enable this option during initialization by adding the following lines.

```kotlin
       .trackApplicationLifecycleEvents()
```

You should also track events that indicate success in your mobile app, like Signed Up, Item Purchased or Article Bookmarked. We recommend tracking just a few important events. You can always add more later!

Here’s what a track call might look like when a user signs up:

```kotlin
    AicactusSDK.with(this).screen("Signup")
    // Button click
    AicactusSDK.with(this).track("Button B Clicked", Properties().putTitle("B").putPrice(10.0))
    // Alias new user id with anonymous id
    AicactusSDK.with(this).alias("AA-BB-CC-NEW-ID")
    // Start trial
    AicactusSDK.with(this).track("Trial Started", Properties().put...
```

Once you’ve added a few track calls, you’re set up!



## Flushing
By default, AicactusSDK sends (“flushes”) events from the ANDROID library in batches of 20, however this is configurable. You can set the flush value to change the batch siz

```Note: Disable batching is not recommended. This increases battery use.```


```kotlin
val config = AicactusSDK.Builder(this, AICACTUS_WRITE_KEY)
    // Enable this to record certain application events automatically!
    .trackApplicationLifecycleEvents()
    .flushQueueSize(10)
    .flushInterval(10, TimeUnit.SECONDS)
    // Enable this to record screen views automatically!
    .recordScreenViews()
    .logLevel(AicactusSDK.LogLevel.VERBOSE)
    .build()
```

```kotlin
AicactusSDK.with(this).flush()
```


