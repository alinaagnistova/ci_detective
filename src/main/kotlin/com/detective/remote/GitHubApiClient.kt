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
import java.util.Base64
import java.util.concurrent.TimeUnit

private const val GITHUB_API_BASE = "https://api.github.com"
private const val GITHUB_API_ACCEPT_HEADER = "application/vnd.github.v3+json"
private const val CONNECT_TIMEOUT_SECONDS = 30L
private const val READ_TIMEOUT_SECONDS = 60L

object GitHubApiClient {

    private val log = Logger.getInstance(GitHubApiClient::class.java)

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    fun fetchFile(
        owner: String,
        repo: String,
        path: String,
        ref: String = MAIN_KEY
    ): Result<String> {
        val token = GitlabCiSettings.getInstance().githubToken
        val url = "$GITHUB_API_BASE/repos/$owner/$repo/contents/${path.trimStart('/')}?ref=$ref"

        return RetryPolicy.withRetry("GitHub GET $owner/$repo/$path") {
            val start = System.currentTimeMillis()
            try {
                val requestBuilder = Request.Builder()
                    .url(url)
                    .addHeader("Accept", GITHUB_API_ACCEPT_HEADER)
                if (token.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                val elapsed = System.currentTimeMillis() - start

                if (response.isSuccessful) {
                    log.info("CI-DETECTIVE: GitHub API ${elapsed}ms → ${response.code} $url")
                    val json = JsonParser.parseString(response.body?.string() ?: "").asJsonObject
                    val encoded = json.get("content").asString.replace("\n", "")
                    Result.success(String(Base64.getDecoder().decode(encoded)))
                } else {
                    log.warn("CI-DETECTIVE: GitHub API ${elapsed}ms → ERROR ${response.code} $url")
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - start
                log.warn("CI-DETECTIVE: GitHub API failed after ${elapsed}ms: ${e.message}")
                Result.failure(e)
            }
        }
    }
}