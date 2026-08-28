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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import javax.swing.JComponent
import com.intellij.util.concurrency.AppExecutorUtil

class GitlabCiSettingsConfigurable : Configurable {

    private var component: GitlabCiSettingsComponent? = null
    private var loadedGitlabToken: String = ""
    private var loadedGithubToken: String = ""

    override fun getDisplayName() = "CI Detective for Gitlab"

    override fun createComponent(): JComponent {
        component = GitlabCiSettingsComponent()
        return component!!.panel
    }

    override fun isModified(): Boolean {
        val settings = GitlabCiSettings.getInstance()
        val c = component ?: return false
        return c.gitlabUrl != settings.gitlabUrl ||
                c.cacheTtlHours != settings.cacheTtlHours ||
                c.gitlabToken != loadedGitlabToken ||
                c.githubToken != loadedGithubToken
    }

    override fun apply() {
        val settings = GitlabCiSettings.getInstance()
        val c = component ?: return
        settings.gitlabUrl = c.gitlabUrl
        settings.cacheTtlHours = c.cacheTtlHours

        AppExecutorUtil.getAppExecutorService().submit {
                settings.gitlabToken = c.gitlabToken
                settings.githubToken = c.githubToken
            }
    }

    override fun reset() {
        val settings = GitlabCiSettings.getInstance()
        val c = component ?: return
        c.gitlabUrl = settings.gitlabUrl
        c.cacheTtlHours = settings.cacheTtlHours

        ApplicationManager.getApplication().executeOnPooledThread {
            val gitlabToken = settings.gitlabToken
            val githubToken = settings.githubToken
            loadedGitlabToken = gitlabToken
            loadedGithubToken = githubToken
            ApplicationManager.getApplication().invokeLater {
                c.gitlabToken = gitlabToken
                c.githubToken = githubToken
            }
        }
    }

    override fun disposeUIResources() {
        component = null
    }
}