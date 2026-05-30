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

package com.detective.cache

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class IncludeCacheTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var cache: IncludeCacheStandalone

    @BeforeEach
    fun setUp() {
        cache = IncludeCacheStandalone(tempDir.toFile())
    }

    @Test
    fun `put and get returns stored value`() {
        cache.put("key1", "content1")
        assertEquals("content1", cache.get("key1"))
    }

    @Test
    fun `get returns null for missing key`() {
        assertNull(cache.get("nonexistent"))
    }

    @Test
    fun `contains returns true after put`() {
        cache.put("key1", "content")
        assertTrue(cache.contains("key1"))
    }

    @Test
    fun `contains returns false for missing key`() {
        assertFalse(cache.contains("missing"))
    }

    @Test
    fun `put overwrites existing value`() {
        cache.put("key1", "old")
        cache.put("key1", "new")
        assertEquals("new", cache.get("key1"))
    }

    @Test
    fun `invalidateAll removes all entries`() {
        cache.put("key1", "content1")
        cache.put("key2", "content2")
        cache.invalidateAll()

        assertFalse(cache.contains("key1"))
        assertFalse(cache.contains("key2"))
    }

    @Test
    fun `getCacheFilePath returns non-null path`() {
        val path = cache.getCacheFilePath("some:key")
        assertNotNull(path)
        assertTrue(path!!.endsWith(".yml"))
    }

    @Test
    fun `getCacheFilePath returns same path for same key`() {
        val path1 = cache.getCacheFilePath("key")
        val path2 = cache.getCacheFilePath("key")
        assertEquals(path1, path2)
    }

    @Test
    fun `getCacheFilePath returns different paths for different keys`() {
        val path1 = cache.getCacheFilePath("key1")
        val path2 = cache.getCacheFilePath("key2")
        assertNotEquals(path1, path2)
    }

    @Test
    fun `written file is read-only`() {
        cache.put("key1", "content")
        val path = cache.getCacheFilePath("key1") ?: fail("path is null")
        val file = File(path)
        assertTrue(file.exists())
        assertFalse(file.canWrite())
    }

    @Test
    fun `put handles special characters in key`() {
        val key = "gitlab:my-group/my-project:main:/templates/base.yml"
        cache.put(key, "yaml content")
        assertEquals("yaml content", cache.get(key))
    }

    @Test
    fun `isStale returns true for missing entry`() {
        assertTrue(cache.isStale("nonexistent"))
    }

    @Test
    fun `isStale returns false for freshly written entry`() {
        cache.put("key1", "content")
        assertFalse(cache.isStale("key1"))
    }
}

/**
 * Standalone версия IncludeCache для тестирования без IntelliJ Platform.
 * Повторяет логику оригинального класса, но без зависимости от Project.
 */
class IncludeCacheStandalone(private val cacheDir: File) {

    private val ttlMs = 60 * 60 * 1000L

    init {
        cacheDir.mkdirs()
    }

    fun get(key: String): String? {
        val file = cacheFile(key)
        return if (file.exists()) file.readText() else null
    }

    fun put(key: String, content: String) {
        val file = cacheFile(key)
        val tempFile = File(file.parent, "${file.name}.tmp")
        synchronized(tempFile.absolutePath.intern()) {
            try {
                if (file.exists() && !file.canWrite()) file.setWritable(true)
                tempFile.writeText(content)
                if (file.exists()) file.delete()
                tempFile.renameTo(file)
                file.setReadOnly()
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }

    fun contains(key: String): Boolean = cacheFile(key).exists()

    fun isStale(key: String): Boolean {
        val file = cacheFile(key)
        if (!file.exists()) return true
        return System.currentTimeMillis() - file.lastModified() >= ttlMs
    }

    fun getCacheFilePath(key: String): String? = cacheFile(key).absolutePath

    fun invalidateAll() {
        cacheDir.listFiles()?.forEach {
            it.setWritable(true)
            it.delete()
        }
    }

    private fun cacheFile(key: String): File {
        val hash = java.security.MessageDigest.getInstance("MD5")
            .digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$hash.yml")
    }
}