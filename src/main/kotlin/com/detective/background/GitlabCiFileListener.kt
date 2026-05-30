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

package com.detective.background

import com.detective.onboarding.GitlabCiOnboardingService
import com.detective.util.GitlabCiUtil
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.psi.PsiManager

class GitlabCiFileListener : FileEditorManagerListener {

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val project = event.manager.project
        val file = event.newFile ?: return
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return

        if (!GitlabCiUtil.isGitlabCiFile(psiFile)) return

        GitlabCiOnboardingService.getInstance(project).showIfNeeded()
        IncludeIndexingTask.schedule(project, psiFile)
    }
}
