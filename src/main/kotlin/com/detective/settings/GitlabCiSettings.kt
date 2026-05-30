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

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
@State(
    name = "GitlabCiSettings",
    storages = [Storage("gitlab-ci-detective.xml")]
)
class GitlabCiSettings : PersistentStateComponent<GitlabCiSettings.State> {

    data class State(
        var gitlabUrl: String = DEFAULT_GITLAB_URL,
        var cacheTtlHours: Int = DEFAULT_CACHE_TTL_HOURS,
    )

    private var myState = State()

    var cacheTtlHours: Int
        get() = myState.cacheTtlHours
        set(value) {
            myState.cacheTtlHours = value
        }

    var gitlabUrl: String
        get() = myState.gitlabUrl
        set(value) {
            myState.gitlabUrl = value
        }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var gitlabToken: String
        get() = PasswordSafe.instance
            .getPassword(credentialAttributes(GITLAB_TOKEN)) ?: ""
        set(value) {
            PasswordSafe.instance.set(
                credentialAttributes(GITLAB_TOKEN),
                Credentials(GITLAB_TOKEN, value)
            )
        }

    var githubToken: String
        get() = PasswordSafe.instance
            .getPassword(credentialAttributes(GITHUB_TOKEN)) ?: ""
        set(value) {
            PasswordSafe.instance.set(
                credentialAttributes(GITHUB_TOKEN),
                Credentials(GITHUB_TOKEN, value)
            )
        }

    private fun credentialAttributes(key: String) =
        CredentialAttributes("CI Detective - $key")

    companion object {
        private const val DEFAULT_GITLAB_URL = "https://gitlab.com"
        private const val GITLAB_TOKEN = "gitlab_token"
        private const val GITHUB_TOKEN = "github_token"
        const val DEFAULT_CACHE_TTL_HOURS = 1

        fun getInstance(): GitlabCiSettings = service()
    }
}