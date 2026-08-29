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

package com.detective.util

import com.detective.background.IncludeIndexingTask
import com.detective.cache.IncludeCache
import com.detective.remote.GitLabComponentParser
import com.detective.remote.RemoteIncludeResolver
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.psi.YAMLAnchor
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import java.io.File

object GitlabCiUtil {

    private val log = Logger.getInstance(IncludeIndexingTask::class.java)

    private val GITLAB_CI_FILENAMES = setOf(".gitlab-ci.yml", ".gitlab-ci.yaml")

    fun isGitlabCiFile(file: PsiFile): Boolean {
        if (GITLAB_CI_FILENAMES.any { file.name.endsWith(it)}) return true
        val path = file.virtualFile?.path ?: return false
        return path.contains(CACHE_DIR_NAME)
    }

    fun isInsideInclude(element: PsiElement): Boolean {
        var parent = element.parent
        while (parent != null) {
            if (parent is YAMLKeyValue && parent.keyText == INCLUDE_KEY) return true
            parent = parent.parent
        }
        return false
    }

    fun findJobInFile(yamlFile: YAMLFile?, jobName: String): YAMLKeyValue? {
        if (yamlFile == null) return null
        return yamlFile.documents
            .flatMap { doc ->
                PsiTreeUtil.findChildrenOfType(doc, YAMLKeyValue::class.java)
                    .filter { it.parent == doc.topLevelValue }
            }
            .firstOrNull { it.keyText == jobName }
    }

    fun collectIncludedFiles(file: PsiFile, project: Project): List<PsiFile> {
        val result = mutableListOf<PsiFile>()
        val baseDir = file.virtualFile?.parent ?: return result

        PsiTreeUtil.findChildrenOfType(file, YAMLKeyValue::class.java)
            .filter { it.keyText == INCLUDE_LOCAL_KEY && isInsideInclude(it) }
            .forEach { kv ->
                val path = (kv.value as? YAMLScalar)?.textValue?.trimStart('/') ?: return@forEach
                val vFile = baseDir.findFileByRelativePath(path) ?: return@forEach
                PsiManager.getInstance(project).findFile(vFile)?.let { result.add(it) }
            }

        return result
    }

    fun collectAllIncludedFiles(file: PsiFile, project: Project): List<PsiFile> {
        val result = mutableListOf<PsiFile>()
        val cache = IncludeCache.getInstance(project)

        result.addAll(collectIncludedFiles(file, project))
        result.addAll(collectRemoteGitLabFiles(file, project))

        PsiTreeUtil.findChildrenOfType(file, YAMLKeyValue::class.java)
            .filter { it.keyText == INCLUDE_REMOTE_KEY && isInsideInclude(it) }
            .forEach { kv ->
                val url = (kv.value as? YAMLScalar)?.textValue ?: return@forEach
                if (url.contains(GITHUB_DOMAIN)) {
                    RemoteIncludeResolver.resolveGitHubFile(project, url)
                } else {
                    RemoteIncludeResolver.resolveRemoteUrl(project, url)
                } ?: return@forEach
                openFromCache(cache, url, project)?.let { result.add(it) }
            }

        PsiTreeUtil.findChildrenOfType(file, YAMLKeyValue::class.java)
            .filter { it.keyText == INCLUDE_TEMPLATE_KEY && isInsideInclude(it) }
            .forEach { kv ->
                val templateName = (kv.value as? YAMLScalar)?.textValue ?: return@forEach
                RemoteIncludeResolver.resolveTemplate(project, templateName) ?: return@forEach
                openFromCache(cache, templateCacheKey(templateName), project)
                    ?.let { result.add(it) }
            }

        PsiTreeUtil.findChildrenOfType(file, YAMLKeyValue::class.java)
            .filter { it.keyText == INCLUDE_COMPONENT_KEY && isInsideInclude(it) }
            .forEach { kv ->
                val componentString = (kv.value as? YAMLScalar)?.textValue ?: return@forEach
                RemoteIncludeResolver.resolveComponent(project, componentString) ?: return@forEach
                openFromCache(cache, componentCacheKey(componentString), project)
                    ?.let { result.add(it) }
            }

        return result
    }

    data class GitLabIncludeFile(
        val projectPath: String,
        val filePath: String,
        val ref: String = MAIN_KEY
    )

