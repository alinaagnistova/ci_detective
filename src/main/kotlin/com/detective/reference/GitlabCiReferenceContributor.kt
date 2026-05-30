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

package com.detective.reference

import com.detective.util.EXTENDS_KEY
import com.detective.util.GitlabCiUtil
import com.detective.util.MAX_DEPTH
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLAlias
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

class GitlabCiReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {

        // extends: .job-name - определение job
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement().withParent(YAMLScalar::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val scalar = element as? YAMLScalar
                        ?: element.parent as? YAMLScalar
                        ?: return emptyArray()
                    val file = element.containingFile ?: return emptyArray()
                    if (!GitlabCiUtil.isGitlabCiFile(file)) return emptyArray()

                    val keyValue = scalar.parent as? YAMLKeyValue ?: return emptyArray()
                    if (keyValue.keyText != EXTENDS_KEY) return emptyArray()

                    val jobName = scalar.textValue
                    if (jobName.isBlank()) return emptyArray()

                    val project = element.project
                    val allFiles = listOf(file) +
                            GitlabCiUtil.collectIncludedFiles(file, project) +
                            collectCachedRecursive(file, project)

                    val target = allFiles.firstNotNullOfOrNull {
                        GitlabCiUtil.findJobInFile(it as? YAMLFile, jobName)
                    } ?: return emptyArray()

                    return arrayOf(ExtendsJobReference(scalar, target))
                }
            }
        )

        // *alias_name - &anchor_name
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(YAMLAlias::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val alias = element as? YAMLAlias ?: return emptyArray()
                    val file = element.containingFile ?: return emptyArray()
                    if (!GitlabCiUtil.isGitlabCiFile(file)) return emptyArray()

                    val anchorName = alias.aliasName
                    val project = element.project

                    val allFiles = listOf(file) +
                            GitlabCiUtil.collectIncludedFiles(file, project) +
                            collectCachedRecursive(file, project)

                    val target = allFiles.firstNotNullOfOrNull {
                        GitlabCiUtil.findAnchorDefinition(it, anchorName)
                    } ?: return emptyArray()

                    return arrayOf(AnchorAliasReference(alias, target))
                }
            }
        )
    }

    private fun collectCachedRecursive(
        file: PsiFile,
        project: Project,
        visited: MutableSet<String> = mutableSetOf(),
        depth: Int = 0
    ): List<PsiFile> {
        if (depth >= MAX_DEPTH) return emptyList()
        val filePath = file.virtualFile?.path ?: file.name
        if (!visited.add(filePath)) return emptyList()

        val result = mutableListOf<PsiFile>()

        val cached = GitlabCiUtil.collectCachedRemoteFiles(file, project)
        result.addAll(cached)
        cached.forEach { result.addAll(collectCachedRecursive(it, project, visited, depth + 1)) }

        val local = GitlabCiUtil.collectIncludedFiles(file, project)
        local.forEach { result.addAll(collectCachedRecursive(it, project, visited, depth + 1)) }

        return result
    }
}

class ExtendsJobReference(
    element: YAMLScalar,
    private val target: PsiElement
) : PsiReferenceBase<YAMLScalar>(element, TextRange(0, element.textLength)) {

    override fun resolve(): PsiElement = target

    override fun getVariants(): Array<Any> = emptyArray()

    override fun isReferenceTo(element: PsiElement): Boolean {
        val resolved = resolve()
        if (resolved == element || resolved.parent == element) return true

        val resolvedKey = resolved as? YAMLKeyValue ?: resolved.parent as? YAMLKeyValue ?: return false
        val elementKey = element as? YAMLKeyValue ?: element.parent as? YAMLKeyValue ?: return false

        return resolvedKey.keyText == elementKey.keyText &&
                resolvedKey.containingFile?.virtualFile?.path == elementKey.containingFile?.virtualFile?.path
    }
}

class AnchorAliasReference(
    element: YAMLAlias,
    private val target: PsiElement
) : PsiReferenceBase<YAMLAlias>(element, TextRange(0, element.textLength)) {

    override fun resolve(): PsiElement = target

    override fun getVariants(): Array<Any> = emptyArray()

    override fun isReferenceTo(element: PsiElement): Boolean =
        resolve() == element || resolve().parent == element
}