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

data class GitLabComponentRef(
    val host: String,
    val projectPath: String,
    val componentName: String,
    val version: String
) {
    val filePath: String get() = "templates/$componentName.yml"

    val cacheKey: String get() = "component:$host/$projectPath/$componentName@$version"
}

object GitLabComponentParser {

    fun parse(componentString: String): GitLabComponentRef? {
        return try {
            val atIndex = componentString.lastIndexOf('@')
            if (atIndex == -1) return null

            val version = componentString.substring(atIndex + 1).ifBlank { return null }
            val withoutVersion = componentString.substring(0, atIndex)

            val parts = withoutVersion.split("/")
            if (parts.size < 4) return null

            val host = parts[0]
            val componentName = parts.last()
            val projectPath = parts.drop(1).dropLast(1).joinToString("/")

            if (host.isBlank() || projectPath.isBlank() || componentName.isBlank()) return null

            GitLabComponentRef(
                host = host,
                projectPath = projectPath,
                componentName = componentName,
                version = version
            )
        } catch (e: Exception) {
            null
        }
    }
}