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

package com.detective.action

import com.detective.navigation.GitlabCiGotoDeclarationHandler
import com.detective.util.GitlabCiUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor

class GotoDeclarationAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return
        if (!GitlabCiUtil.isGitlabCiFile(file)) return

        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return

        val targets = GitlabCiGotoDeclarationHandler().getGotoDeclarationTargets(element, offset, editor) ?: return

        if (targets.isEmpty()) return

        val target = targets.first()
        val targetFile = target.containingFile?.virtualFile ?: return
        val targetOffset = target.textOffset

        FileEditorManager.getInstance(project).openEditor(
            OpenFileDescriptor(project, targetFile, targetOffset),
            true
        )
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: run {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: run {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isEnabledAndVisible = GitlabCiUtil.isGitlabCiFile(file)
    }

}