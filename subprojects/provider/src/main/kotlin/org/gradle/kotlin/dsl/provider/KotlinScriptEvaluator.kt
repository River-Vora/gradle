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

package org.gradle.kotlin.dsl.provider

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.plugins.PluginAwareInternal

import org.gradle.cache.CacheOpenException
import org.gradle.cache.internal.CacheKeyBuilder
import org.gradle.caching.internal.controller.BuildCacheController

import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.internal.ScriptSourceHasher

import org.gradle.internal.classloader.ClasspathHasher
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.hash.HashCode
import org.gradle.internal.id.UniqueId
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.internal.time.Time.startTimer

import org.gradle.kotlin.dsl.cache.LoadDirectory
import org.gradle.kotlin.dsl.cache.PackMetadata
import org.gradle.kotlin.dsl.cache.ScriptBuildCacheKey
import org.gradle.kotlin.dsl.cache.ScriptCache
import org.gradle.kotlin.dsl.cache.StoreDirectory

import org.gradle.kotlin.dsl.execution.EvalOption
import org.gradle.kotlin.dsl.execution.EvalOptions
import org.gradle.kotlin.dsl.execution.Interpreter
import org.gradle.kotlin.dsl.execution.ProgramId

import org.gradle.kotlin.dsl.get

import org.gradle.kotlin.dsl.support.EmbeddedKotlinProvider
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.kotlinEap
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.kotlin.dsl.support.ScriptCompilationException
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.support.transitiveClosureOf

import org.gradle.plugin.management.internal.DefaultPluginRequests
import org.gradle.plugin.management.internal.PluginRequests

import org.gradle.plugin.use.internal.PluginRequestApplicator

import java.io.File


interface KotlinScriptEvaluator {

    fun evaluate(
        target: Any,
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope,
        topLevelScript: Boolean,
        options: EvalOptions
    )
}


