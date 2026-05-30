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

package com.detective.navigation

import com.detective.cache.IncludeCache
import com.detective.messages.CiDetectiveBundle
import com.detective.remote.GitLabApiClient
import com.detective.remote.RemoteIncludeResolver
import com.detective.util.EXTENDS_KEY
import com.detective.util.GITHUB_DOMAIN
import com.detective.util.GitlabCiUtil
import com.detective.util.INCLUDE_FILE_KEY
import com.detective.util.INCLUDE_LOCAL_KEY
import com.detective.util.INCLUDE_REMOTE_KEY
import com.detective.util.INCLUDE_TEMPLATE_KEY
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.yaml.psi.YAMLAlias
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import java.io.File

class GitlabCiGotoDeclarationHandler : GotoDeclarationHandler {
    private val log = Logger.getInstance(GitlabCiGotoDeclarationHandler::class.java)


    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null) return null

        val file = sourceElement.containingFile ?: return null
        if (!GitlabCiUtil.isGitlabCiFile(file)) return null

        log.info("CI-DETECTIVE: hover element=${sourceElement::class.simpleName} " +
                "parent=${sourceElement.parent::class.simpleName} " +
                "parent2=${sourceElement.parent?.parent?.javaClass?.simpleName}")

        val aliasResult = resolveYamlAlias(sourceElement, file)
        if (aliasResult != null) return aliasResult

        val scalar = sourceElement.parent as? YAMLScalar
            ?: (sourceElement as? YAMLScalar)
            ?: sourceElement.parent?.parent as? YAMLScalar
            ?: return null

        val keyValue = scalar.parent as? YAMLKeyValue
            ?: scalar.parent?.parent?.parent as? YAMLKeyValue
            ?: return null

        return when (keyValue.keyText) {
            INCLUDE_LOCAL_KEY -> resolveLocalInclude(scalar, file, sourceElement)
            EXTENDS_KEY -> resolveExtends(scalar, file, sourceElement)
            INCLUDE_FILE_KEY -> resolveGitLabFileInclude(scalar, file, sourceElement)
            INCLUDE_REMOTE_KEY -> resolveRemoteInclude(scalar, file, sourceElement)
            INCLUDE_TEMPLATE_KEY -> resolveTemplateInclude(scalar, file, sourceElement)
            else -> null
        }
    }

    private fun resolveYamlAlias(element: PsiElement, file: PsiFile): Array<PsiElement>? {
        val alias = element as? YAMLAlias
            ?: element.parent as? YAMLAlias
            ?: return null

        val anchorName = alias.aliasName
        val project = element.project

        GitlabCiUtil.findAnchorDefinition(file, anchorName)?.let { return arrayOf(it) }

        (GitlabCiUtil.collectIncludedFiles(file, project) +
                GitlabCiUtil.collectCachedRemoteFiles(file, project))
            .firstNotNullOfOrNull { GitlabCiUtil.findAnchorDefinition(it, anchorName) }
            ?.let { return arrayOf(it) }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                CiDetectiveBundle.progressMessage("progress.loading.remote"),
                true
            ) {
                override fun run(indicator: ProgressIndicator) {
                    GitlabCiUtil.collectAllIncludedFiles(file, project)
                    scheduleRestart(project, file)
                }
            }
        )
        return null
    }

    private fun resolveLocalInclude(
        scalar: YAMLScalar,
        file: PsiFile,
        sourceElement: PsiElement
    ): Array<PsiElement>? {
        val keyValue = scalar.parent as? YAMLKeyValue ?: return null
        if (!GitlabCiUtil.isInsideInclude(keyValue)) return null

        val path = scalar.textValue.trimStart('/')
        if (path.isBlank()) return null

        val baseDir = file.virtualFile?.parent ?: return null
        val targetFile = baseDir.findFileByRelativePath(path) ?: return null
        return PsiManager.getInstance(sourceElement.project).findFile(targetFile)
            ?.let { arrayOf(it) }
    }

    private fun resolveGitLabFileInclude(
        scalar: YAMLScalar,
        file: PsiFile,
        sourceElement: PsiElement
    ): Array<PsiElement>? {
        val fileKeyValue = scalar.parent as? YAMLKeyValue
            ?: scalar.parent?.parent?.parent as? YAMLKeyValue
            ?: return null

        if (!GitlabCiUtil.isInsideInclude(fileKeyValue)) return null

        val filePath = scalar.textValue.ifBlank { return null }
        val info = GitlabCiUtil.extractGitLabFileCacheInfo(fileKeyValue, filePath) ?: return null

        val project = sourceElement.project
        val cache = IncludeCache.getInstance(project)

        loadFromCacheOrNull(info.cacheKey, cache, project, file)?.let { return it }

        if (!GitLabApiClient.isTokenConfigured()) {
            showTokenWarningOnce(project)
            return null
        }

        val cacheFilePath = cache.getCacheFilePath(info.cacheKey) ?: return null
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                CiDetectiveBundle.progressMessage("progress.loading.include"),
                true
            ) {
                override fun run(indicator: ProgressIndicator) {
                    RemoteIncludeResolver.resolveGitLabFile(
                        project, info.projectPath, info.filePath, info.ref
                    ) ?: return
                    refreshAndRestart(cacheFilePath, project, file)
                }
            }
        )
        return null
    }

    private fun resolveRemoteInclude(
        scalar: YAMLScalar,
        file: PsiFile,
        sourceElement: PsiElement
    ): Array<PsiElement>? {
        val keyValue = scalar.parent as? YAMLKeyValue ?: return null
        if (!GitlabCiUtil.isInsideInclude(keyValue)) return null

        val url = scalar.textValue.ifBlank { return null }
        val project = sourceElement.project
        val cache = IncludeCache.getInstance(project)

        loadFromCacheOrNull(url, cache, project, file)?.let { return it }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                CiDetectiveBundle.progressMessage("progress.loading.remote"),
                true
            ) {
                override fun run(indicator: ProgressIndicator) {
                    val content = if (url.contains(GITHUB_DOMAIN)) {
                        RemoteIncludeResolver.resolveGitHubFile(project, url)
                    } else {
                        RemoteIncludeResolver.resolveRemoteUrl(project, url)
                    } ?: return

                    val cacheFilePath = cache.getCacheFilePath(url) ?: return
                    cache.put(url, content)
                    refreshAndRestart(cacheFilePath, project, file)
                }
            }
        )
        return null
    }

    private fun resolveTemplateInclude(
        scalar: YAMLScalar,
        file: PsiFile,
        sourceElement: PsiElement
    ): Array<PsiElement>? {
        val keyValue = scalar.parent as? YAMLKeyValue ?: return null
        if (!GitlabCiUtil.isInsideInclude(keyValue)) return null

        val templateName = scalar.textValue.ifBlank { return null }
        val project = sourceElement.project
        val cache = IncludeCache.getInstance(project)
        val cacheKey = GitlabCiUtil.templateCacheKey(templateName)

        loadFromCacheOrNull(cacheKey, cache, project, file)?.let { return it }

        if (!GitLabApiClient.isTokenConfigured()) {
            showTokenWarningOnce(project)
            return null
        }

        val cacheFilePath = cache.getCacheFilePath(cacheKey) ?: return null
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                CiDetectiveBundle.progressMessage("progress.loading.template"),
                true
            ) {
                override fun run(indicator: ProgressIndicator) {
                    val content = RemoteIncludeResolver.resolveTemplate(project, templateName) ?: return
                    cache.put(cacheKey, content)
                    refreshAndRestart(cacheFilePath, project, file)
                }
            }
        )
        return null
    }

    private fun resolveExtends(
        scalar: YAMLScalar,
        file: PsiFile,
        sourceElement: PsiElement
    ): Array<PsiElement>? {
        val jobName = scalar.textValue.ifBlank { return null }
        val project = sourceElement.project

        val localFiles = listOf(file) + GitlabCiUtil.collectIncludedFiles(file, project)
        localFiles.firstNotNullOfOrNull { GitlabCiUtil.findJobInFile(it as? YAMLFile, jobName) }
            ?.let { return arrayOf(it) }

        GitlabCiUtil.collectAllCachedFilesRecursive(file, project)
            .firstNotNullOfOrNull { GitlabCiUtil.findJobInFile(it as? YAMLFile, jobName) }
            ?.let { return arrayOf(it) }

        if (!GitLabApiClient.isTokenConfigured()) {
            showTokenWarningOnce(project)
            return null
        }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(
                project,
                CiDetectiveBundle.progressMessage("progress.loading.includes"),
                true
            ) {
                override fun run(indicator: ProgressIndicator) {
                    ApplicationManager.getApplication().runReadAction {
                        GitlabCiUtil.collectAllIncludedFiles(file, project)
                    }
                    scheduleRestart(project, file)
                }
            }
        )
        return null
    }

    private fun loadFromCacheOrNull(
        cacheKey: String,
        cache: IncludeCache,
        project: Project,
        sourceFile: PsiFile
    ): Array<PsiElement>? {
        if (!cache.contains(cacheKey) || cache.isStale(cacheKey)) return null

        val cacheFilePath = cache.getCacheFilePath(cacheKey) ?: return null
        val ioFile = File(cacheFilePath)
        if (!ioFile.exists()) return null

        if (ioFile.canWrite()) ioFile.setReadOnly()

        val virtualFile = LocalFileSystem.getInstance().findFileByPath(cacheFilePath)
        if (virtualFile != null) {
            return PsiManager.getInstance(project).findFile(virtualFile)?.let { arrayOf(it) }
        }

        ApplicationManager.getApplication().invokeLater {
            LocalFileSystem.getInstance().refreshAndFindFileByPath(cacheFilePath)
            if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart(sourceFile)
        }
        return null
    }

    private fun refreshAndRestart(cacheFilePath: String, project: Project, file: PsiFile) {
        ApplicationManager.getApplication().invokeLater {
            LocalFileSystem.getInstance().refreshAndFindFileByPath(cacheFilePath)
            if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart(file)
        }
    }

    private fun scheduleRestart(project: Project, file: PsiFile) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart(file)
        }
    }

    companion object {
        private const val TOKEN_WARNING_COOLDOWN_MS = 30_000L
        private const val PLUGIN_NAME = "CI Detective for Gitlab"
        private const val NOTIFICATION_GROUP = "CI Detective"

        @Volatile
        private var lastTokenWarningTime = 0L

        fun showTokenWarningOnce(project: Project) {
            synchronized(GitlabCiGotoDeclarationHandler::class.java) {
                val now = System.currentTimeMillis()
                if (now - lastTokenWarningTime < TOKEN_WARNING_COOLDOWN_MS) return
                lastTokenWarningTime = now
            }
            ApplicationManager.getApplication().invokeLater {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP)
                    .createNotification(
                        CiDetectiveBundle.progressMessage("notification.token.missing.title"),
                        buildWarningText(),
                        NotificationType.WARNING
                    )
                    .notify(project)
            }
        }

        fun buildWarningText(): String = buildString {
            append(CiDetectiveBundle.message("notification.token.missing.text"))
            append(" <b>")
            append(CiDetectiveBundle.message("menu.settings"))
            append(" \u2192 ")
            append(CiDetectiveBundle.message("menu.tools"))
            append(" \u2192 ")
            append(PLUGIN_NAME)
            append("</b>")
        }
    }
}