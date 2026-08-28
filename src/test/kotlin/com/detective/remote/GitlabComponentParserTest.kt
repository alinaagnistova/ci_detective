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

package com.detective.remote

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GitlabComponentParserTest {

    @Test
    fun `parse standard component string`() {
        val result = GitLabComponentParser.parse("gitlab.com/my-org/my-component/deploy@1.0.0")

        assertNotNull(result)
        assertEquals("gitlab.com", result!!.host)
        assertEquals("my-org/my-component", result.projectPath)
        assertEquals("deploy", result.componentName)
        assertEquals("1.0.0", result.version)
    }

    @Test
    fun `parse component with latest version`() {
        val result = GitLabComponentParser.parse("gitlab.com/my-org/my-component/deploy@~latest")

        assertNotNull(result)
        assertEquals("~latest", result!!.version)
    }

    @Test
    fun `parse component with custom host`() {
        val result = GitLabComponentParser.parse("gitlab.example.com/my-org/my-component/deploy@main")

        assertNotNull(result)
        assertEquals("gitlab.example.com", result!!.host)
        assertEquals("my-org/my-component", result.projectPath)
    }

    @Test
    fun `parse component with nested namespace`() {
        val result = GitLabComponentParser.parse(
            "gitlab.com/my-org/subgroup/my-component/deploy@1.0.0"
        )

        assertNotNull(result)
        assertEquals("my-org/subgroup/my-component", result!!.projectPath)
        assertEquals("deploy", result.componentName)
    }

    @Test
    fun `parse returns null when no version`() {
        assertNull(GitLabComponentParser.parse("gitlab.com/my-org/my-component/deploy"))
    }

    @Test
    fun `parse returns null for too short string`() {
        assertNull(GitLabComponentParser.parse("gitlab.com/org@1.0.0"))
    }

    @Test
    fun `parse returns null for empty string`() {
        assertNull(GitLabComponentParser.parse(""))
    }

    @Test
    fun `filePath is correct`() {
        val result = GitLabComponentParser.parse("gitlab.com/my-org/my-component/deploy@1.0.0")
        assertEquals("templates/deploy.yml", result!!.filePath)
    }

    @Test
    fun `cacheKey has component prefix`() {
        val result = GitLabComponentParser.parse("gitlab.com/my-org/my-component/deploy@1.0.0")
        assertTrue(result!!.cacheKey.startsWith("component:"))
    }

    @Test
    fun `cacheKey is deterministic`() {
        val key1 = GitLabComponentParser.parse("gitlab.com/my-org/my-component/deploy@1.0.0")!!.cacheKey
        val key2 = GitLabComponentParser.parse("gitlab.com/my-org/my-component/deploy@1.0.0")!!.cacheKey
        assertEquals(key1, key2)
    }

    @Test
    fun `cacheKey differs for different versions`() {
        val key1 = GitLabComponentParser.parse("gitlab.com/my-org/my-component/deploy@1.0.0")!!.cacheKey
        val key2 = GitLabComponentParser.parse("gitlab.com/my-org/my-component/deploy@2.0.0")!!.cacheKey
        assertNotEquals(key1, key2)
    }
}