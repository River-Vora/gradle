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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A provider of dependency resolution state for local components produced by any build in the build tree.
 * <p>
 * In general, you should be using {@link LocalComponentRegistry} instead of this type, as it
 * triggers additional side effects that this provider does not.
 */
@ServiceScope(Scope.BuildTree.class)
@ThreadSafe
public interface BuildTreeLocalComponentProvider {

    /**
     * Get the local component for the target project.
     *
     * @param targetProjectId the project to get the local component for
     * @param sourceBuild the build that is requesting the local component
     */
    LocalComponentGraphResolveState getComponent(ProjectIdentity targetProjectId, Path sourceBuild);

}
