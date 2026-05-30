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

import com.detective.cache.IncludeCache
import com.detective.remote.RemoteIncludeResolver
import com.detective.util.GITHUB_DOMAIN
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import org.jetbrains.yaml.psi.YAMLScalar
import java.io.File

class RemoteIncludeReference(
    element: YAMLScalar,
    private val url: String
) : PsiReferenceBase<YAMLScalar>(element, TextRange(0, element.textLength)) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val cache = IncludeCache.getInstance(project)

        val content = if (url.contains(GITHUB_DOMAIN)) {
            RemoteIncludeResolver.resolveGitHubFile(project, url)
        } else {
            RemoteIncludeResolver.resolveRemoteUrl(project, url)
        } ?: return null

        val cacheFilePath = cache.getCacheFilePath(url) ?: return null
        val ioFile = File(cacheFilePath)

        if (!ioFile.exists()) {
            ioFile.writeText(content)
        }

        val virtualFile = LocalFileSystem.getInstance().findFileByPath(cacheFilePath)
            ?: LocalFileSystem.getInstance().refreshAndFindFileByPath(cacheFilePath)
            ?: return null

        return PsiManager.getInstance(project).findFile(virtualFile)
    }

    override fun getVariants(): Array<Any> = emptyArray()
}