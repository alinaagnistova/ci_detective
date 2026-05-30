/*
 * Copyright 2026 Alina Agnistova
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.detective.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GitlabCiUtilTest {


    @Test
    fun `gitlabCacheKey produces correct format`() {
        val key = GitlabCiUtil.gitlabCacheKey("my-group/my-project", "main", "/templates/base.yml")
        assertEquals("gitlab:my-group/my-project:main:/templates/base.yml", key)
    }

    @Test
    fun `gitlabCacheKey is unique for different projectPaths`() {
        val key1 = GitlabCiUtil.gitlabCacheKey("group-a/project", "main", "/file.yml")
        val key2 = GitlabCiUtil.gitlabCacheKey("group-b/project", "main", "/file.yml")
        assertNotEquals(key1, key2)
    }

    @Test
    fun `gitlabCacheKey is unique for different refs`() {
        val key1 = GitlabCiUtil.gitlabCacheKey("group/project", "main", "/file.yml")
        val key2 = GitlabCiUtil.gitlabCacheKey("group/project", "develop", "/file.yml")
        assertNotEquals(key1, key2)
    }

    @Test
    fun `gitlabCacheKey is unique for different filePaths`() {
        val key1 = GitlabCiUtil.gitlabCacheKey("group/project", "main", "/templates/a.yml")
        val key2 = GitlabCiUtil.gitlabCacheKey("group/project", "main", "/templates/b.yml")
        assertNotEquals(key1, key2)
    }

    @Test
    fun `gitlabCacheKey is deterministic`() {
        val key1 = GitlabCiUtil.gitlabCacheKey("group/project", "main", "/file.yml")
        val key2 = GitlabCiUtil.gitlabCacheKey("group/project", "main", "/file.yml")
        assertEquals(key1, key2)
    }

    @Test
    fun `gitlabCacheKey starts with gitlab prefix`() {
        val key = GitlabCiUtil.gitlabCacheKey("group/project", "main", "/file.yml")
        assertTrue(key.startsWith("gitlab:"))
    }

    @Test
    fun `gitlabCacheKey handles nested project path`() {
        val key = GitlabCiUtil.gitlabCacheKey("group/subgroup/project", "1.x", "/ci/base.yml")
        assertEquals("gitlab:group/subgroup/project:1.x:/ci/base.yml", key)
    }

    @Test
    fun `templateCacheKey produces correct format`() {
        val key = GitlabCiUtil.templateCacheKey("Auto-DevOps.gitlab-ci.yml")
        assertEquals("template:Auto-DevOps.gitlab-ci.yml", key)
    }

    @Test
    fun `templateCacheKey starts with template prefix`() {
        val key = GitlabCiUtil.templateCacheKey("Kotlin.gitlab-ci.yml")
        assertTrue(key.startsWith("template:"))
    }

    @Test
    fun `templateCacheKey is unique for different template names`() {
        val key1 = GitlabCiUtil.templateCacheKey("Auto-DevOps.gitlab-ci.yml")
        val key2 = GitlabCiUtil.templateCacheKey("Kotlin.gitlab-ci.yml")
        assertNotEquals(key1, key2)
    }

    @Test
    fun `templateCacheKey is deterministic`() {
        val key1 = GitlabCiUtil.templateCacheKey("Auto-DevOps.gitlab-ci.yml")
        val key2 = GitlabCiUtil.templateCacheKey("Auto-DevOps.gitlab-ci.yml")
        assertEquals(key1, key2)
    }

    @Test
    fun `gitlabCacheKey and templateCacheKey never collide`() {
        val gitlabKey = GitlabCiUtil.gitlabCacheKey("group/project", "main", "/file.yml")
        val templateKey = GitlabCiUtil.templateCacheKey("group/project")
        assertNotEquals(gitlabKey, templateKey)
    }
}