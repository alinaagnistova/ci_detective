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
import com.intellij.util.concurrency.AppExecutorUtil
import javax.swing.JComponent

class GitlabCiSettingsConfigurable : Configurable {

    private var component: GitlabCiSettingsComponent? = null
    @Volatile private var loadedGitlabToken: String = ""
    @Volatile private var loadedGithubToken: String = ""
    @Volatile private var tokensLoaded: Boolean = false

    override fun getDisplayName() = "CI Detective for Gitlab"

    override fun createComponent(): JComponent {
        component = GitlabCiSettingsComponent()
        val settings = GitlabCiSettings.getInstance()

        if (tokensLoaded) {
            component?.gitlabToken = loadedGitlabToken
            component?.githubToken = loadedGithubToken
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val gitlabToken = settings.gitlabToken
            val githubToken = settings.githubToken
            loadedGitlabToken = gitlabToken
            loadedGithubToken = githubToken
            tokensLoaded = true
            ApplicationManager.getApplication().invokeLater {
                component?.gitlabToken = gitlabToken
                component?.githubToken = githubToken
            }
        }
        return component!!.panel
    }

    override fun isModified(): Boolean {
        val settings = GitlabCiSettings.getInstance()
        val c = component ?: return false

        if (c.gitlabUrl != settings.gitlabUrl) return true
        if (c.cacheTtlHours != settings.cacheTtlHours) return true

        if (!tokensLoaded) return false
        if (c.gitlabToken != loadedGitlabToken) return true
        if (c.githubToken != loadedGithubToken) return true

        return false
    }

    override fun apply() {
        val settings = GitlabCiSettings.getInstance()
        val c = component ?: return
        settings.gitlabUrl = c.gitlabUrl
        settings.cacheTtlHours = c.cacheTtlHours

        val newGitlabToken = c.gitlabToken
        val newGithubToken = c.githubToken

        AppExecutorUtil.getAppExecutorService().submit {
            settings.gitlabToken = newGitlabToken
            settings.githubToken = newGithubToken
            loadedGitlabToken = newGitlabToken
            loadedGithubToken = newGithubToken
        }
    }

    override fun reset() {
        val settings = GitlabCiSettings.getInstance()
        val c = component ?: return
        c.gitlabUrl = settings.gitlabUrl
        c.cacheTtlHours = settings.cacheTtlHours
        if (tokensLoaded) {
            c.gitlabToken = loadedGitlabToken
            c.githubToken = loadedGithubToken
        }
    }

    override fun disposeUIResources() {
        component = null
    }
}