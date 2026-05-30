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

@file:Suppress("DEPRECATION")

package com.detective.annotation

import com.detective.util.EXTENDS_KEY
import com.detective.util.GitlabCiUtil
import com.detective.util.INCLUDE_DIRECTIVES
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiElement
import com.intellij.ui.JBColor
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import java.awt.Color
import org.jetbrains.yaml.psi.YAMLAlias

class GitlabCiAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element.containingFile ?: return
        if (!GitlabCiUtil.isGitlabCiFile(file)) return

        if (element is YAMLAlias) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .textAttributes(EXTENDS_JOB_KEY)
                .create()
            return
        }

        val scalar = element as? YAMLScalar ?: return

        val keyValue = scalar.parent as? YAMLKeyValue
            ?: scalar.parent?.parent?.parent as? YAMLKeyValue

        when (keyValue?.keyText) {
            in INCLUDE_DIRECTIVES -> annotateIncludePath(scalar, keyValue!!, holder)
            EXTENDS_KEY -> annotateExtendsJob(scalar, holder)
        }
    }

    private fun annotateIncludePath(
        scalar: YAMLScalar,
        keyValue: YAMLKeyValue,
        holder: AnnotationHolder
    ) {
        if (!GitlabCiUtil.isInsideInclude(keyValue)) return
        if (scalar.textValue.isBlank()) return

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(scalar)
            .textAttributes(INCLUDE_PATH_KEY)
            .create()
    }

    private fun annotateExtendsJob(
        scalar: YAMLScalar,
        holder: AnnotationHolder
    ) {
        if (scalar.textValue.isBlank()) return

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(scalar)
            .textAttributes(EXTENDS_JOB_KEY)
            .create()
    }

    companion object {
        val INCLUDE_PATH_KEY = TextAttributesKey.createTextAttributesKey(
            "GITLAB_CI_INCLUDE_PATH",
            TextAttributes().apply {
                effectType = EffectType.LINE_UNDERSCORE
                effectColor = JBColor(Color(98, 151, 85), Color(98, 151, 85))
            }
        )

        val EXTENDS_JOB_KEY = TextAttributesKey.createTextAttributesKey(
            "GITLAB_CI_EXTENDS_JOB",
            TextAttributes().apply {
                effectType = EffectType.LINE_UNDERSCORE
                effectColor = JBColor(Color(104, 151, 187), Color(104, 151, 187))
            }
        )
    }
}