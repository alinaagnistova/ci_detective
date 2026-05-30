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

package com.detective.settings

import com.detective.settings.GitlabCiSettings.Companion.DEFAULT_CACHE_TTL_HOURS
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class GitlabCiSettingsComponent {

    private val gitlabUrlField = JBTextField()
    private val gitlabTokenField = JBPasswordField()
    private val githubTokenField = JBPasswordField()
    private val cacheTtlField = JBTextField()


    val panel: JPanel = FormBuilder.createFormBuilder()
        .addSeparator()
        .addComponent(JBLabel("<html><b>GitLab</b></html>"))
        .addLabeledComponent("GitLab URL:", gitlabUrlField)
        .addLabeledComponent("Personal Access Token:", gitlabTokenField)
        .addSeparator()
        .addComponent(JBLabel("<html><b>GitHub</b></html>"))
        .addLabeledComponent("Personal Access Token:", githubTokenField)
        .addSeparator()
        .addComponent(JBLabel("<html><b>Cache</b></html>"))
        .addLabeledComponent("Cache TTL (hours):", cacheTtlField)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    fun getPreferredFocusedComponent(): JComponent = gitlabUrlField

    var gitlabUrl: String
        get() = gitlabUrlField.text
        set(value) { gitlabUrlField.text = value }

    var gitlabToken: String
        get() = String(gitlabTokenField.password)
        set(value) { gitlabTokenField.text = value }

    var githubToken: String
        get() = String(githubTokenField.password)
        set(value) { githubTokenField.text = value }

    var cacheTtlHours: Int
        get() = cacheTtlField.text.toIntOrNull() ?: DEFAULT_CACHE_TTL_HOURS
        set(value) { cacheTtlField.text = value.toString() }

}