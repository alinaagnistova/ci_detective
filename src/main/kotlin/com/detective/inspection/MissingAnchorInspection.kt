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

package com.detective.inspection

import com.detective.cache.IncludeCache
import com.detective.messages.CiDetectiveBundle
import com.detective.util.GitlabCiUtil
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.psi.YAMLAlias
import org.jetbrains.yaml.psi.YamlPsiElementVisitor
import java.io.File

class MissingAnchorInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : YamlPsiElementVisitor() {
            override fun visitAlias(alias: YAMLAlias) {
                val file = alias.containingFile ?: return
                if (!GitlabCiUtil.isGitlabCiFile(file)) return
                if (!alias.isValid) return

                val anchorName = alias.aliasName ?: return
                if (anchorName.isBlank()) return

                if (isOnTheFly && !isAliasTextValid(alias, anchorName, file)) return

                val project = alias.project
                val cachedFiles = GitlabCiUtil.collectCachedRemoteFiles(file, project)

                val allFiles = listOf(file) +
                        GitlabCiUtil.collectIncludedFiles(file, project) +
                        cachedFiles
                if (allFiles.any { GitlabCiUtil.findAnchorDefinition(it, anchorName) != null }) return

                if (shouldSuppressDueToUncachedRemotes(file, cachedFiles)) return

                holder.registerProblem(
                    alias,
                    CiDetectiveBundle.message("inspection.anchor.not.defined", anchorName)
                )
            }
        }
    }

    private fun isAliasTextValid(alias: YAMLAlias, anchorName: String, file: PsiFile): Boolean {
        return try {
            val textAtPosition = file.text.substring(
                alias.textRange.startOffset,
                alias.textRange.endOffset
            )
            textAtPosition.contains(anchorName)
        } catch (e: Exception) {
            false
        }
    }

    private fun shouldSuppressDueToUncachedRemotes(
        file: PsiFile,
        cachedFiles: List<PsiFile>
    ): Boolean {
        val cache = IncludeCache.getInstance(file.project)
        val cachedFilesHaveAnchors = cachedFiles.any {
            GitlabCiUtil.collectAnchors(it).isNotEmpty()
        }
        val hasUncachedRemote = GitlabCiUtil.extractGitLabFileIncludes(file).any { include ->
            val cacheKey = GitlabCiUtil.gitlabCacheKey(
                include.projectPath,
                include.ref,
                include.filePath
            )
            val cachedFilePath = cache.getCacheFilePath(cacheKey)
            cachedFilePath == null || !File(cachedFilePath).exists()
        }
        return hasUncachedRemote && cachedFilesHaveAnchors
    }
}