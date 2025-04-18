/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.tasks.application;

import org.gradle.work.DisableCachingByDefault;

/**
 * Legacy class for binary compatibility. It has the same behavior as {@link org.gradle.jvm.application.tasks.CreateStartScripts}.
 *
 * @see org.gradle.jvm.application.tasks.CreateStartScripts
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class CreateStartScripts extends org.gradle.jvm.application.tasks.CreateStartScripts {
}
