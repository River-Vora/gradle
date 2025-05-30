/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.process.internal.streams;

import org.gradle.internal.UncheckedException;
import org.gradle.process.internal.StreamsHandler;

import java.io.IOException;
import java.util.concurrent.Executor;

/**
 * A handler that writes nothing to the process' stdin
 */
public class EmptyStdInStreamsHandler implements StreamsHandler {
    @Override
    public void connectStreams(Process process, String processName, Executor executor) {
        try {
            process.getOutputStream().close();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void removeStartupContext() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void disconnect() {
    }
}