internal
class StandardKotlinScriptEvaluator(
    private val classPathProvider: KotlinScriptClassPathProvider,
    private val classloadingCache: KotlinScriptClassloadingCache,
    private val pluginRequestApplicator: PluginRequestApplicator,
    private val pluginRequestsHandler: PluginRequestsHandler,
    private val embeddedKotlinProvider: EmbeddedKotlinProvider,
    private val classPathModeExceptionCollector: ClassPathModeExceptionCollector,
    private val kotlinScriptBasePluginsApplicator: KotlinScriptBasePluginsApplicator,
    private val scriptSourceHasher: ScriptSourceHasher,
    private val classPathHasher: ClasspathHasher,
    private val scriptCache: ScriptCache,
    private val implicitImports: ImplicitImports,
    private val progressLoggerFactory: ProgressLoggerFactory
) : KotlinScriptEvaluator {

    override fun evaluate(
        target: Any,
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope,
        topLevelScript: Boolean,
        options: EvalOptions
    ) {
        withOptions(options) {

            interpreter.eval(
                target,
                scriptSource,
                scriptSourceHasher.hash(scriptSource),
                scriptHandler,
                targetScope,
                baseScope,
                topLevelScript,
                options
            )
        }
    }

    private
    inline fun withOptions(options: EvalOptions, action: () -> Unit) {
        if (EvalOption.IgnoreErrors in options)
            classPathModeExceptionCollector.ignoringErrors(action)
        else
            action()
    }

    private
    fun setupEmbeddedKotlinForBuildscript(scriptHandler: ScriptHandler) {
        embeddedKotlinProvider.run {
            addRepositoryTo(scriptHandler.repositories)
            pinDependenciesOn(
                scriptHandler.configurations["classpath"],
                embeddedKotlinModules)
        }
    }

    private
    val interpreter by lazy {
        Interpreter(InterpreterHost())
    }

    private
    inner class InterpreterHost : Interpreter.Host {

        override fun setupEmbeddedKotlinFor(scriptHost: KotlinScriptHost<*>) {
            setupEmbeddedKotlinForBuildscript(scriptHost.scriptHandler)
        }

        override fun startCompilerOperation(description: String): AutoCloseable {
            val operation = progressLoggerFactory
                .newOperation(KotlinScriptEvaluator::class.java)
                .start("Compiling script into cache", "Compiling $description into local compilation cache")
            return AutoCloseable { operation.completed() }
        }

        override fun hashOf(classPath: ClassPath): HashCode =
            classPathHasher.hash(classPath)

        override fun applyPluginsTo(scriptHost: KotlinScriptHost<*>, pluginRequests: PluginRequests) {
            pluginRequestsHandler.handle(
                pluginRequests,
                scriptHost.scriptHandler as ScriptHandlerInternal,
                scriptHost.target as PluginAwareInternal,
                scriptHost.targetScope)
        }

        override fun applyBasePluginsTo(project: Project) {
            kotlinScriptBasePluginsApplicator
                .apply(project)
        }

        override fun closeTargetScopeOf(scriptHost: KotlinScriptHost<*>) {

            pluginRequestApplicator.applyPlugins(
                DefaultPluginRequests.EMPTY,
                scriptHost.scriptHandler as ScriptHandlerInternal?,
                null,
                scriptHost.targetScope)

            //TODO:kotlin-eap - move to a precompiled InterpreterHost.afterTopLevelSettings callback
            (scriptHost.target as? Settings)?.run {
                addKotlinDevRepository()
            }
        }

        override fun cachedClassFor(
            programId: ProgramId
        ): Class<*>? = classloadingCache.get(programId)

        override fun cache(
            specializedProgram: Class<*>,
            programId: ProgramId
        ) {
            classloadingCache.put(
                programId,
                specializedProgram
            )
        }

        override fun cachedDirFor(
            scriptHost: KotlinScriptHost<*>,
            templateId: String,
            sourceHash: HashCode,
            parentClassLoader: ClassLoader,
            accessorsClassPath: ClassPath?,
            initializer: (File) -> Unit
        ): File = try {

            val baseCacheKey =
                cacheKeyPrefix + templateId + sourceHash + parentClassLoader

            val effectiveCacheKey =
                accessorsClassPath?.let { baseCacheKey + it }
                    ?: baseCacheKey

            cacheDirFor(scriptHost, effectiveCacheKey, initializer)
        } catch (e: CacheOpenException) {
            throw e.cause as? ScriptCompilationException ?: e
        }

        private
        fun cacheDirFor(
            scriptHost: KotlinScriptHost<*>,
            cacheKeySpec: CacheKeyBuilder.CacheKeySpec,
            initializer: (File) -> Unit
        ): File =
            scriptCache.cacheDirFor(cacheKeySpec, properties = cacheProperties) { baseDir, cacheKey ->
                val cacheDir = File(baseDir, "cache").apply { require(mkdir()) }
                initializeCacheDir(cacheDir, cacheKey, scriptHost, initializer)
            }.resolve("cache")

        private
        fun initializeCacheDir(cacheDir: File, cacheKey: String, scriptHost: KotlinScriptHost<*>, initializer: (File) -> Unit) {

            // TODO: Move BuildCacheController integration to ScriptCache
            val cacheController =
                if (scriptCache.hasBuildCacheIntegration) buildCacheControllerOf(scriptHost)
                else null

            if (cacheController != null) {
                val buildCacheKey = ScriptBuildCacheKey(scriptHost.scriptSource.displayName, cacheKey)
                val existing = cacheController.load(LoadDirectory(cacheDir, buildCacheKey))
                if (existing === null) {

                    val executionTime = executionTimeMillisOf {
                        initializer(cacheDir)
                    }

                    cacheController.store(
                        StoreDirectory(
                            cacheDir,
                            buildCacheKey,
                            PackMetadata(buildInvocationIdOf(scriptHost), executionTime)
                        )
                    )
                }
            } else {
                initializer(cacheDir)
            }
        }

        private
        fun buildCacheControllerOf(scriptHost: KotlinScriptHost<*>): BuildCacheController? =
            (scriptHost.target as? Project)
                ?.serviceOf<BuildCacheController>()
                ?.takeIf { it.isEnabled }

        private
        fun buildInvocationIdOf(scriptHost: KotlinScriptHost<*>): UniqueId =
            (scriptHost.target as Project)
                .gradle.serviceOf<BuildInvocationScopeId>()
                .id

        private
        val cacheProperties = mapOf("version" to "12")

        private
        val cacheKeyPrefix =
            CacheKeyBuilder.CacheKeySpec.withPrefix("gradle-kotlin-dsl")

        override fun compilationClassPathOf(classLoaderScope: ClassLoaderScope): ClassPath =
            classPathProvider.compilationClassPathOf(classLoaderScope)

        override fun loadClassInChildScopeOf(
            classLoaderScope: ClassLoaderScope,
            childScopeId: String,
            location: File,
            className: String,
            accessorsClassPath: ClassPath?
        ): Class<*> =
            classLoaderScope
                .createChild(childScopeId)
                .local(DefaultClassPath.of(location))
                .apply { accessorsClassPath?.let(::local) }
                .lock()
                .localClassLoader
                .loadClass(className)

        override val implicitImports: List<String>
            get() = this@StandardKotlinScriptEvaluator.implicitImports.list
    }
}


private
inline fun executionTimeMillisOf(action: () -> Unit) = startTimer().run {
    action()
    elapsedMillis
}


//TODO:kotlin-eap - make it conditional to a `-dev` or `-eap` Kotlin version
private
fun Settings.addKotlinDevRepository() {

    gradle.settingsEvaluated {
        if (pluginManagement.repositories.isEmpty()) {
            pluginManagement.run {
                repositories.run {
                    kotlinEap()
                    gradlePluginPortal()
                }
            }
        }
    }

    gradle.beforeProject { project ->
        project.buildscript.repositories.kotlinEap()
        project.repositories.kotlinEap()
    }
}


private
val embeddedKotlinModules by lazy {
    transitiveClosureOf("stdlib-jdk8", "reflect")
}