    fun extractGitLabFileIncludes(file: PsiFile): List<GitLabIncludeFile> {
        val result = mutableListOf<GitLabIncludeFile>()

        PsiTreeUtil.findChildrenOfType(file, YAMLMapping::class.java)
            .filter { mapping -> isInsideInclude(mapping) }
            .forEach { mapping ->
                val keyValues = mapping.keyValues
                val projectPath = keyValues.find { it.keyText == PROJECT_KEY }
                    ?.let { (it.value as? YAMLScalar)?.textValue } ?: return@forEach
                val ref = keyValues.find { it.keyText == REF_KEY }
                    ?.let { (it.value as? YAMLScalar)?.textValue } ?: MAIN_KEY

                val fileKv = keyValues.find { it.keyText == INCLUDE_FILE_KEY } ?: return@forEach

                when (val fileValue = fileKv.value) {
                    is YAMLScalar -> {
                        result.add(GitLabIncludeFile(projectPath, fileValue.textValue, ref))
                    }

                    is YAMLSequence -> {
                        fileValue.items.forEach { item ->
                            val filePath = (item.value as? YAMLScalar)?.textValue ?: return@forEach
                            result.add(GitLabIncludeFile(projectPath, filePath, ref))
                        }
                    }
                }
            }
        return result
    }

    fun collectRemoteGitLabFiles(file: PsiFile, project: Project): List<PsiFile> {
        val result = mutableListOf<PsiFile>()
        val cache = IncludeCache.getInstance(project)

        extractGitLabFileIncludes(file).forEach { include ->
            val cacheKey = gitlabCacheKey(include.projectPath, include.ref, include.filePath)
            RemoteIncludeResolver.resolveGitLabFile(
                project = project,
                projectPath = include.projectPath,
                filePath = include.filePath,
                ref = include.ref
            ) ?: return@forEach
            openFromCache(cache, cacheKey, project)?.let { result.add(it) }
        }

        return result
    }

    fun collectAllIncludedFilesRecursive(
        file: PsiFile,
        project: Project,
        visitedPaths: MutableSet<String> = mutableSetOf(),
        depth: Int = 0
    ): List<PsiFile> {
        if (depth >= MAX_DEPTH) return emptyList()

        val filePath = file.virtualFile?.path ?: file.name
        if (!visitedPaths.add(filePath)) return emptyList()

        val result = mutableListOf<PsiFile>()
        val directIncludes = collectAllIncludedFiles(file, project)
        result.addAll(directIncludes)
        directIncludes.forEach { includedFile ->
            result.addAll(
                collectAllIncludedFilesRecursive(
                    file = includedFile,
                    project = project,
                    visitedPaths = visitedPaths,
                    depth = depth + 1
                )
            )
        }

        return result
    }

    fun findAnchorDefinition(file: PsiFile, anchorName: String): PsiElement? =
        PsiTreeUtil.findChildrenOfType(file, YAMLAnchor::class.java)
            .firstOrNull { it.name == anchorName }

    fun collectAnchors(file: PsiFile): List<YAMLAnchor> =
        PsiTreeUtil.findChildrenOfType(file, YAMLAnchor::class.java).toList()

    fun collectCachedRemoteFiles(file: PsiFile, project: Project): List<PsiFile> {
        val result = mutableListOf<PsiFile>()
        val cache = IncludeCache.getInstance(project)

        fun openIfCached(cacheKey: String) {
            val cachedFilePath = cache.getCacheFilePath(cacheKey) ?: return
            if (!File(cachedFilePath).exists()) return
            val virtualFile = LocalFileSystem.getInstance()
                .findFileByPath(cachedFilePath) ?: return
            PsiManager.getInstance(project).findFile(virtualFile)?.let { result.add(it) }
        }

        extractGitLabFileIncludes(file).forEach { include ->
            openIfCached(gitlabCacheKey(include.projectPath, include.ref, include.filePath))
        }

        PsiTreeUtil.findChildrenOfType(file, YAMLKeyValue::class.java)
            .filter { it.keyText == INCLUDE_REMOTE_KEY && isInsideInclude(it) }
            .forEach { kv ->
                val url = (kv.value as? YAMLScalar)?.textValue ?: return@forEach
                openIfCached(url)
            }

        PsiTreeUtil.findChildrenOfType(file, YAMLKeyValue::class.java)
            .filter { it.keyText == INCLUDE_TEMPLATE_KEY && isInsideInclude(it) }
            .forEach { kv ->
                val templateName = (kv.value as? YAMLScalar)?.textValue ?: return@forEach
                openIfCached(templateCacheKey(templateName))
            }

        PsiTreeUtil.findChildrenOfType(file, YAMLKeyValue::class.java)
            .filter { it.keyText == INCLUDE_COMPONENT_KEY && isInsideInclude(it) }
            .forEach { kv ->
                val componentString = (kv.value as? YAMLScalar)?.textValue ?: return@forEach
                openIfCached(componentCacheKey(componentString))
            }

        return result
    }

