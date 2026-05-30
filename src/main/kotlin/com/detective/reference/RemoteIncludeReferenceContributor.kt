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

import com.detective.util.GitlabCiUtil
import com.detective.util.INCLUDE_REMOTE_KEY
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

class RemoteIncludeReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(YAMLScalar::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val scalar = element as? YAMLScalar ?: return emptyArray()

                    val file = element.containingFile ?: return emptyArray()
                    if (!GitlabCiUtil.isGitlabCiFile(file)) return emptyArray()

                    val keyValue = scalar.parent as? YAMLKeyValue ?: return emptyArray()
                    if (keyValue.keyText != INCLUDE_REMOTE_KEY) return emptyArray()
                    if (!GitlabCiUtil.isInsideInclude(keyValue)) return emptyArray()

                    val url = scalar.textValue
                    if (url.isBlank()) return emptyArray()

                    return arrayOf(RemoteIncludeReference(scalar, url))
                }
            },
            PsiReferenceRegistrar.HIGHER_PRIORITY
        )
    }
}