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

package com.detective.background

import com.detective.cache.IncludeCache
import com.detective.messages.CiDetectiveBundle
import com.detective.remote.GitLabComponentParser
import com.detective.remote.RemoteIncludeResolver
import com.detective.util.CACHE_DIR_PATH
import com.detective.util.GITHUB_DOMAIN
import com.detective.util.GitlabCiUtil
import com.detective.util.INCLUDE_COMPONENT_KEY
import com.detective.util.INCLUDE_FILE_KEY
import com.detective.util.INCLUDE_KEY
import com.detective.util.INCLUDE_REMOTE_KEY
import com.detective.util.MAIN_KEY
import com.detective.util.MAX_DEPTH
import com.detective.util.PROJECT_KEY
import com.detective.util.REF_KEY
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import java.io.File
import com.intellij.psi.PsiElement

class IncludeIndexingTask(
    project: Project,
    private val psiFile: PsiFile
) : Task.Backgroundable(
    project,
    CiDetectiveBundle.progressMessage("progress.indexing"),
    true
) {
    private val log = Logger.getInstance(IncludeIndexingTask::class.java)

    override fun run(indicator: ProgressIndicator) {
        val start = System.currentTimeMillis()
        indicator.isIndeterminate = true
        indicator.text = CiDetectiveBundle.progressMessage("progress.scanning")

        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<RemoteInclude>()
        val totalLoaded = mutableListOf<RemoteInclude>()

        ApplicationManager.getApplication().runReadAction {
            collectRemoteIncludesFromFile(psiFile, queue, visited)
        }

        while (queue.isNotEmpty()) {
            if (indicator.isCanceled) return

            val include = queue.removeFirst()
            totalLoaded.add(include)

            indicator.isIndeterminate = false
            indicator.text = CiDetectiveBundle.progressMessage("progress.loading", include.displayName)

            val content = downloadAndCache(include) ?: continue

            if (include.depth < MAX_DEPTH) {
                ApplicationManager.getApplication().runReadAction {
                    parseYamlContent(content, include.displayName)
                        ?.let { collectIncludesFromYaml(it, queue, visited, include.depth + 1) }
                }
            }
        }

        ApplicationManager.getApplication().invokeLater {
            refreshCacheDir()
            restartDaemon()
        }

        val elapsed = System.currentTimeMillis() - start
        log.info("CI-DETECTIVE: indexing completed in ${elapsed}ms for ${totalLoaded.size} includes")
    }

    private fun refreshCacheDir() {
        val cacheDir = File(project.basePath ?: return, CACHE_DIR_PATH)
        LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(cacheDir.absolutePath)
            ?.refresh(false, true)
    }

    private fun restartDaemon() {
        if (project.isDisposed) return
        val pf = PsiManager.getInstance(project)
            .findFile(psiFile.virtualFile ?: return) ?: return
        DaemonCodeAnalyzer.getInstance(project).restart(pf)
    }

    private fun downloadAndCache(include: RemoteInclude): String? {
        val cache = IncludeCache.getInstance(project)
        synchronized(include.cacheKey.intern()) {
            if (cache.contains(include.cacheKey) && !cache.isStale(include.cacheKey)) {
                return cache.get(include.cacheKey)
            }

            val content = when (include) {
                is RemoteInclude.GitLabFile -> RemoteIncludeResolver.resolveGitLabFile(
                    project, include.projectPath, include.filePath, include.ref
                )

                is RemoteInclude.RemoteUrl -> if (include.url.contains(GITHUB_DOMAIN)) {
                    RemoteIncludeResolver.resolveGitHubFile(project, include.url)
                } else {
                    RemoteIncludeResolver.resolveRemoteUrl(project, include.url)
                }
            } ?: return null

            val cacheFilePath = cache.getCacheFilePath(include.cacheKey) ?: return null
            cache.putAsync(include.cacheKey, content)
            ApplicationManager.getApplication().invokeLater {
                LocalFileSystem.getInstance().refreshAndFindFileByPath(cacheFilePath)
            }
            return content
        }
    }

    private fun parseYamlContent(content: String, name: String): PsiFile? {
        return try {
            val fileName = name.substringAfterLast("/")
                .ifBlank { DEFAULT_YAML_NAME }
                .let { if (it.endsWith(".yml") || it.endsWith(".yaml")) it else "$it.yml" }
            PsiFileFactory.getInstance(project).createFileFromText(fileName, YAMLFileType.YML, content)
        } catch (e: Exception) {
            log.warn("CI-DETECTIVE: failed to parse yaml content for $name", e)
            null
        }
    }

    private fun getTextValue(kv: YAMLKeyValue): String? {
        val value = kv.value ?: return null
        if (value is YAMLScalar) return value.textValue
        return value.text?.trim()
            ?.removeSurrounding("'")
            ?.removeSurrounding("\"")
            ?.ifBlank { null }
    }

    private fun collectRemoteIncludesFromFile(
        file: PsiFile,
        queue: ArrayDeque<RemoteInclude>,
        visited: MutableSet<String>,
        depth: Int = 0
    ) {
        if (depth >= MAX_DEPTH) return

        GitlabCiUtil.extractGitLabFileIncludes(file).forEach { include ->
            val cacheKey = "gitlab:${include.projectPath}:${include.ref}:${include.filePath}"
            if (visited.add(cacheKey)) {
                queue.add(
                    RemoteInclude.GitLabFile(
                        projectPath = include.projectPath,
                        filePath = include.filePath,
                        ref = include.ref,
                        depth = depth
                    )
                )
            }
        }

        PsiTreeUtil.findChildrenOfType(file, YAMLKeyValue::class.java)
            .filter { it.keyText == INCLUDE_REMOTE_KEY && GitlabCiUtil.isInsideInclude(it) }
            .forEach { kv ->
                val url = getTextValue(kv) ?: return@forEach
                if (visited.add(url)) queue.add(RemoteInclude.RemoteUrl(url = url, depth = depth))
            }

        PsiTreeUtil.findChildrenOfType(file, YAMLKeyValue::class.java)
            .filter { it.keyText == INCLUDE_COMPONENT_KEY && GitlabCiUtil.isInsideInclude(it) }
            .forEach { kv ->
                val componentString = getTextValue(kv) ?: return@forEach
                val ref = GitLabComponentParser.parse(componentString) ?: return@forEach
                if (visited.add(ref.cacheKey)) {
                    queue.add(
                        RemoteInclude.GitLabFile(
                            projectPath = ref.projectPath,
                            filePath = ref.filePath,
                            ref = ref.version.removePrefix("~"),
                            depth = depth
                        )
                    )
                }
            }

        GitlabCiUtil.collectIncludedFiles(file, file.project)
            .forEach { collectRemoteIncludesFromFile(it, queue, visited, depth + 1) }
    }

    private fun collectIncludesFromYaml(
        file: PsiFile,
        queue: ArrayDeque<RemoteInclude>,
        visited: MutableSet<String>,
        depth: Int
    ) {
        if (depth >= MAX_DEPTH) return

        PsiTreeUtil.findChildrenOfType(file, YAMLMapping::class.java)
            .forEach { mapping ->
                val keyValues = mapping.keyValues
                if (!keyValues.any { it.keyText == PROJECT_KEY }) return@forEach
                if (!keyValues.any { it.keyText == INCLUDE_FILE_KEY }) return@forEach
                if (!isInsideIncludeBlock(mapping)) return@forEach

                val projectPath = getTextValue(keyValues.find { it.keyText == PROJECT_KEY } ?: return@forEach)
                    ?: return@forEach
                val filePath = getTextValue(keyValues.find { it.keyText == INCLUDE_FILE_KEY } ?: return@forEach)
                    ?: return@forEach
                val ref = keyValues.find { it.keyText == REF_KEY }?.let { getTextValue(it) } ?: MAIN_KEY

                val cacheKey = GitlabCiUtil.gitlabCacheKey(projectPath, ref, filePath)
                if (visited.add(cacheKey)) {
                    queue.add(
                        RemoteInclude.GitLabFile(
                            projectPath = projectPath,
                            filePath = filePath,
                            ref = ref,
                            depth = depth
                        )
                    )
                }
            }

        PsiTreeUtil.findChildrenOfType(file, YAMLKeyValue::class.java)
            .filter { it.keyText == INCLUDE_REMOTE_KEY && isInsideIncludeBlock(it) }
            .forEach { kv ->
                val url = getTextValue(kv) ?: return@forEach
                if (visited.add(url)) queue.add(RemoteInclude.RemoteUrl(url = url, depth = depth))
            }
    }

    private fun isInsideIncludeBlock(element: PsiElement): Boolean {
        var parent = element.parent
        while (parent != null) {
            if (parent is YAMLKeyValue && parent.keyText == INCLUDE_KEY) return true
            parent = parent.parent
        }
        return false
    }

    private fun writeToCacheFile(cacheFilePath: String, content: String) {
        val ioFile = File(cacheFilePath)
        try {
            if (ioFile.exists() && !ioFile.canWrite()) ioFile.setWritable(true)
            ioFile.writeText(content)
            ioFile.setReadOnly()
        } catch (e: Exception) {
            log.warn("CI-DETECTIVE: failed to write cache file: $cacheFilePath", e)
        }
    }

    companion object {
        private const val DEFAULT_YAML_NAME = "remote.yml"

        fun schedule(project: Project, psiFile: PsiFile) {
            ProgressManager.getInstance().run(IncludeIndexingTask(project, psiFile))
        }
    }
}

sealed class RemoteInclude {
    abstract val cacheKey: String
    abstract val displayName: String
    abstract val depth: Int

    data class GitLabFile(
        val projectPath: String,
        val filePath: String,
        val ref: String = MAIN_KEY,
        override val depth: Int = 0
    ) : RemoteInclude() {
        override val cacheKey = GitlabCiUtil.gitlabCacheKey(projectPath, ref, filePath)
        override val displayName = "$projectPath$filePath"
    }

    data class RemoteUrl(
        val url: String,
        override val depth: Int = 0
    ) : RemoteInclude() {
        override val cacheKey = url
        override val displayName = url.substringAfterLast("/")
    }
}