/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.build.event.types;

import com.google.common.collect.ImmutableMap;
import org.gradle.tooling.internal.protocol.problem.InternalAdditionalData;
import org.jspecify.annotations.NullMarked;

import java.io.Serializable;
import java.util.Map;

@NullMarked
public class DefaultInternalAdditionalData implements InternalAdditionalData, Serializable {

    private final Map<String, Object> additionalData;

    public DefaultInternalAdditionalData(Map<String, Object> additionalData) {
        this.additionalData = ImmutableMap.copyOf(additionalData);
    }

    @Override
    public Map<String, Object> getAsMap() {
        return additionalData;
    }
}
