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
package io.aicactus.sdk.sample;

import android.app.Application;

import io.aicactus.sdk.AicactusSDK;

public class SampleApp extends Application {

    private static final String AICACTUS_WRITE_KEY = "d152290b-ba08-4a15-a66e-fce748116a0f";

    @Override
    public void onCreate() {
        super.onCreate();

        // Create an client with the given context and  write key.
        AicactusSDK config = new AicactusSDK.Builder(this, AICACTUS_WRITE_KEY)
                // Enable this to record certain application events automatically!
                .withStagingServer()
                .trackApplicationLifecycleEvents()
                // Enable this to record screen views automatically!
                .recordScreenViews()
                //.logLevel(AicactusSDK.LogLevel.VERBOSE)
                .build();

        // Set the initialized instance as a globally accessible instance.
        AicactusSDK.setup(config);
    }
}
