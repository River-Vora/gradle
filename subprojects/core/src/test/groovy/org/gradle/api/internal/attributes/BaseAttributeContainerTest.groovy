/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.attributes

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory
import org.gradle.util.AttributeTestUtil
import org.gradle.util.GradleVersion
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

/**
 * Abstract base class for testing functionality common to all {@link AttributeContainer} implementations.
 */
/* package */ abstract class BaseAttributeContainerTest extends Specification {
    protected attributesFactory
    protected final CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    protected final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    def setup() {
        attributesFactory = AttributeTestUtil.attributesFactory()
        def diagnosticsFactory = new NoOpProblemDiagnosticsFactory()
        def buildOperationProgressEventEmitter = Mock(BuildOperationProgressEventEmitter)
        DeprecationLogger.reset()
        DeprecationLogger.init(WarningMode.All, buildOperationProgressEventEmitter, TestUtil.problemsService(), diagnosticsFactory.newUnlimitedStream())
    }

    /**
     * Returns a new instance of the container type tested by this class.
     *
     * @param attributes optional map of attributes with values to populate the container with if present
     * @param attributes optional map of more attributes with values to populate the container with if present (for testing duplicate keys)
     * @return the container, populated with any given attributes from the argument
     */
    protected abstract AttributeContainer createContainer(Map<Attribute<?>, ?> attributes = [:], Map<Attribute<?>, ?> moreAttributes = [:])

    def "requesting a null key from an empty container emits a deprecation message"() {
        given:
        def container = createContainer()

        when:
        def result = container.getAttribute(null)

        then:
        result == null

        and:
        def events = outputEventListener.events.findAll { it.logLevel == LogLevel.WARN }
        events.size() == 1
        events[0].message == "Retrieving attribute with a null key. This behavior has been deprecated. This will fail with an error in Gradle 10.0. Don't request attributes from attribute containers using null keys. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#null-attribute-lookup"
    }

    def "requesting a null key from a container with elements emits a deprecation message"() {
        given:
        def container = createContainer([(Attribute.of("testString", String)): "testValue", (Attribute.of("testInt", Integer)): 1])

        when:
        def result = container.getAttribute(null)

        then:
        result == null

        and:
        def events = outputEventListener.events.findAll { it.logLevel == LogLevel.WARN }
        events.size() == 1
        events[0].message == "Retrieving attribute with a null key. This behavior has been deprecated. This will fail with an error in Gradle 10.0. Don't request attributes from attribute containers using null keys. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#null-attribute-lookup"
    }

    def "can't contain 2 identically named attributes with different types from the same classloader"() {
        when:
        def container = createContainer([(Attribute.of("test", String)): "a", (Attribute.of("test", Integer)): 1])

        then:
        def exception = thrown(Exception)
        if (container instanceof ImmutableAttributes) {
            exception.message == "Cannot have two attributes with the same name but different types. This container already has an attribute named 'test' of type 'java.lang.String' and you are trying to store another one of type 'java.lang.Integer'"
        } else {
            exception.message == "Cannot have two attributes with the same name but different types. This container already has an attribute named 'test' of type 'java.lang.Integer' and you are trying to store another one of type 'java.lang.String'"
        }
    }

    def "can't contain 2 identically named attributes with the same type loaded from different classloaders"() {
        given: "a second classloader, that has no parent, and can load the Named class"
        def pathToClass = Thread.currentThread().getContextClassLoader().getResource(Named.name.replace(".", "/") + ".class").toString()
        def pathToJar = pathToClass.substring("jar:".length(), pathToClass.indexOf('!'))
        def urls = new URL[] { new URL(pathToJar) }
        ClassLoader loader2 = new URLClassLoader(urls, (ClassLoader) null) // Loader 2 only has the URL of the jar containing Named

        when: "that alternate classloader is used to load the Named class"
        Class<?> named2 = loader2.loadClass(Named.name)

        then: "2 copies of the class Named exist, loaded from the default classloader and the alternate classloader"
        Named.name == named2.name
        Named.classLoader != named2.classLoader
        Named != named2

        when:
        createContainer([(Attribute.of("test", Named)): new MyNamed("name1"), (Attribute.of("test", named2)): new MyNamed("name2")])

        then:
        def exception = thrown(Exception)
        exception.message == "Cannot have two attributes with the same name but different types. This container already has an attribute named 'test' of type 'org.gradle.api.Named' and you are trying to store another one of type 'org.gradle.api.Named'"
    }

    private static final class MyNamed implements Named, Serializable {
        private final String name

        MyNamed(String name) {
            this.name = name
        }

        @Override
        String getName() {
            return null
        }
    }
}
