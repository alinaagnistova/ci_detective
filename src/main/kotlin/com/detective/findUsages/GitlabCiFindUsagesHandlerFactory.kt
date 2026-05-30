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

package com.detective.findUsages

import com.detective.util.CACHE_DIR_NAME
import com.detective.util.EXTENDS_KEY
import com.detective.util.GitlabCiUtil
import com.detective.util.GitlabCiUtil.collectAllYamlFiles
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.yaml.psi.YAMLAlias
import org.jetbrains.yaml.psi.YAMLAnchor
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

class GitlabCiFindUsagesHandlerFactory : FindUsagesHandlerFactory() {

    override fun canFindUsages(element: PsiElement): Boolean {
        val file = element.containingFile ?: return false

        if (element is YAMLAnchor || element.parent is YAMLAnchor) {
            return GitlabCiUtil.isGitlabCiFile(file) ||
                    file.virtualFile?.path?.contains(CACHE_DIR_NAME) == true
        }

        if (!GitlabCiUtil.isGitlabCiFile(file)) {
            if (file.virtualFile?.path?.contains(CACHE_DIR_NAME) != true) return false
        }

        val keyValue = element as? YAMLKeyValue
            ?: element.parent as? YAMLKeyValue
            ?: return false
        return keyValue.parent ==
                (keyValue.containingFile as? YAMLFile)?.documents?.firstOrNull()?.topLevelValue
    }

    override fun createFindUsagesHandler(
        element: PsiElement,
        forHighlightUsages: Boolean
    ): FindUsagesHandler {
        val anchor = element as? YAMLAnchor ?: element.parent as? YAMLAnchor
        if (anchor != null) return AnchorFindUsagesHandler(anchor)
        return GitlabCiFindUsagesHandler(element)
    }
}

class AnchorFindUsagesHandler(
    element: PsiElement
) : FindUsagesHandler(element) {

    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in UsageInfo>,
        options: FindUsagesOptions
    ): Boolean {
        val anchor = element as? YAMLAnchor
            ?: element.parent as? YAMLAnchor
            ?: return true
        val anchorName = anchor.name

        val allFiles = collectAllYamlFiles(element)

        allFiles.forEach { yamlFile ->
            ApplicationManager.getApplication().runReadAction {
                PsiTreeUtil.findChildrenOfType(yamlFile, YAMLAlias::class.java).forEach { alias ->
                    if (alias.aliasName == anchorName) processor.process(UsageInfo(alias))
                }
            }
        }

        return true
    }
}

class GitlabCiFindUsagesHandler(
    element: PsiElement
) : FindUsagesHandler(element) {

    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in UsageInfo>,
        options: FindUsagesOptions
    ): Boolean {
        val keyValue = element as? YAMLKeyValue
            ?: element.parent as? YAMLKeyValue
            ?: return true
        val jobName = keyValue.keyText

        val allFiles = collectAllYamlFiles(element)

        allFiles.forEach { yamlFile ->
            ApplicationManager.getApplication().runReadAction {
                PsiTreeUtil.findChildrenOfType(yamlFile, YAMLKeyValue::class.java).forEach { kv ->
                    if (kv.keyText == EXTENDS_KEY) {
                        val scalar = kv.value as? YAMLScalar ?: return@forEach
                        if (scalar.textValue == jobName) processor.process(UsageInfo(scalar))
                    }
                }
            }
        }

        return true
    }
}
