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

import com.detective.cache.IncludeCache
import com.detective.util.GITHUB_DOMAIN
import com.detective.util.GitlabCiUtil
import com.detective.util.HEAD_REF
import com.detective.util.INCLUDE_FILE_KEY
import com.detective.util.INCLUDE_REMOTE_KEY
import com.detective.util.MAIN_KEY
import com.detective.util.PROJECT_KEY
import com.detective.util.REF_KEY
import com.intellij.openapi.project.Project

private const val RAW_GITHUB_DOMAIN = "raw.githubusercontent.com"
private const val RAW_GITHUB_MIN_PARTS = 4
private const val GITHUB_MIN_PARTS = 5
private const val RAW_GITHUB_PATH_START_INDEX = 3
private const val GITHUB_PATH_START_INDEX = 4
object RemoteIncludeResolver {

    fun resolveGitLabFile(
        project: Project,
        projectPath: String,
        filePath: String,
        ref: String = MAIN_KEY
    ): String? {
        val cacheKey = GitlabCiUtil.gitlabCacheKey(projectPath, ref, filePath)
        return resolveWithCache(project, cacheKey) {
            GitLabApiClient.fetchFile(projectPath, filePath, ref).getOrNull()
        }
    }

    fun resolveRemoteUrl(project: Project, url: String): String? =
        resolveWithCache(project, url) {
            GitLabApiClient.fetchRemoteUrl(url).getOrNull()
        }

    fun resolveGitHubFile(project: Project, url: String): String? =
        resolveWithCache(project, url) {
            val parsed = parseGitHubUrl(url) ?: return@resolveWithCache null
            GitHubApiClient.fetchFile(
                owner = parsed.owner,
                repo = parsed.repo,
                path = parsed.path,
                ref = parsed.ref
            ).getOrNull()
        }

    fun resolveTemplate(project: Project, templateName: String): String? {
        val cacheKey = GitlabCiUtil.templateCacheKey(templateName)
        return resolveWithCache(project, cacheKey) {
            GitLabApiClient.fetchTemplate(templateName).getOrNull()
        }
    }

    fun resolveComponent(project: Project, componentString: String): String? {
        val ref = GitLabComponentParser.parse(componentString) ?: return null
        val cacheKey = ref.cacheKey

        return resolveWithCache(project, cacheKey) {
            val resolvedRef = if (ref.version.startsWith("~")) HEAD_REF else ref.version
            GitLabApiClient.fetchFile(
                projectPath = ref.projectPath,
                filePath = ref.filePath,
                ref = resolvedRef
            ).getOrNull()
        }
    }

    fun resolve(project: Project, includeMap: Map<String, String>): String? {
        return when {
            includeMap.containsKey(INCLUDE_FILE_KEY) -> {
                val projectPath = includeMap[PROJECT_KEY] ?: return null
                val filePath = includeMap[INCLUDE_FILE_KEY] ?: return null
                val ref = includeMap[REF_KEY] ?: MAIN_KEY
                resolveGitLabFile(project, projectPath, filePath, ref)
            }
            includeMap.containsKey(INCLUDE_REMOTE_KEY) -> {
                val url = includeMap[INCLUDE_REMOTE_KEY] ?: return null
                if (url.contains(GITHUB_DOMAIN)) resolveGitHubFile(project, url)
                else resolveRemoteUrl(project, url)
            }
            else -> null
        }
    }

    private fun resolveWithCache(
        project: Project,
        cacheKey: String,
        fetch: () -> String?
    ): String? {
        val cache = IncludeCache.getInstance(project)
        if (!cache.isStale(cacheKey)) {
            cache.get(cacheKey)?.let { return it }
        }
        return fetch()?.also { cache.put(cacheKey, it) }
    }

    data class GitHubFileRef(
        val owner: String,
        val repo: String,
        val ref: String,
        val path: String
    )

    fun parseGitHubUrl(url: String): GitHubFileRef? {
        return try {
            when {
                url.contains(RAW_GITHUB_DOMAIN) -> {
                    val parts = url.removePrefix("https://$RAW_GITHUB_DOMAIN/").split("/")
                    if (parts.size < RAW_GITHUB_MIN_PARTS) return null
                    GitHubFileRef(
                        owner = parts[0],
                        repo = parts[1],
                        ref = parts[2],
                        path = parts.drop(RAW_GITHUB_PATH_START_INDEX).joinToString("/")
                    )
                }
                url.contains(GITHUB_DOMAIN) -> {
                    val parts = url.removePrefix("https://$GITHUB_DOMAIN/").split("/")
                    if (parts.size < GITHUB_MIN_PARTS) return null
                    GitHubFileRef(
                        owner = parts[0],
                        repo = parts[1],
                        ref = parts[3],
                        path = parts.drop(GITHUB_PATH_START_INDEX).joinToString("/")
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}