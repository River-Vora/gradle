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

plugins {
    id("gradlebuild.distribution.api-java")
}

description = """Provides the version catalog plugin."""

dependencies {
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagement)

    api(libs.guava)
    api(libs.inject)

    implementation(projects.loggingApi)
    implementation(projects.modelCore)
    implementation(projects.platformBase)
    implementation(projects.platformJvm)
    implementation(projects.stdlibJavaExtensions)

    implementation(libs.jspecify)

    runtimeOnly(libs.groovy)

    integTestImplementation(testFixtures(projects.core))
    integTestImplementation(testFixtures(projects.jvmServices))
    integTestImplementation(testFixtures(projects.resourcesHttp))

    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
