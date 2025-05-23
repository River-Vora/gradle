/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.operations.notify

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.launcher.exec.RunBuildBuildOperationType

class BuildOperationNotificationContinuousBuildIntegrationTest extends AbstractContinuousIntegrationTest {

    def notifications = new BuildOperationNotificationFixture(executer, testDirectoryProvider)

    def "obtains notifications about init scripts"() {
        when:
        buildFile """
            apply plugin: "java"
        """
        file("src/main/java/Thing.java") << "class Thing {}"

        then:
        succeeds("build")

        when:
        file("src/main/java/Thing.java").text = "class Thing {\n\n}"

        then:
        buildTriggeredAndSucceeded()
        def runs = notifications.ops(RunBuildBuildOperationType.Details)

        and:
        runs.size() == 2
        runs[0].id != runs[1].id
    }

}