    data class GitLabFileCacheInfo(
        val projectPath: String,
        val ref: String,
        val filePath: String,
        val cacheKey: String
    )

    fun extractGitLabFileCacheInfo(keyValue: YAMLKeyValue, filePath: String): GitLabFileCacheInfo? {
        val mapping = keyValue.parent as? YAMLMapping ?: return null
        val projectPath = mapping.keyValues.find { it.keyText == PROJECT_KEY }
            ?.let { (it.value as? YAMLScalar)?.textValue } ?: return null
        val ref = mapping.keyValues.find { it.keyText == REF_KEY }
            ?.let { (it.value as? YAMLScalar)?.textValue } ?: MAIN_KEY

        return GitLabFileCacheInfo(
            projectPath = projectPath,
            ref = ref,
            filePath = filePath,
            cacheKey = gitlabCacheKey(projectPath, ref, filePath)
        )
    }

    fun collectAllYamlFiles(element: PsiElement): List<YAMLFile> {
        val allFiles = mutableListOf<YAMLFile>()

        ApplicationManager.getApplication().runReadAction {
            val project = element.project
            val psiManager = PsiManager.getInstance(project)

            ProjectRootManager.getInstance(project)
                .contentRoots
                .forEach { root ->
                    root.children.forEach { vFile ->
                        val psiFile = psiManager.findFile(vFile) ?: return@forEach
                        if (isGitlabCiFile(psiFile)) {
                            (psiFile as? YAMLFile)?.let { allFiles.add(it) }
                        }
                    }
                }

            val cacheDir = File(project.basePath ?: return@runReadAction, CACHE_DIR_PATH)
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { cachedFile ->
                    val vFile = LocalFileSystem.getInstance()
                        .findFileByPath(cachedFile.absolutePath) ?: return@forEach
                    (psiManager.findFile(vFile) as? YAMLFile)?.let { allFiles.add(it) }
                }
            }
        }

        return allFiles
    }

    fun collectAllCachedFilesRecursive(
        file: PsiFile,
        project: Project,
        visited: MutableSet<String> = mutableSetOf(),
        depth: Int = 0
    ): List<PsiFile> {
        if (depth >= MAX_DEPTH) return emptyList()

        val filePath = file.virtualFile?.path ?: file.name
        if (!visited.add(filePath)) return emptyList()

        val result = mutableListOf<PsiFile>()

        val localFiles = collectIncludedFiles(file, project)
        result.addAll(localFiles)
        localFiles.filterIsInstance<YAMLFile>().forEach {
            result.addAll(collectAllCachedFilesRecursive(it, project, visited, depth + 1))
        }

        val cachedFiles = collectCachedRemoteFiles(file, project)
        result.addAll(cachedFiles)
        cachedFiles.filterIsInstance<YAMLFile>().forEach {
            result.addAll(collectAllCachedFilesRecursive(it, project, visited, depth + 1))
        }

        return result
    }

    fun gitlabCacheKey(projectPath: String, ref: String, filePath: String) =
        "gitlab:$projectPath:$ref:$filePath"

    fun templateCacheKey(templateName: String) = "template:$templateName"

    fun componentCacheKey(componentString: String): String {
        val ref = GitLabComponentParser.parse(componentString)
        return ref?.cacheKey ?: "component:$componentString"
    }

    private fun openFromCache(cache: IncludeCache, cacheKey: String, project: Project): PsiFile? {
        val cacheFilePath = cache.getCacheFilePath(cacheKey) ?: return null
        val virtualFile = LocalFileSystem.getInstance()
            .findFileByPath(cacheFilePath) ?: return null
        return PsiManager.getInstance(project).findFile(virtualFile)
    }

}