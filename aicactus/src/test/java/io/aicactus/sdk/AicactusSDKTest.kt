/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Segment.io, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.aicactus.sdk

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.ComponentName
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.os.Bundle
import androidx.annotation.Nullable
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.nhaarman.mockitokotlin2.doNothing
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.aicactus.sdk.ProjectSettings.create
import io.aicactus.sdk.TestUtils.NoDescriptionMatcher
import io.aicactus.sdk.TestUtils.grantPermission
import io.aicactus.sdk.TestUtils.mockApplication
import io.aicactus.sdk.integrations.AliasPayload
import io.aicactus.sdk.integrations.GroupPayload
import io.aicactus.sdk.integrations.IdentifyPayload
import io.aicactus.sdk.integrations.Integration
import io.aicactus.sdk.integrations.Logger
import io.aicactus.sdk.integrations.ScreenPayload
import io.aicactus.sdk.integrations.TrackPayload
import io.aicactus.sdk.internal.Utils.AnalyticsNetworkExecutorService
import io.aicactus.sdk.internal.Utils.DEFAULT_FLUSH_INTERVAL
import io.aicactus.sdk.internal.Utils.DEFAULT_FLUSH_QUEUE_SIZE
import io.aicactus.sdk.internal.Utils.isNullOrEmpty
import java.io.IOException
import java.lang.Boolean.TRUE
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.Throws
import org.assertj.core.api.Assertions
import org.assertj.core.data.MapEntry
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.Spy
import org.mockito.hamcrest.MockitoHamcrest.argThat
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
open class AicactusSDKTest {
    private val SETTINGS =
        """
            |{
            |  "integrations": {
            |    "test": { 
            |      "foo": "bar"
            |    }
            |  },
            | "plan": {
            |
            |  }
            |}
            """.trimMargin()

    @Mock
    private lateinit var traitsCache: Traits.Cache
    @Spy
    private lateinit var networkExecutor: AnalyticsNetworkExecutorService

    @Spy
    private var analyticsExecutor: ExecutorService = TestUtils.SynchronousExecutor()

    @Mock
    private lateinit var client: Client

    @Mock
    private lateinit var stats: Stats

    @Mock
    private lateinit var projectSettingsCache: ProjectSettings.Cache

    @Mock
    private lateinit var integration: Integration<*>

    @Mock
    lateinit var lifecycle: Lifecycle
    private lateinit var defaultOptions: Options
    private lateinit var factory: Integration.Factory
    private lateinit var optOut: BooleanPreference
    private lateinit var application: Application
    private lateinit var traits: Traits
    private lateinit var analyticsContext: AnalyticsContext
    private lateinit var aicactusSDK: AicactusSDK
    @Mock
    private lateinit var jsMiddleware: JSMiddleware

    @Before
    @Throws(IOException::class, NameNotFoundException::class)
    fun setUp() {
        AicactusSDK.INSTANCES.clear()

        MockitoAnnotations.initMocks(this)
        defaultOptions = Options()
        application = mockApplication()
        traits = Traits.create()
        whenever(traitsCache.get()).thenReturn(traits)

        val packageInfo = PackageInfo()
        packageInfo.versionCode = 100
        packageInfo.versionName = "1.0.0"

        val packageManager = Mockito.mock(PackageManager::class.java)
        whenever(packageManager.getPackageInfo("com.foo", 0)).thenReturn(packageInfo)
        whenever(application.packageName).thenReturn("com.foo")
        whenever(application.packageManager).thenReturn(packageManager)

        analyticsContext = Utils.createContext(traits)
        factory = object : Integration.Factory {
            override fun create(settings: ValueMap, aicactusSDK: AicactusSDK): Integration<*>? {
                return integration
            }

            override fun key(): String {
                return "test"
            }
        }
        whenever(projectSettingsCache.get())
            .thenReturn(create(Cartographer.INSTANCE.fromJson(SETTINGS)))

        val sharedPreferences =
            RuntimeEnvironment.application
                .getSharedPreferences("aicactusSDK-test-qaz", MODE_PRIVATE)
        optOut = BooleanPreference(sharedPreferences, "opt-out-test", false)

        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(AicactusSDK.LogLevel.VERBOSE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                false,
                CountDownLatch(0),
                false,
                false, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                ValueMap(),
                lifecycle,
                false
        )

        // Used by singleton tests.
        grantPermission(RuntimeEnvironment.application, android.Manifest.permission.INTERNET)
    }

