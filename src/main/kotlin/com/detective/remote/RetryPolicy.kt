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

import com.intellij.openapi.diagnostic.Logger

object RetryPolicy {

    private val log = Logger.getInstance(RetryPolicy::class.java)
    private const val MAX_RETRIES = 3
    private const val INITIAL_DELAY_MS = 1000L


    fun <T> withRetry(operation: String, block: () -> Result<T>): Result<T> {
        var lastResult: Result<T> = Result.failure(Exception("No attempts made"))
        var delayMs = INITIAL_DELAY_MS

        repeat(MAX_RETRIES) { attempt ->
            lastResult = block()
            if (lastResult.isSuccess) return lastResult

            val error = lastResult.exceptionOrNull()?.message ?: "unknown error"

            if (attempt < MAX_RETRIES - 1) {
                log.warn("CI-DETECTIVE: $operation failed (attempt ${attempt + 1}/$MAX_RETRIES): $error. Retrying in ${delayMs}ms...")
                Thread.sleep(delayMs)
                delayMs *= 2
            } else {
                log.warn("CI-DETECTIVE: $operation failed after $MAX_RETRIES attempts: $error")
            }
        }

        return lastResult
    }
}

