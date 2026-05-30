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
import com.detective.util.EXTENDS_KEY
import com.detective.util.GitlabCiUtil
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YamlPsiElementVisitor
import java.io.File
import com.intellij.psi.PsiFile

class MissingExtendsJobInspection : LocalInspectionTool() {

    override fun getShortName() = "MissingExtendsJobInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : YamlPsiElementVisitor() {
            override fun visitKeyValue(keyValue: YAMLKeyValue) {
                val file = keyValue.containingFile ?: return
                if (!GitlabCiUtil.isGitlabCiFile(file)) return
                if (keyValue.keyText != EXTENDS_KEY) return

                val scalar = keyValue.value as? YAMLScalar ?: return
                val jobName = scalar.textValue
                if (jobName.isBlank()) return

                if (hasUncachedRemoteIncludes(file)) return

                val yamlFile = file as? YAMLFile ?: return
                val allFiles = listOf(file) +
                        GitlabCiUtil.collectAllCachedFilesRecursive(yamlFile, keyValue.project)

                if (allFiles.none { GitlabCiUtil.findJobInFile(it as? YAMLFile, jobName) != null }) {
                    holder.registerProblem(
                        scalar,
                        CiDetectiveBundle.message("inspection.extends.not.found", jobName)
                    )
                }
            }
        }
    }

    private fun hasUncachedRemoteIncludes(file: PsiFile): Boolean {
        val cache = IncludeCache.getInstance(file.project)
        return GitlabCiUtil.extractGitLabFileIncludes(file).any { include ->
            val cacheKey = GitlabCiUtil.gitlabCacheKey(
                include.projectPath,
                include.ref,
                include.filePath
            )
            val cacheFilePath = cache.getCacheFilePath(cacheKey) ?: return@any true
            if (!File(cacheFilePath).exists()) return@any true
            LocalFileSystem.getInstance().findFileByPath(cacheFilePath) == null
        }
    }
}