    @After
    fun tearDown() {
        RuntimeEnvironment.application
            .getSharedPreferences("aicactusSDK-android-qaz", MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun invalidIdentity() {
        try {
            aicactusSDK.identify(null, null, null)
        } catch (e: IllegalArgumentException) {
            Assertions.assertThat(e)
                .hasMessage("Either userId or some traits must be provided.")
        }
    }

    @Test
    fun identify() {
        aicactusSDK.identify("prateek", Traits().putUsername("f2prateek"), null)

        verify(integration)
            .identify(
                argThat<IdentifyPayload>(
                    object : TestUtils.NoDescriptionMatcher<IdentifyPayload>() {
                        override fun matchesSafely(item: IdentifyPayload): Boolean {
                            return item.userId() == "prateek" &&
                                item.traits().username() == "f2prateek"
                        }
                    })
            )
    }

    @Test
    fun identifyUpdatesCache() {
        aicactusSDK.identify("foo", Traits().putValue("bar", "qaz"), null)

        Assertions.assertThat(traits).contains(MapEntry.entry("userId", "foo"))
        Assertions.assertThat(traits).contains(MapEntry.entry("bar", "qaz"))
        Assertions.assertThat(analyticsContext.traits()).contains(MapEntry.entry("userId", "foo"))
        Assertions.assertThat(analyticsContext.traits()).contains(MapEntry.entry("bar", "qaz"))
        verify(traitsCache).set(traits)
        verify(integration)
            .identify(
                argThat<IdentifyPayload>(
                    object : NoDescriptionMatcher<IdentifyPayload>() {
                        override fun matchesSafely(item: IdentifyPayload): Boolean {
                            // Exercises a bug where payloads didn't pick up userId in identify correctly.
                            // https://github.com/segmentio/analytics-android/issues/169
                            return item.userId() == "foo"
                        }
                    })
            )
    }

    @Test
    @Nullable
    fun invalidGroup() {
        try {
            aicactusSDK.group("")
            Assertions.fail("empty groupId and name should throw exception")
        } catch (expected: IllegalArgumentException) {
            Assertions.assertThat(expected).hasMessage("groupId must not be null or empty.")
        }
    }

    @Test
    fun group() {
        aicactusSDK.group("segment", Traits().putEmployees(42), null)

        verify(integration)
            .group(
                argThat<GroupPayload>(
                    object : NoDescriptionMatcher<GroupPayload>() {
                        override fun matchesSafely(item: GroupPayload): Boolean {
                            return item.groupId() == "segment" &&
                                item.traits().employees() == 42L
                        }
                    })
            )
    }

    @Test
    fun invalidTrack() {
        try {
            aicactusSDK.track(null.toString())
        } catch (e: IllegalArgumentException) {
            Assertions.assertThat(e).hasMessage("event must not be null or empty.")
        }
        try {
            aicactusSDK.track("   ")
        } catch (e: IllegalArgumentException) {
            Assertions.assertThat(e).hasMessage("event must not be null or empty.")
        }
    }

    @Test
    fun track() {
        aicactusSDK.track("wrote tests", Properties().putUrl("github.com"))
        verify(integration)
            .track(
                argThat<TrackPayload>(
                    object : NoDescriptionMatcher<TrackPayload>() {
                        override fun matchesSafely(payload: TrackPayload): Boolean {
                            return payload.event() == "wrote tests" &&
                                payload.properties().url() == "github.com"
                        }
                    })
            )
    }

    @Test
    @Throws(IOException::class)
    fun invalidScreen() {
        try {
            aicactusSDK.screen(null, null as String?)
            Assertions.fail("null category and name should throw exception")
        } catch (expected: IllegalArgumentException) {
            Assertions.assertThat(expected).hasMessage("either category or name must be provided.")
        }

        try {
            aicactusSDK.screen("", "")
            Assertions.fail("empty category and name should throw exception")
        } catch (expected: IllegalArgumentException) {
            Assertions.assertThat(expected).hasMessage("either category or name must be provided.")
        }
    }

    @Test
    fun screen() {
        aicactusSDK.screen("android", "saw tests", Properties().putUrl("github.com"))
        verify(integration)
            .screen(
                argThat<ScreenPayload>(
                    object : NoDescriptionMatcher<ScreenPayload>() {
                        override fun matchesSafely(payload: ScreenPayload): Boolean {
                            return payload.name() == "saw tests" &&
                                payload.category() == "android" &&
                                payload.properties().url() == "github.com"
                        }
                    })
            )
    }

    @Test
    fun optionsDisableIntegrations() {
        aicactusSDK.screen("foo", "bar", null, Options().setIntegration("test", false))
        aicactusSDK.track("foo", null, Options().setIntegration("test", false))
        aicactusSDK.group("foo", null, Options().setIntegration("test", false))
        aicactusSDK.identify("foo", null, Options().setIntegration("test", false))
        aicactusSDK.alias("foo", Options().setIntegration("test", false))

        aicactusSDK.screen(
            "foo", "bar", null, Options().setIntegration(Options.ALL_INTEGRATIONS_KEY, false)
        )
        aicactusSDK.track("foo", null, Options().setIntegration(Options.ALL_INTEGRATIONS_KEY, false))
        aicactusSDK.group("foo", null, Options().setIntegration(Options.ALL_INTEGRATIONS_KEY, false))
        aicactusSDK.identify(
            "foo", null, Options().setIntegration(Options.ALL_INTEGRATIONS_KEY, false)
        )
        aicactusSDK.alias("foo", Options().setIntegration(Options.ALL_INTEGRATIONS_KEY, false))

        verifyNoMoreInteractions(integration)
    }

    @Test
    fun optionsCustomContext() {
        aicactusSDK.track("foo", null, Options().putContext("from_tests", true))

        verify(integration)
            .track(
                argThat<TrackPayload>(
                    object : NoDescriptionMatcher<TrackPayload>() {
                        override fun matchesSafely(payload: TrackPayload): Boolean {
                            return payload.context()["from_tests"] == TRUE
                        }
                    })
            )
    }

    @Test
    @Throws(IOException::class)
    fun optOutDisablesEvents() {
        aicactusSDK.optOut(true)
        aicactusSDK.track("foo")
        verifyNoMoreInteractions(integration)
    }

    @Test
    @Throws(IOException::class)
    fun emptyTrackingPlan() {
        aicactusSDK.projectSettings = create(
            Cartographer.INSTANCE.fromJson(
                """
                                      |{
                                      |  "integrations": {
                                      |    "test": {
                                      |      "foo": "bar"
                                      |    }
                                      |  },
                                      |  "plan": {
                                      |  }
                                      |}
                                      """.trimMargin()
            )
        )

        aicactusSDK.track("foo")
        verify(integration)
            .track(
                argThat<TrackPayload>(
                    object : NoDescriptionMatcher<TrackPayload>() {
                        override fun matchesSafely(payload: TrackPayload): Boolean {
                            return payload.event() == "foo"
                        }
                    })
            )
        verifyNoMoreInteractions(integration)
    }

    @Test
    @Throws(IOException::class)
    fun emptyEventPlan() {
        aicactusSDK.projectSettings = create(
            Cartographer.INSTANCE.fromJson(
                """
                              |{
                              |  "integrations": {
                              |    "test": {
                              |      "foo": "bar"
                              |    }
                              |  },
                              |  "plan": {
                              |    "track": {
                              |    }
                              |  }
                              |}
                              """.trimMargin()
            )
        )
        aicactusSDK.track("foo")
        verify(integration)
            .track(
                argThat<TrackPayload>(
                    object : NoDescriptionMatcher<TrackPayload>() {
                        override fun matchesSafely(payload: TrackPayload): Boolean {
                            return payload.event() == "foo"
                        }
                    })
            )
        verifyNoMoreInteractions(integration)
    }

    @Test
    @Throws(IOException::class)
    fun trackingPlanDisablesEvent() {
        aicactusSDK.projectSettings = create(
            Cartographer.INSTANCE.fromJson(
                """
                              |{
                              |  "integrations": {
                              |    "test": {
                              |      "foo": "bar"
                              |    }
                              |  },
                              |  "plan": {
                              |    "track": {
                              |      "foo": {
                              |        "enabled": false
                              |      }
                              |    }
                              |  }
                              |}
                              """.trimMargin()
            )
        )
        aicactusSDK.track("foo")
        verifyNoMoreInteractions(integration)
    }

    @Test
    @Throws(IOException::class)
    fun trackingPlanDisablesEventForSingleIntegration() {
        aicactusSDK.projectSettings = create(
            Cartographer.INSTANCE.fromJson(
                """
                              |{
                              |  "integrations": {
                              |    "test": {
                              |      "foo": "bar"
                              |    }
                              |  },
                              |  "plan": {
                              |    "track": {
                              |      "foo": {
                              |        "enabled": true,
                              |        "integrations": {
                              |          "test": false
                              |        }
                              |      }
                              |    }
                              |  }
                              |}
                              """.trimMargin()
            )
        )
        aicactusSDK.track("foo")
        verifyNoMoreInteractions(integration)
    }

    @Test
    @Throws(IOException::class)
    fun trackingPlanDisabledEventCannotBeOverriddenByOptions() {
        aicactusSDK.projectSettings = create(
            Cartographer.INSTANCE.fromJson(
                """
                              |{
                              |  "integrations": {
                              |    "test": {
                              |      "foo": "bar"
                              |    }
                              |  },
                              |  "plan": {
                              |    "track": {
                              |      "foo": {
                              |        "enabled": false
                              |      }
                              |    }
                              |  }
                              |}
                              """.trimMargin()
            )
        )
        aicactusSDK.track("foo", null, Options().setIntegration("test", true))
        verifyNoMoreInteractions(integration)
    }

    @Test
    @Throws(IOException::class)
    fun trackingPlanDisabledEventForIntegrationOverriddenByOptions() {
        aicactusSDK.projectSettings = create(
            Cartographer.INSTANCE.fromJson(
                """
                              |{
                              |  "integrations": {
                              |    "test": {
                              |      "foo": "bar"
                              |    }
                              |  },
                              |  "plan": {
                              |    "track": {
                              |      "foo": {
                              |        "enabled": true,
                              |        "integrations": {
                              |          "test": false
                              |        }
                              |      }
                              |    }
                              |  }
                              |}
                              """.trimMargin()
            )
        )
        aicactusSDK.track("foo", null, Options().setIntegration("test", true))
        verify(integration)
            .track(
                argThat<TrackPayload>(
                    object : NoDescriptionMatcher<TrackPayload>() {
                        override fun matchesSafely(payload: TrackPayload): Boolean {
                            return payload.event() == "foo"
                        }
                    })
            )
        verifyNoMoreInteractions(integration)
    }

    @Test
    @Throws(IOException::class)
    fun invalidAlias() {
        try {
            aicactusSDK.alias("")
            Assertions.fail("empty new id should throw error")
        } catch (expected: IllegalArgumentException) {
            Assertions.assertThat(expected).hasMessage("newId must not be null or empty.")
        }
    }

    @Test
    fun alias() {
        val anonymousId = traits.anonymousId()
        aicactusSDK.alias("foo")
        val payloadArgumentCaptor =
            ArgumentCaptor.forClass(AliasPayload::class.java)
        verify(integration).alias(payloadArgumentCaptor.capture())
        Assertions.assertThat(payloadArgumentCaptor.value).containsEntry("previousId", anonymousId)
        Assertions.assertThat(payloadArgumentCaptor.value).containsEntry("userId", "foo")
    }

    @Test
    fun aliasWithCachedUserID() {
        aicactusSDK.identify(
            "prayansh", Traits().putValue("bar", "qaz"), null
        ) // refer identifyUpdatesCache
        aicactusSDK.alias("foo")
        val payloadArgumentCaptor =
            ArgumentCaptor.forClass(AliasPayload::class.java)
        verify(integration).alias(payloadArgumentCaptor.capture())
        Assertions.assertThat(payloadArgumentCaptor.value).containsEntry("previousId", "prayansh")
        Assertions.assertThat(payloadArgumentCaptor.value).containsEntry("userId", "foo")
    }

    @Test
    fun flush() {
        aicactusSDK.flush()

        verify(integration).flush()
    }

    @Test
    fun reset() {
        aicactusSDK.reset()

        verify(integration).reset()
    }

    @Test
    @Throws(Exception::class)
    fun getSnapshot() {
        aicactusSDK.snapshot

        verify(stats).createSnapshot()
    }

    @Test
    fun logoutClearsTraitsAndUpdatesContext() {
        analyticsContext.setTraits(Traits().putAge(20).putAvatar("bar"))

        aicactusSDK.logout()

        verify(traitsCache).delete()
        verify(traitsCache)
            .set(
                argThat(
                    object : TypeSafeMatcher<Traits>() {
                        override fun matchesSafely(traits: Traits): Boolean {
                            return !isNullOrEmpty(traits.anonymousId())
                        }

                        override fun describeTo(description: Description) {}
                    })
            )
        Assertions.assertThat(analyticsContext.traits()).hasSize(1)
        Assertions.assertThat(analyticsContext.traits()).containsKey("anonymousId")
    }

    @Test
    fun onIntegrationReadyShouldFailForNullKey() {
        try {
            aicactusSDK.onIntegrationReady(null as String?, Mockito.mock(AicactusSDK.Callback::class.java))
            Assertions.fail("registering for null integration should fail")
        } catch (e: java.lang.IllegalArgumentException) {
            Assertions.assertThat(e).hasMessage("key cannot be null or empty.")
        }
    }

    @Test
    fun onIntegrationReady() {
        val callback: AicactusSDK.Callback<*> = Mockito.mock(AicactusSDK.Callback::class.java)
        aicactusSDK.onIntegrationReady("test", callback)
        verify(callback).onReady(null)
    }

    @Test
    fun shutdown() {
        Assertions.assertThat(aicactusSDK.shutdown).isFalse()
        aicactusSDK.shutdown()
        verify(application).unregisterActivityLifecycleCallbacks(aicactusSDK.activityLifecycleCallback)
        verify(stats).shutdown()
        verify(networkExecutor).shutdown()
        Assertions.assertThat(aicactusSDK.shutdown).isTrue()
        try {
            aicactusSDK.track("foo")
            Assertions.fail("Enqueuing a message after shutdown should throw.")
        } catch (e: IllegalStateException) {
            Assertions.assertThat(e).hasMessage("Cannot enqueue messages after client is shutdown.")
        }

        try {
            aicactusSDK.flush()
            Assertions.fail("Enqueuing a message after shutdown should throw.")
        } catch (e: IllegalStateException) {
            Assertions.assertThat(e).hasMessage("Cannot enqueue messages after client is shutdown.")
        }
    }

    @Test
    fun shutdownTwice() {
        Assertions.assertThat(aicactusSDK.shutdown).isFalse()
        aicactusSDK.shutdown()
        aicactusSDK.shutdown()
        verify(stats).shutdown()
        Assertions.assertThat(aicactusSDK.shutdown).isTrue()
    }

    @Test
    @Throws(Exception::class)
    fun shutdownDisallowedOnCustomSingletonInstance() {
        AicactusSDK.singleton = null
        try {
            val analytics = AicactusSDK.Builder(RuntimeEnvironment.application, "foo").build()
            AicactusSDK.setup(analytics)
            analytics.shutdown()
            Assertions.fail("Calling shutdown() on static singleton instance should throw")
        } catch (ignored: UnsupportedOperationException) {
        }
    }

    @Test
    fun setSingletonInstanceMayOnlyBeCalledOnce() {
        AicactusSDK.singleton = null

        val analytics = AicactusSDK.Builder(RuntimeEnvironment.application, "foo").build()
        AicactusSDK.setup(analytics)

        try {
            AicactusSDK.setup(analytics)
            Assertions.fail("Can't set singleton instance twice.")
        } catch (e: IllegalStateException) {
            Assertions.assertThat(e).hasMessage("Singleton instance already exists.")
        }
    }

    @Test
    fun setSingletonInstanceAfterWithFails() {
        AicactusSDK.singleton = null
        AicactusSDK.setup(AicactusSDK.Builder(RuntimeEnvironment.application, "foo").build())

        val analytics = AicactusSDK.Builder(RuntimeEnvironment.application, "bar").build()
        try {
            AicactusSDK.setup(analytics)
            Assertions.fail("Can't set singleton instance after with().")
        } catch (e: IllegalStateException) {
            Assertions.assertThat(e).hasMessage("Singleton instance already exists.")
        }
    }

    @Test
    fun setSingleInstanceReturnedFromWith() {
        AicactusSDK.singleton = null
        val analytics = AicactusSDK.Builder(RuntimeEnvironment.application, "foo").build()
        AicactusSDK.setup(analytics)
        Assertions.assertThat(AicactusSDK.with(RuntimeEnvironment.application)).isSameAs(analytics)
    }

    @Test
    @Throws(Exception::class)
    fun multipleInstancesWithSameTagThrows() {
        AicactusSDK.Builder(RuntimeEnvironment.application, "foo").build()
        try {
            AicactusSDK.Builder(RuntimeEnvironment.application, "bar").tag("foo").build()
            Assertions.fail("Creating client with duplicate should throw.")
        } catch (expected: IllegalStateException) {
            Assertions.assertThat(expected)
                .hasMessageContaining("Duplicate aicactusSDK client created with tag: foo.")
        }
    }

    @Test
    @Throws(Exception::class)
    fun multipleInstancesWithSameTagIsAllowedAfterShutdown() {
        AicactusSDK.Builder(RuntimeEnvironment.application, "foo").build().shutdown()
        AicactusSDK.Builder(RuntimeEnvironment.application, "bar").tag("foo").build()
    }

    @Test
    @Throws(Exception::class)
    fun getSnapshotInvokesStats() {
        aicactusSDK.snapshot
        verify(stats).createSnapshot()
    }

    @Test
    @Throws(Exception::class)
    fun invalidURlsThrowAndNotCrash() {
        val connection = ConnectionFactory()

        try {
            connection.openConnection("SOME_BUSTED_URL")
            Assertions.fail("openConnection did not throw when supplied an invalid URL as expected.")
        } catch (expected: IOException) {
            Assertions.assertThat(expected).hasMessageContaining("Attempted to use malformed url")
            Assertions.assertThat(expected).isInstanceOf(IOException::class.java)
        }
    }

    @Test
    @Throws(NameNotFoundException::class)
    fun trackApplicationLifecycleEventsInstalled() {
        AicactusSDK.INSTANCES.clear()
        val callback = AtomicReference<DefaultLifecycleObserver>()
        doNothing()
            .whenever(lifecycle)
            .addObserver(
                argThat<LifecycleObserver>(
                    object : NoDescriptionMatcher<LifecycleObserver>() {
                        override fun matchesSafely(item: LifecycleObserver): Boolean {
                            callback.set(item as DefaultLifecycleObserver)
                            return true
                        }
                    })
            )

        val mockLifecycleOwner = Mockito.mock(LifecycleOwner::class.java)

        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(AicactusSDK.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                true,
                CountDownLatch(0),
                false,
                false, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                ValueMap(),
                lifecycle,
                false
        )

        callback.get().onCreate(mockLifecycleOwner)

        verify(integration)
            .track(
                argThat<TrackPayload>(
                    object : NoDescriptionMatcher<TrackPayload>() {
                        override fun matchesSafely(payload: TrackPayload): Boolean {
                            return payload.event() ==
                                "Application Installed" &&
                                payload.properties()
                                .getString("version") == "1.0.0" &&
                                payload.properties()
                                .getString("build") == 100.toString()
                        }
                    })
            )

        callback.get().onCreate(mockLifecycleOwner)
        verifyNoMoreInteractions(integration) // Application Installed is not duplicated
    }

    @Test
    @Throws(NameNotFoundException::class)
    fun trackApplicationLifecycleEventsUpdated() {
        AicactusSDK.INSTANCES.clear()

        val packageInfo = PackageInfo()
        packageInfo.versionCode = 101
        packageInfo.versionName = "1.0.1"

        val sharedPreferences =
            RuntimeEnvironment.application.getSharedPreferences("aicactusSDK-android-qaz", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("build", 100)
        editor.putString("version", "1.0.0")
        editor.apply()
        whenever(application.getSharedPreferences("aicactusSDK-android-qaz", MODE_PRIVATE))
            .thenReturn(sharedPreferences)

        val packageManager = Mockito.mock(PackageManager::class.java)
        whenever(packageManager.getPackageInfo("com.foo", 0)).thenReturn(packageInfo)
        whenever(application.packageName).thenReturn("com.foo")
        whenever(application.packageManager).thenReturn(packageManager)

        val callback = AtomicReference<DefaultLifecycleObserver>()
        doNothing()
            .whenever(lifecycle)
            .addObserver(
                argThat<LifecycleObserver>(
                    object : NoDescriptionMatcher<LifecycleObserver>() {
                        override fun matchesSafely(item: LifecycleObserver): Boolean {
                            callback.set(item as DefaultLifecycleObserver)
                            return true
                        }
                    })
            )

        val mockLifecycleOwner = Mockito.mock(LifecycleOwner::class.java)

        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(AicactusSDK.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                true,
                CountDownLatch(0),
                false,
                false, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                ValueMap(),
                lifecycle,
                false
        )

        callback.get().onCreate(mockLifecycleOwner)

        verify(integration)
            .track(
                argThat<TrackPayload>(
                    object : NoDescriptionMatcher<TrackPayload>() {
                        override fun matchesSafely(payload: TrackPayload): Boolean {
                            return payload.event() ==
                                "Application Updated" &&
                                payload.properties()
                                .getString("previous_version") == "1.0.0" &&
                                payload.properties()
                                .getString("previous_build") == 100.toString() &&
                                payload.properties()
                                .getString("version") == "1.0.1" &&
                                payload.properties()
                                .getString("build") == 101.toString()
                        }
                    })
            )
    }

    @Test
    @Throws(NameNotFoundException::class)
    fun recordScreenViews() {
        AicactusSDK.INSTANCES.clear()

        val callback = AtomicReference<ActivityLifecycleCallbacks>()
        doNothing()
            .whenever(application)
            .registerActivityLifecycleCallbacks(
                argThat<ActivityLifecycleCallbacks>(
                    object : NoDescriptionMatcher<ActivityLifecycleCallbacks>() {
                        override fun matchesSafely(item: ActivityLifecycleCallbacks): Boolean {
                            callback.set(item)
                            return true
                        }
                    })
            )

        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(Analytics.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                false,
                CountDownLatch(0),
                true,
                false, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                ValueMap(),
                lifecycle,
                false
        )

        val activity = Mockito.mock(Activity::class.java)
        val packageManager = Mockito.mock(PackageManager::class.java)
        val info = Mockito.mock(ActivityInfo::class.java)

        whenever(activity.packageManager).thenReturn(packageManager)
        //noinspection WrongConstant
        whenever(packageManager.getActivityInfo(any(ComponentName::class.java), eq(PackageManager.GET_META_DATA)))
            .thenReturn(info)
        whenever(info.loadLabel(packageManager)).thenReturn("Foo")

        callback.get().onActivityStarted(activity)

        aicactusSDK.screen("Foo")
        verify(integration)
            .screen(
                argThat<ScreenPayload>(
                    object : NoDescriptionMatcher<ScreenPayload>() {
                        override fun matchesSafely(payload: ScreenPayload): Boolean {
                            return payload.name() == "Foo"
                        }
                    })
            )
    }

    @Test
    fun trackDeepLinks() {
        AicactusSDK.INSTANCES.clear()

        val callback =
            AtomicReference<ActivityLifecycleCallbacks>()
        doNothing()
            .whenever(application)
            .registerActivityLifecycleCallbacks(
                argThat<ActivityLifecycleCallbacks>(
                    object : NoDescriptionMatcher<ActivityLifecycleCallbacks>() {
                        override fun matchesSafely(item: ActivityLifecycleCallbacks): Boolean {
                            callback.set(item)
                            return true
                        }
                    })
            )

        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(Analytics.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                true,
                CountDownLatch(0),
                false,
                true, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                ValueMap(),
                lifecycle,
                false
        )

        val expectedURL = "app://track.com/open?utm_id=12345&gclid=abcd&nope="

        val activity = Mockito.mock(Activity::class.java)
        val intent = Mockito.mock(Intent::class.java)
        val uri = Uri.parse(expectedURL)

        whenever(intent.data).thenReturn(uri)
        whenever(activity.intent).thenReturn(intent)

        callback.get().onActivityCreated(activity, Bundle())

        verify(integration)
            .track(
                argThat<TrackPayload>(
                    object : NoDescriptionMatcher<TrackPayload>() {
                        override fun matchesSafely(payload: TrackPayload): Boolean {
                            return payload.event() == "Deep Link Opened" &&
                                payload.properties()
                                .getString("url") == expectedURL &&
                                payload.properties()
                                .getString("gclid") == "abcd" &&
                                payload.properties()
                                .getString("utm_id") == "12345"
                        }
                    })
            )
    }

    @Test
    fun trackDeepLinks_disabled() {
        AicactusSDK.INSTANCES.clear()

        val callback =
            AtomicReference<ActivityLifecycleCallbacks>()

        doNothing()
            .whenever(application)
            .registerActivityLifecycleCallbacks(
                argThat<ActivityLifecycleCallbacks>(
                    object : NoDescriptionMatcher<ActivityLifecycleCallbacks>() {
                        override fun matchesSafely(item: ActivityLifecycleCallbacks): Boolean {
                            callback.set(item)
                            return true
                        }
                    })
            )

        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(Analytics.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                true,
                CountDownLatch(0),
                false,
                false, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                ValueMap(),
                lifecycle,
                false
        )

        val expectedURL = "app://track.com/open?utm_id=12345&gclid=abcd&nope="

        val activity = Mockito.mock(Activity::class.java)
        val intent = Mockito.mock(Intent::class.java)
        val uri = Uri.parse(expectedURL)

        whenever(intent.data).thenReturn(uri)
        whenever(activity.intent).thenReturn(intent)

        verify(integration, Mockito.never())
            .track(
                argThat<TrackPayload>(
                    object : NoDescriptionMatcher<TrackPayload>() {
                        override fun matchesSafely(payload: TrackPayload): Boolean {
                            return payload.event() == "Deep Link Opened" &&
                                payload.properties()
                                .getString("url") == expectedURL &&
                                payload.properties()
                                .getString("gclid") == "abcd" &&
                                payload.properties()
                                .getString("utm_id") == "12345"
                        }
                    })
            )
    }

    @Test
    fun trackDeepLinks_null() {
        AicactusSDK.INSTANCES.clear()

        val callback =
            AtomicReference<ActivityLifecycleCallbacks>()

        doNothing()
            .whenever(application)
            .registerActivityLifecycleCallbacks(
                argThat<ActivityLifecycleCallbacks>(
                    object : NoDescriptionMatcher<ActivityLifecycleCallbacks>() {
                        override fun matchesSafely(item: ActivityLifecycleCallbacks): Boolean {
                            callback.set(item)
                            return true
                        }
                    })
            )

        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(AicactusSDK.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                true,
                CountDownLatch(0),
                false,
                false, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                ValueMap(),
                lifecycle,
                false
        )

        val activity = Mockito.mock(Activity::class.java)

        whenever(activity.intent).thenReturn(null)

        callback.get().onActivityCreated(activity, Bundle())

        verify(integration, Mockito.never())
            .track(
                argThat<TrackPayload>(
                    object : NoDescriptionMatcher<TrackPayload>() {
                        override fun matchesSafely(payload: TrackPayload): Boolean {
                            return payload.event() == "Deep Link Opened"
                        }
                    })
            )
    }

    @Test
    fun trackDeepLinks_nullData() {
        AicactusSDK.INSTANCES.clear()

        val callback =
            AtomicReference<ActivityLifecycleCallbacks>()

        doNothing()
            .whenever(application)
            .registerActivityLifecycleCallbacks(
                argThat<ActivityLifecycleCallbacks>(
                    object : NoDescriptionMatcher<ActivityLifecycleCallbacks>() {
                        override fun matchesSafely(item: ActivityLifecycleCallbacks): Boolean {
                            callback.set(item)
                            return true
                        }
                    })
            )

        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(AicactusSDK.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                true,
                CountDownLatch(0),
                false,
                false, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                ValueMap(),
                lifecycle,
                false
        )

        val activity = Mockito.mock(Activity::class.java)

        val intent = Mockito.mock(Intent::class.java)

        whenever(activity.intent).thenReturn(intent)
        whenever(intent.data).thenReturn(null)

        callback.get().onActivityCreated(activity, Bundle())

        verify(integration, Mockito.never())
            .track(
                argThat<TrackPayload>(
                    object : NoDescriptionMatcher<TrackPayload>() {
                        override fun matchesSafely(payload: TrackPayload): Boolean {
                            return payload.event() == "Deep Link Opened"
                        }
                    })
            )
    }

    @Test
    @Throws(NameNotFoundException::class)
    fun registerActivityLifecycleCallbacks() {
        AicactusSDK.INSTANCES.clear()

        val callback =
            AtomicReference<ActivityLifecycleCallbacks>()

        doNothing()
            .whenever(application)
            .registerActivityLifecycleCallbacks(
                argThat<ActivityLifecycleCallbacks>(
                    object : NoDescriptionMatcher<ActivityLifecycleCallbacks>() {
                        override fun matchesSafely(item: ActivityLifecycleCallbacks): Boolean {
                            callback.set(item)
                            return true
                        }
                    })
            )

        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(Analytics.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                true,
                CountDownLatch(0),
                false,
                false, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                ValueMap(),
                lifecycle,
                false
        )

        val activity = Mockito.mock(Activity::class.java)
        val bundle = Bundle()

        callback.get().onActivityCreated(activity, bundle)
        verify(integration).onActivityCreated(activity, bundle)

        callback.get().onActivityStarted(activity)
        verify(integration).onActivityStarted(activity)

        callback.get().onActivityResumed(activity)
        verify(integration).onActivityResumed(activity)

        callback.get().onActivityPaused(activity)
        verify(integration).onActivityPaused(activity)

        callback.get().onActivityStopped(activity)
        verify(integration).onActivityStopped(activity)

        callback.get().onActivitySaveInstanceState(activity, bundle)
        verify(integration).onActivitySaveInstanceState(activity, bundle)

        callback.get().onActivityDestroyed(activity)
        verify(integration).onActivityDestroyed(activity)

        verifyNoMoreInteractions(integration)
    }

    @Test
    @Throws(NameNotFoundException::class)
    fun trackApplicationLifecycleEventsApplicationOpened() {
        AicactusSDK.INSTANCES.clear()

        val callback =
            AtomicReference<DefaultLifecycleObserver>()

        doNothing()
            .whenever(lifecycle)
            .addObserver(
                argThat<LifecycleObserver>(
                    object : NoDescriptionMatcher<LifecycleObserver>() {
                        override fun matchesSafely(item: LifecycleObserver): Boolean {
                            callback.set(item as DefaultLifecycleObserver)
                            return true
                        }
                    })
            )

        val mockLifecycleOwner = Mockito.mock(LifecycleOwner::class.java)

        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(Analytics.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                true,
                CountDownLatch(0),
                false,
                false, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                ValueMap(),
                lifecycle,
                false
        )

        callback.get().onCreate(mockLifecycleOwner)
        callback.get().onStart(mockLifecycleOwner)

        verify(integration)
            .track(
                argThat<TrackPayload>(
                    object : NoDescriptionMatcher<TrackPayload>() {
                        override fun matchesSafely(payload: TrackPayload): Boolean {
                            return payload.event() == "Application Opened" &&
                                payload.properties().getString("version") == "1.0.0" &&
                                payload.properties().getString("build") == 100.toString() &&
                                !payload.properties().getBoolean("from_background", true)
                        }
                    })
            )
    }

    @Test
    @Throws(NameNotFoundException::class)
    fun trackApplicationLifecycleEventsApplicationBackgrounded() {
        AicactusSDK.INSTANCES.clear()

        val callback =
            AtomicReference<DefaultLifecycleObserver>()

        doNothing()
            .whenever(lifecycle)
            .addObserver(
                argThat<LifecycleObserver>(
                    object : NoDescriptionMatcher<LifecycleObserver>() {
                        override fun matchesSafely(item: LifecycleObserver): Boolean {
                            callback.set(item as DefaultLifecycleObserver)
                            return true
                        }
                    })
            )

        val mockLifecycleOwner = Mockito.mock(LifecycleOwner::class.java)

        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(AicactusSDK.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                true,
                CountDownLatch(0),
                false,
                false, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                ValueMap(),
                lifecycle,
                false
        )

        val backgroundedActivity = Mockito.mock(Activity::class.java)
        whenever(backgroundedActivity.isChangingConfigurations).thenReturn(false)

        callback.get().onCreate(mockLifecycleOwner)
        callback.get().onResume(mockLifecycleOwner)
        callback.get().onStop(mockLifecycleOwner)

        verify(integration)
            .track(
                argThat<TrackPayload>(
                    object : NoDescriptionMatcher<TrackPayload>() {
                        override fun matchesSafely(payload: TrackPayload): Boolean {
                            return payload.event() == "Application Backgrounded"
                        }
                    })
            )
    }

    @Test
    @Throws(NameNotFoundException::class)
    fun trackApplicationLifecycleEventsApplicationForegrounded() {
        AicactusSDK.INSTANCES.clear()

        val callback =
            AtomicReference<DefaultLifecycleObserver>()

        doNothing()
            .whenever(lifecycle)
            .addObserver(
                argThat<LifecycleObserver>(
                    object : NoDescriptionMatcher<LifecycleObserver>() {
                        override fun matchesSafely(item: LifecycleObserver): Boolean {
                            callback.set(item as DefaultLifecycleObserver)
                            return true
                        }
                    })
            )

        val mockLifecycleOwner = Mockito.mock(LifecycleOwner::class.java)

        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(Analytics.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                true,
                CountDownLatch(0),
                false,
                false, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                ValueMap(),
                lifecycle,
                false
        )

        callback.get().onCreate(mockLifecycleOwner)
        callback.get().onStart(mockLifecycleOwner)
        callback.get().onStop(mockLifecycleOwner)
        callback.get().onStart(mockLifecycleOwner)

        verify(integration)
            .track(
                argThat<TrackPayload>(
                    object : NoDescriptionMatcher<TrackPayload>() {
                        override fun matchesSafely(payload: TrackPayload): Boolean {
                            return payload.event() == "Application Backgrounded"
                        }
                    })
            )

        verify(integration)
            .track(
                argThat<TrackPayload>(
                    object : NoDescriptionMatcher<TrackPayload>() {
                        override fun matchesSafely(payload: TrackPayload): Boolean {
                            return payload.event() == "Application Opened" &&
                                payload.properties()
                                    .getBoolean("from_background", false)
                        }
                    })
            )
    }

    @Test
    @Throws(NameNotFoundException::class)
    fun unregisterActivityLifecycleCallbacks() {
        AicactusSDK.INSTANCES.clear()

        val registeredCallback = AtomicReference<ActivityLifecycleCallbacks>()
        val unregisteredCallback = AtomicReference<ActivityLifecycleCallbacks>()

        doNothing()
            .whenever(application)
            .registerActivityLifecycleCallbacks(
                argThat<ActivityLifecycleCallbacks>(
                    object : NoDescriptionMatcher<ActivityLifecycleCallbacks>() {
                        override fun matchesSafely(item: ActivityLifecycleCallbacks): Boolean {
                            registeredCallback.set(item)
                            return true
                        }
                    })
            )
        doNothing()
            .whenever(application)
            .unregisterActivityLifecycleCallbacks(
                argThat<ActivityLifecycleCallbacks>(
                    object : NoDescriptionMatcher<ActivityLifecycleCallbacks>() {
                        override fun matchesSafely(item: ActivityLifecycleCallbacks): Boolean {
                            unregisteredCallback.set(item)
                            return true
                        }
                    })
            )

        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(AicactusSDK.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                true,
                CountDownLatch(0),
                false,
                false, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                ValueMap(),
                lifecycle,
                false
        )

        Assertions.assertThat(aicactusSDK.shutdown).isFalse()
        aicactusSDK.shutdown()

        // Same callback was registered and unregistered
        Assertions.assertThat(aicactusSDK.activityLifecycleCallback).isSameAs(registeredCallback.get())
        Assertions.assertThat(aicactusSDK.activityLifecycleCallback).isSameAs(unregisteredCallback.get())

        val activity = Mockito.mock(Activity::class.java)
        val bundle = Bundle()

        // Verify callbacks do not call through after shutdown
        registeredCallback.get().onActivityCreated(activity, bundle)
        verify(integration, never()).onActivityCreated(activity, bundle)

        registeredCallback.get().onActivityStarted(activity)
        verify(integration, never()).onActivityStarted(activity)

        registeredCallback.get().onActivityResumed(activity)
        verify(integration, never()).onActivityResumed(activity)

        registeredCallback.get().onActivityPaused(activity)
        verify(integration, never()).onActivityPaused(activity)

        registeredCallback.get().onActivityStopped(activity)
        verify(integration, never()).onActivityStopped(activity)

        registeredCallback.get().onActivitySaveInstanceState(activity, bundle)
        verify(integration, never()).onActivitySaveInstanceState(activity, bundle)

        registeredCallback.get().onActivityDestroyed(activity)
        verify(integration, never()).onActivityDestroyed(activity)

        verifyNoMoreInteractions(integration)
    }

    @Test
    @Throws(NameNotFoundException::class)
    fun removeLifecycleObserver() {
        AicactusSDK.INSTANCES.clear()

        val registeredCallback = AtomicReference<DefaultLifecycleObserver>()
        val unregisteredCallback = AtomicReference<DefaultLifecycleObserver>()

        doNothing()
            .whenever(lifecycle)
            .addObserver(
                argThat<LifecycleObserver>(
                    object : NoDescriptionMatcher<LifecycleObserver>() {
                        override fun matchesSafely(item: LifecycleObserver): Boolean {
                            registeredCallback.set(item as DefaultLifecycleObserver)
                            return true
                        }
                    })
            )
        doNothing()
            .whenever(lifecycle)
            .removeObserver(
                argThat<LifecycleObserver>(
                    object : NoDescriptionMatcher<LifecycleObserver>() {
                        override fun matchesSafely(item: LifecycleObserver): Boolean {
                            unregisteredCallback.set(item as DefaultLifecycleObserver)
                            return true
                        }
                    })
            )
        val mockLifecycleOwner = Mockito.mock(LifecycleOwner::class.java)

        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(AicactusSDK.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                false,
                CountDownLatch(0),
                false,
                false, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                ValueMap(),
                lifecycle,
                false
        )

        Assertions.assertThat(aicactusSDK.shutdown).isFalse()
        aicactusSDK.shutdown()
        val lifecycleObserverSpy = spy(aicactusSDK.activityLifecycleCallback)
        // Same callback was registered and unregistered
        Assertions.assertThat(aicactusSDK.activityLifecycleCallback).isSameAs(registeredCallback.get())
        Assertions.assertThat(aicactusSDK.activityLifecycleCallback).isSameAs(unregisteredCallback.get())

        // Verify callbacks do not call through after shutdown
        registeredCallback.get().onCreate(mockLifecycleOwner)
        verify(lifecycleObserverSpy, never()).onCreate(mockLifecycleOwner)

        registeredCallback.get().onStop(mockLifecycleOwner)
        verify(lifecycleObserverSpy, never()).onStop(mockLifecycleOwner)

        registeredCallback.get().onStart(mockLifecycleOwner)
        verify(lifecycleObserverSpy, never()).onStart(mockLifecycleOwner)

        verifyNoMoreInteractions(lifecycleObserverSpy)
    }

    @Test
    @Throws(IOException::class)
    fun loadNonEmptyDefaultProjectSettingsOnNetworkError() {
        AicactusSDK.INSTANCES.clear()
        // Make project download empty map and thus use default settings
        whenever(projectSettingsCache.get()).thenReturn(null)
        whenever(client.fetchSettings()).thenThrow(IOException::class.java) // Simulate network error

        val defaultProjectSettings =
            ValueMap()
                .putValue(
                    "integrations",
                    ValueMap()
                        .putValue(
                            "Adjust",
                            ValueMap()
                                .putValue("appToken", "<>")
                                .putValue("trackAttributionData", true)
                        )
                )

        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(AicactusSDK.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                true,
                CountDownLatch(0),
                false,
                false,
                true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                defaultProjectSettings,
                lifecycle,
                false
        )

        Assertions.assertThat(aicactusSDK.projectSettings).hasSize(2)
        Assertions.assertThat(aicactusSDK.projectSettings).containsKey("integrations")
        Assertions.assertThat(aicactusSDK.projectSettings.integrations()).hasSize(2)
        Assertions.assertThat(aicactusSDK.projectSettings.integrations()).containsKey("AiCactus")
        Assertions.assertThat(aicactusSDK.projectSettings.integrations()).containsKey("Adjust")
    }

    @Test
    @Throws(IOException::class)
    fun loadEmptyDefaultProjectSettingsOnNetworkError() {
        AicactusSDK.INSTANCES.clear()
        // Make project download empty map and thus use default settings
        whenever(projectSettingsCache.get()).thenReturn(null)
        whenever(client.fetchSettings()).thenThrow(IOException::class.java) // Simulate network error

        val defaultProjectSettings = ValueMap()
        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(AicactusSDK.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                true,
                CountDownLatch(0),
                false,
                false, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                defaultProjectSettings,
                lifecycle,
                false
        )

        Assertions.assertThat(aicactusSDK.projectSettings).hasSize(2)
        Assertions.assertThat(aicactusSDK.projectSettings).containsKey("integrations")
        Assertions.assertThat(aicactusSDK.projectSettings.integrations()).hasSize(1)
        Assertions.assertThat(aicactusSDK.projectSettings.integrations()).containsKey("AiCactus")
    }

    @Test
    @Throws(IOException::class)
    fun overwriteSegmentIoIntegration() {
        AicactusSDK.INSTANCES.clear()
        // Make project download empty map and thus use default settings
        whenever(projectSettingsCache.get()).thenReturn(null)
        whenever(client.fetchSettings()).thenThrow(IOException::class.java) // Simulate network error

        val defaultProjectSettings = ValueMap()
            .putValue(
                "integrations",
                ValueMap()
                    .putValue(
                        "AiCactus",
                        ValueMap()
                            .putValue("appToken", "<>")
                            .putValue("trackAttributionData", true)
                    )
            )
        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(Analytics.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                true,
                CountDownLatch(0),
                false,
                false, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                defaultProjectSettings,
                lifecycle,
                false
        )

        Assertions.assertThat(aicactusSDK.projectSettings).hasSize(2)
        Assertions.assertThat(aicactusSDK.projectSettings).containsKey("integrations")
        Assertions.assertThat(aicactusSDK.projectSettings.integrations()).containsKey("AiCactus")
        Assertions.assertThat(aicactusSDK.projectSettings.integrations()).hasSize(1)
        Assertions.assertThat(aicactusSDK.projectSettings.integrations().getValueMap("AiCactus"))
            .hasSize(3)
        Assertions.assertThat(aicactusSDK.projectSettings.integrations().getValueMap("AiCactus"))
            .containsKey("apiKey")
        Assertions.assertThat(aicactusSDK.projectSettings.integrations().getValueMap("AiCactus"))
            .containsKey("appToken")
        Assertions.assertThat(aicactusSDK.projectSettings.integrations().getValueMap("AiCactus"))
            .containsKey("trackAttributionData")
    }

    @Test
    fun overridingOptionsDoesNotModifyGlobalAnalytics() {
        aicactusSDK.track("event", null, Options().putContext("testProp", true))
        val payload = ArgumentCaptor.forClass(TrackPayload::class.java)
        verify(integration).track(payload.capture())
        Assertions.assertThat(payload.value.context()).containsKey("testProp")
        Assertions.assertThat(payload.value.context()["testProp"]).isEqualTo(true)
        Assertions.assertThat(aicactusSDK.analyticsContext).doesNotContainKey("testProp")
    }

    @Test
    fun overridingOptionsWithDefaultOptionsPlusAdditional() {
        aicactusSDK.track("event", null, aicactusSDK.getDefaultOptions().putContext("testProp", true))
        val payload = ArgumentCaptor.forClass(TrackPayload::class.java)
        verify(integration).track(payload.capture())
        Assertions.assertThat(payload.value.context()).containsKey("testProp")
        Assertions.assertThat(payload.value.context()["testProp"]).isEqualTo(true)
        Assertions.assertThat(aicactusSDK.analyticsContext).doesNotContainKey("testProp")
    }

    @Test
    fun enableExperimentalNanosecondResolutionTimestamps() {
        AicactusSDK.INSTANCES.clear()
        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(AicactusSDK.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                true,
                CountDownLatch(0),
                false,
                false, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                ValueMap(),
                lifecycle,
                true
        )

        aicactusSDK.track("event")
        val payload = ArgumentCaptor.forClass(TrackPayload::class.java)
        verify(integration).track(payload.capture())
        val timestamp = payload.value["timestamp"] as String
        Assertions.assertThat(timestamp).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{9}Z")
    }

    @Test
    fun disableExperimentalNanosecondResolutionTimestamps() {
        AicactusSDK.INSTANCES.clear()
        aicactusSDK = AicactusSDK(
                application,
                networkExecutor,
                stats,
                traitsCache,
                analyticsContext,
                defaultOptions,
                Logger.with(Analytics.LogLevel.NONE),
                "qaz", listOf(factory),
                client,
                Cartographer.INSTANCE,
                projectSettingsCache,
                "foo",
                DEFAULT_FLUSH_QUEUE_SIZE,
                DEFAULT_FLUSH_INTERVAL.toLong(),
                analyticsExecutor,
                true,
                CountDownLatch(0),
                false,
                false, true,
                optOut,
                Crypto.none(), emptyList(), emptyMap(),
                jsMiddleware,
                ValueMap(),
                lifecycle,
                false
        )

        aicactusSDK.track("event")
        val payload = ArgumentCaptor.forClass(TrackPayload::class.java)
        verify(integration).track(payload.capture())
        val timestamp = payload.value["timestamp"] as String
        Assertions.assertThat(timestamp).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z")
    }
}
