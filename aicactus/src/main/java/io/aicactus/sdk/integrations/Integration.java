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
package io.aicactus.sdk.integrations;

import android.app.Activity;
import android.os.Bundle;
import androidx.annotation.NonNull;

import io.aicactus.sdk.AicactusSDK;
import io.aicactus.sdk.ValueMap;

/**
 * Converts Segment messages to a format a bundled integration understands, and calls those methods.
 *
 * @param <T> The type of the backing instance. This isn't strictly necessary (since we return an
 *     object), but serves as documentation for what type to expect with {@link
 *     #getUnderlyingInstance()}.
 */
public abstract class Integration<T> {

    public interface Factory {

        /**
         * Attempts to create an adapter for with {@code settings}. This returns the adapter if one
         * was created, or null if this factory isn't capable of creating such an adapter.
         */
        Integration<?> create(ValueMap settings, AicactusSDK aicactusSDK);

        /** The key for which this factory can create an {@link Integration}. */
        @NonNull
        String key();
    }

    /** @see android.app.Application.ActivityLifecycleCallbacks */
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

    /** @see android.app.Application.ActivityLifecycleCallbacks */
    public void onActivityStarted(Activity activity) {}

    /** @see android.app.Application.ActivityLifecycleCallbacks */
    public void onActivityResumed(Activity activity) {}

    /** @see android.app.Application.ActivityLifecycleCallbacks */
    public void onActivityPaused(Activity activity) {}

    /** @see android.app.Application.ActivityLifecycleCallbacks */
    public void onActivityStopped(Activity activity) {}

    /** @see android.app.Application.ActivityLifecycleCallbacks */
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

    /** @see android.app.Application.ActivityLifecycleCallbacks */
    public void onActivityDestroyed(Activity activity) {}

    /**
     * @see AicactusSDK#identify(String, io.aicactus.sdk.Traits, io.aicactus.sdk.Options)
     */
    public void identify(IdentifyPayload identify) {}

    /** @see AicactusSDK#group(String, io.aicactus.sdk.Traits, io.aicactus.sdk.Options) */
    public void group(GroupPayload group) {}

    /**
     * @see AicactusSDK#track(String, io.aicactus.sdk.Properties, io.aicactus.sdk.Options)
     */
    public void track(TrackPayload track) {}

    /** @see AicactusSDK#alias(String, io.aicactus.sdk.Options) */
    public void alias(AliasPayload alias) {}

    /**
     * @see AicactusSDK#screen(String, String, io.aicactus.sdk.Properties,
     *     io.aicactus.sdk.Options)
     */
    public void screen(ScreenPayload screen) {}

    /** @see AicactusSDK#flush() */
    public void flush() {}

    /** @see AicactusSDK#reset() */
    public void reset() {}

    /**
     * The underlying instance for this provider - used for integration specific actions. This will
     * return {@code null} for SDK's that only provide interactions with static methods (e.g.
     * Localytics).
     */
    public T getUnderlyingInstance() {
        return null;
    }
}
