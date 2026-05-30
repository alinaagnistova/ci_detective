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

class RemoteIncludeResolverTest {

    @Test
    fun `parseGitHubUrl parses raw githubusercontent URL`() {
        val url = "https://raw.githubusercontent.com/owner/repo/main/path/to/file.yml"
        val result = RemoteIncludeResolver.parseGitHubUrl(url)

        assertNotNull(result)
        assertEquals("owner", result!!.owner)
        assertEquals("repo", result.repo)
        assertEquals("main", result.ref)
        assertEquals("path/to/file.yml", result.path)
    }

    @Test
    fun `parseGitHubUrl parses github blob URL`() {
        val url = "https://github.com/owner/repo/blob/main/path/to/file.yml"
        val result = RemoteIncludeResolver.parseGitHubUrl(url)

        assertNotNull(result)
        assertEquals("owner", result!!.owner)
        assertEquals("repo", result.repo)
        assertEquals("main", result.ref)
        assertEquals("path/to/file.yml", result.path)
    }

    @Test
    fun `parseGitHubUrl parses raw URL with nested path`() {
        val url = "https://raw.githubusercontent.com/org/repo/feature-branch/ci/templates/base.yml"
        val result = RemoteIncludeResolver.parseGitHubUrl(url)

        assertNotNull(result)
        assertEquals("org", result!!.owner)
        assertEquals("repo", result.repo)
        assertEquals("feature-branch", result.ref)
        assertEquals("ci/templates/base.yml", result.path)
    }

    @Test
    fun `parseGitHubUrl returns null for non-github URL`() {
        val url = "https://example.com/some/file.yml"
        assertNull(RemoteIncludeResolver.parseGitHubUrl(url))
    }

    @Test
    fun `parseGitHubUrl returns null for raw URL with too few parts`() {
        val url = "https://raw.githubusercontent.com/owner/repo"
        assertNull(RemoteIncludeResolver.parseGitHubUrl(url))
    }

    @Test
    fun `parseGitHubUrl returns null for github blob URL with too few parts`() {
        val url = "https://github.com/owner/repo/blob"
        assertNull(RemoteIncludeResolver.parseGitHubUrl(url))
    }

    @Test
    fun `parseGitHubUrl parses URL with tag ref`() {
        val url = "https://raw.githubusercontent.com/owner/repo/v1.2.3/templates/deploy.yml"
        val result = RemoteIncludeResolver.parseGitHubUrl(url)

        assertNotNull(result)
        assertEquals("v1.2.3", result!!.ref)
        assertEquals("templates/deploy.yml", result.path)
    }

    @Test
    fun `parseGitHubUrl parses URL with commit sha ref`() {
        val url = "https://raw.githubusercontent.com/owner/repo/abc123def456/file.yml"
        val result = RemoteIncludeResolver.parseGitHubUrl(url)

        assertNotNull(result)
        assertEquals("abc123def456", result!!.ref)
        assertEquals("file.yml", result.path)
    }
}