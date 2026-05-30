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

import com.detective.settings.GitlabCiSettings
import com.detective.util.CACHE_DIR_NAME
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

@Service(Service.Level.PROJECT)
class IncludeCache(private val project: Project) {

    private val writeScope = CoroutineScope(Dispatchers.IO)

    private val cacheDir: Path by lazy {
        val dir = Paths.get(project.basePath ?: "", ".idea", CACHE_DIR_NAME)
        Files.createDirectories(dir)
        dir
    }

    fun get(url: String): String? {
        val file = cacheFile(url)
        return if (file.exists()) file.readText() else null
    }

    fun put(url: String, content: String) {
        writeToDisk(url, content)
        evictIfNeeded()
    }

    fun putAsync(url: String, content: String) {
        writeScope.launch {
            writeToDisk(url, content)
            evictIfNeeded()
        }
    }

    private fun writeToDisk(url: String, content: String) {
        val file = cacheFile(url)
        val tempFile = File(file.parent, "${file.name}.tmp")
        synchronized(tempFile.absolutePath.intern()) {
            try {
                if (file.exists() && !file.canWrite()) {
                    file.setWritable(true)
                }
                tempFile.writeText(content)
                if (file.exists()) file.delete()
                tempFile.renameTo(file)
                file.setReadOnly()
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }

    fun isStale(url: String): Boolean {
        val file = cacheFile(url)
        if (!file.exists()) return true
        val ttlMs = GitlabCiSettings.getInstance().cacheTtlHours * 60 * 60 * 1000L
        val age = System.currentTimeMillis() - file.lastModified()
        return age >= ttlMs
    }

    fun invalidateAll() {
        val dir = cacheDir.toFile()
        dir.listFiles()?.forEach {
            it.setWritable(true)
            it.delete()
        }
        ApplicationManager.getApplication().invokeLater {
            val vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.absolutePath)
            vDir?.refresh(false, true)
        }
    }

    private fun evictIfNeeded() {
        val files = cacheDir.toFile().listFiles() ?: return
        val totalSize = files.sumOf { it.length() }
        if (totalSize <= MAX_CACHE_SIZE_BYTES) return

        files.sortedBy { it.lastModified() }.forEach { file ->
            val currentSize = cacheDir.toFile()
                .listFiles()?.sumOf { it.length() } ?: 0
            if (currentSize <= MAX_CACHE_SIZE_BYTES) return
            file.setWritable(true)
            file.delete()
        }
    }

    fun contains(url: String): Boolean = cacheFile(url).exists()

    fun getCacheFilePath(url: String): String? {
        return try {
            cacheFile(url).absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun cacheFile(url: String): File {
        val hash = MessageDigest.getInstance(HASH_ALGORITHM)
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return cacheDir.resolve("$hash.yml").toFile()
    }

    companion object {
        private const val MAX_CACHE_SIZE_BYTES = 100L * 1024 * 1024
        private const val HASH_ALGORITHM = "MD5"

        fun getInstance(project: Project): IncludeCache = project.service()
    }
}