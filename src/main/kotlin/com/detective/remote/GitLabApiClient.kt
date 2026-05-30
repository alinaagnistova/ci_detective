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

import com.detective.settings.GitlabCiSettings
import com.detective.util.MAIN_KEY
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val CONNECT_TIMEOUT_SECONDS = 30L
private const val READ_TIMEOUT_SECONDS = 60L

object GitLabApiClient {

    private val log = Logger.getInstance(GitLabApiClient::class.java)


    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    fun fetchFile(
        projectPath: String,
        filePath: String,
        ref: String = MAIN_KEY
    ): Result<String> {
        val settings = GitlabCiSettings.getInstance()
        val baseUrl = settings.gitlabUrl.trimEnd('/')

        val encodedProject = projectPath.trim('/').replace("/", "%2F")
        val encodedFile = filePath.trim('/').replace("/", "%2F")
        val url = "$baseUrl/api/v4/projects/$encodedProject/repository/files/$encodedFile/raw?ref=$ref"

        return executeRequest(url, settings.gitlabToken)
    }

    fun fetchRemoteUrl(url: String): Result<String> =
        executeRequest(url, GitlabCiSettings.getInstance().gitlabToken)


    fun fetchTemplate(templateName: String): Result<String> {
        val settings = GitlabCiSettings.getInstance()
        val baseUrl = settings.gitlabUrl.trimEnd('/')
        val name = templateName.removeSuffix(".gitlab-ci.yml").removeSuffix(".yml")
        val url = "$baseUrl/api/v4/templates/gitlab_ci_ymls/$name"

        return executeRequest(url, settings.gitlabToken).mapCatching { body ->
            JsonParser.parseString(body).asJsonObject.get("content").asString
        }
    }

    fun isTokenConfigured(): Boolean = GitlabCiSettings.getInstance().gitlabToken.isNotBlank()

    private fun executeRequest(url: String, token: String): Result<String> {
        return RetryPolicy.withRetry("GET $url") {
            val start = System.currentTimeMillis()
            try {
                val requestBuilder = Request.Builder().url(url)
                if (token.isNotBlank()) requestBuilder.addHeader("PRIVATE-TOKEN", token)

                val response = client.newCall(requestBuilder.build()).execute()
                val elapsed = System.currentTimeMillis() - start

                if (response.isSuccessful) {
                    log.info("CI-DETECTIVE: GitLab API ${elapsed}ms → ${response.code} $url")
                    Result.success(response.body?.string() ?: "")
                } else {
                    log.warn("CI-DETECTIVE: GitLab API ${elapsed}ms → ERROR ${response.code} $url")
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - start
                log.warn("CI-DETECTIVE: GitLab API failed after ${elapsed}ms: ${e.message}")
                Result.failure(e)
            }
        }
    }
}