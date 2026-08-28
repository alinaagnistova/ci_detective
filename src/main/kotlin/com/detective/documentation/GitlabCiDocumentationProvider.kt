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

package com.detective.documentation

import com.detective.cache.IncludeCache
import com.detective.messages.CiDetectiveBundle
import com.detective.remote.GitLabComponentParser
import com.detective.util.EXTENDS_KEY
import com.detective.util.GitlabCiUtil
import com.detective.util.INCLUDE_COMPONENT_KEY
import com.detective.util.INCLUDE_FILE_KEY
import com.detective.util.INCLUDE_LOCAL_KEY
import com.detective.util.INCLUDE_REMOTE_KEY
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import java.io.File

class GitlabCiDocumentationProvider : DocumentationProvider {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val source = originalElement ?: element ?: return null
        val file = source.containingFile ?: return null
        if (!GitlabCiUtil.isGitlabCiFile(file)) return null

        val scalar = findScalar(source) ?: return null

        val keyValue = scalar.parent as? YAMLKeyValue
            ?: scalar.parent?.parent?.parent as? YAMLKeyValue

        return when (keyValue?.keyText) {
            INCLUDE_LOCAL_KEY -> generateLocalIncludeDoc(scalar, file)
            INCLUDE_FILE_KEY -> generateGitLabFileDoc(scalar, keyValue!!, file)
            INCLUDE_REMOTE_KEY -> generateRemoteDoc(scalar, file)
            INCLUDE_COMPONENT_KEY -> generateComponentDoc(scalar, file)
            EXTENDS_KEY -> generateExtendsDoc(scalar, file)
            else -> null
        }
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? =
        generateDoc(element, originalElement)

    private fun findScalar(element: PsiElement): YAMLScalar? {
        var current: PsiElement? = element
        repeat(PSI_LOOKUP_DEPTH) {
            if (current is YAMLScalar) return current as YAMLScalar
            current = current?.parent
        }
        return null
    }

    private fun generateLocalIncludeDoc(scalar: YAMLScalar, file: PsiElement): String? {
        val path = scalar.textValue.trimStart('/').ifBlank { return null }
        val baseDir = file.containingFile?.virtualFile?.parent ?: return null
        val targetVFile = baseDir.findFileByRelativePath(path)
            ?: return buildNotFoundDoc(CiDetectiveBundle.message("doc.file.not.found", path))

        val psiFile = PsiManager.getInstance(file.project).findFile(targetVFile) as? YAMLFile
            ?: return null

        return buildIncludeDoc(
            title = CiDetectiveBundle.message("doc.title.local"),
            subtitle = path,
            jobs = extractJobNames(psiFile)
        )
    }

    private fun generateGitLabFileDoc(
        scalar: YAMLScalar,
        keyValue: YAMLKeyValue,
        file: PsiElement
    ): String? {
        val filePath = scalar.textValue.ifBlank { return null }
        val info = GitlabCiUtil.extractGitLabFileCacheInfo(keyValue, filePath) ?: return null
        val jobs = extractJobsFromCache(info.cacheKey, file)

        return buildIncludeDoc(
            title = CiDetectiveBundle.message("doc.title.gitlab"),
            subtitle = "${info.projectPath} @ ${info.ref}",
            extra = info.filePath,
            jobs = jobs
        )
    }

    private fun generateRemoteDoc(scalar: YAMLScalar, file: PsiElement): String? {
        val url = scalar.textValue.ifBlank { return null }
        val jobs = extractJobsFromCache(url, file)

        return buildIncludeDoc(
            title = CiDetectiveBundle.message("doc.title.remote"),
            subtitle = url.substringAfterLast("/"),
            extra = url,
            jobs = jobs
        )
    }

    private fun generateComponentDoc(scalar: YAMLScalar, file: PsiElement): String? {
        val componentString = scalar.textValue.ifBlank { return null }
        val ref = GitLabComponentParser.parse(componentString)
            ?: return buildNotFoundDoc(CiDetectiveBundle.message("doc.component.not.found", componentString))

        val jobs = extractJobsFromCache(ref.cacheKey, file)

        return buildIncludeDoc(
            title = CiDetectiveBundle.message("doc.title.component"),
            subtitle = "${ref.projectPath}/${ref.componentName}",
            extra = "@${ref.version}",
            jobs = jobs
        )
    }

    private fun generateExtendsDoc(scalar: YAMLScalar, file: PsiElement): String? {
        val jobName = scalar.textValue.ifBlank { return null }
        val project = file.project

        val allFiles = listOf(file.containingFile) +
                GitlabCiUtil.collectAllIncludedFilesRecursive(file.containingFile, project)

        val jobDefinition = allFiles.firstNotNullOfOrNull {
            GitlabCiUtil.findJobInFile(it as? YAMLFile, jobName)
        } ?: return buildNotFoundDoc(CiDetectiveBundle.message("doc.job.not.found", jobName))

        val preview = jobDefinition.text.lines().take(PREVIEW_MAX_LINES).joinToString("\n")

        return buildString {
            append("<html><body>")
            append("<b>${CiDetectiveBundle.message("doc.job.template")}</b> <code>$jobName</code><br/><br/>")
            append("<pre>${escapeHtml(preview)}</pre>")
            append("</body></html>")
        }
    }

    private fun extractJobsFromCache(cacheKey: String, file: PsiElement): List<String> {
        val cachedFilePath = IncludeCache.getInstance(file.project)
            .getCacheFilePath(cacheKey) ?: return listOf(CiDetectiveBundle.message("doc.not.cached"))

        if (!File(cachedFilePath).exists()) return listOf(CiDetectiveBundle.message("doc.not.cached"))

        val vFile = LocalFileSystem.getInstance().findFileByPath(cachedFilePath)
        val psiFile = vFile?.let {
            PsiManager.getInstance(file.project).findFile(it) as? YAMLFile
        }
        return psiFile?.let { extractJobNames(it) } ?: listOf(CiDetectiveBundle.message("doc.not.cached"))
    }

    private fun extractJobNames(yamlFile: YAMLFile): List<String> {
        return yamlFile.documents
            .flatMap { doc ->
                PsiTreeUtil.findChildrenOfType(doc, YAMLKeyValue::class.java)
                    .filter { it.parent == doc.topLevelValue }
                    .map { it.keyText }
            }
            .filter { it.isNotBlank() }
    }

    private fun buildIncludeDoc(
        title: String,
        subtitle: String,
        extra: String? = null,
        jobs: List<String>
    ): String = buildString {
        append("<html><body>")
        append("<b>$title:</b> <code>${escapeHtml(subtitle)}</code>")
        if (extra != null) append("<br/><small>${escapeHtml(extra)}</small>")
        append("<br/><br/>")
        if (jobs.isEmpty()) {
            append("<i>${CiDetectiveBundle.message("doc.no.jobs")}</i>")
        } else {
            append("<b>${CiDetectiveBundle.message("doc.jobs.defined")}</b><br/>")
            jobs.forEach { append("&bull; <code>${escapeHtml(it)}</code><br/>") }
        }
        append("</body></html>")
    }

    private fun buildNotFoundDoc(message: String) =
        "<html><body><b>⚠️ $message</b></body></html>"

    private fun escapeHtml(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    companion object {
        private const val PSI_LOOKUP_DEPTH = 5
        private const val PREVIEW_MAX_LINES = 20
    }
}