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

import com.detective.messages.CiDetectiveBundle
import com.detective.util.GitlabCiUtil
import com.detective.util.INCLUDE_LOCAL_KEY
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YamlPsiElementVisitor

class MissingLocalIncludeInspection : LocalInspectionTool() {
    override fun getShortName() = "MissingLocalIncludeInspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : YamlPsiElementVisitor() {
            override fun visitKeyValue(keyValue: YAMLKeyValue) {
                val file = keyValue.containingFile ?: return
                if (!GitlabCiUtil.isGitlabCiFile(file)) return

                if (keyValue.keyText != INCLUDE_LOCAL_KEY) return
                if (!GitlabCiUtil.isInsideInclude(keyValue)) return

                val scalar = keyValue.value as? YAMLScalar ?: return
                val path = scalar.textValue.trimStart('/')
                if (path.isBlank()) return

                val baseDir = file.virtualFile?.parent ?: return
                val targetFile = baseDir.findFileByRelativePath(path)

                if (targetFile == null) {
                    holder.registerProblem(
                        scalar,
                        CiDetectiveBundle.message("inspection.file.not.found", path)
                    )
                }
            }
        }
    }

}