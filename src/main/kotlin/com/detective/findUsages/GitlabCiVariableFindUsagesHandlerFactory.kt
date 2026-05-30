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

import com.detective.util.GitlabCiUtil
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

class GitlabCiVariableFindUsagesHandlerFactory : FindUsagesHandlerFactory() {

    override fun canFindUsages(element: PsiElement): Boolean {
        val file = element.containingFile ?: return false
        if (!GitlabCiUtil.isGitlabCiFile(file)) return false

        val keyValue = element as? YAMLKeyValue
            ?: element.parent as? YAMLKeyValue
            ?: return false
        return isInsideVariables(keyValue)
    }

    override fun createFindUsagesHandler(
        element: PsiElement,
        forHighlightUsages: Boolean
    ): FindUsagesHandler = GitlabCiVariableFindUsagesHandler(element)

    private fun isInsideVariables(element: PsiElement): Boolean {
        var parent = element.parent
        while (parent != null) {
            if (parent is YAMLKeyValue && parent.keyText == VARIABLES_KEY) return true
            parent = parent.parent
        }
        return false
    }

    companion object {
        private const val VARIABLES_KEY = "variables"
    }
}

class GitlabCiVariableFindUsagesHandler(
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

        val varName = keyValue.keyText
        if (varName.isBlank()) return true

        val patterns = listOf(
            "\$$varName",
            "\${$varName}",
            "\${$varName:-",
            "\${$varName:+"
        )

        val allFiles = GitlabCiUtil.collectAllYamlFiles(element)

        allFiles.forEach { yamlFile ->
            ApplicationManager.getApplication().runReadAction {
                PsiTreeUtil.findChildrenOfType(yamlFile, YAMLScalar::class.java).forEach { scalar ->
                    if (patterns.any { scalar.textValue.contains(it) }) {
                        processor.process(UsageInfo(scalar))
                    }
                }
            }
        }

        return true
    }
}
