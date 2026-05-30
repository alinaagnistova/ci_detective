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

import com.detective.util.GitlabCiUtil
import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement

class YamlAliasInspectionSuppressor : InspectionSuppressor {

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId != YAML_UNRESOLVED_ALIAS) return false

        val file = element.containingFile ?: return false
        return GitlabCiUtil.isGitlabCiFile(file)
    }

    override fun getSuppressActions(
        element: PsiElement?,
        toolId: String
    ): Array<SuppressQuickFix> = emptyArray()


    companion object {
        private const val YAML_UNRESOLVED_ALIAS = "YAMLUnresolvedAlias"
    }
}