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

class RetryPolicyTest {

    @Test
    fun `withRetry returns success on first attempt`() {
        var callCount = 0
        val result = RetryPolicy.withRetry("test") {
            callCount++
            Result.success("ok")
        }

        assertTrue(result.isSuccess)
        assertEquals("ok", result.getOrNull())
        assertEquals(1, callCount)
    }

    @Test
    fun `withRetry retries on failure and succeeds`() {
        var callCount = 0
        val result = RetryPolicy.withRetry("test") {
            callCount++
            if (callCount < 3) Result.failure<String>(Exception("fail"))
            else Result.success("ok")
        }

        assertTrue(result.isSuccess)
        assertEquals("ok", result.getOrNull())
        assertEquals(3, callCount)
    }

    @Test
    fun `withRetry returns failure after max attempts`() {
        var callCount = 0
        val result = RetryPolicy.withRetry("test") {
            callCount++
            Result.failure<String>(Exception("always fails"))
        }

        assertTrue(result.isFailure)
        assertEquals(3, callCount)
    }

    @Test
    fun `withRetry does not exceed max attempts`() {
        var callCount = 0
        RetryPolicy.withRetry("test") {
            callCount++
            Result.failure<String>(Exception("fail"))
        }

        assertTrue(callCount <= 3)
    }

    @Test
    fun `withRetry returns last exception message on failure`() {
        var attempt = 0
        val result = RetryPolicy.withRetry("test") {
            attempt++
            Result.failure<String>(Exception("error attempt $attempt"))
        }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("error attempt") == true)
    }
}