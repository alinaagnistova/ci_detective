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

package com.detective.onboarding

import com.detective.messages.CiDetectiveBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.prefs.Preferences

@Service(Service.Level.PROJECT)
class GitlabCiOnboardingService(private val project: Project) {

    fun showIfNeeded() {
        val prefs = Preferences
            .userRoot()
            .node(PREFS_NODE)

        val alreadyShown = prefs.getBoolean(PREFS_KEY, false)
        if (alreadyShown) return

        prefs.putBoolean(PREFS_KEY, true)

        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                CiDetectiveBundle.progressMessage("onboarding.plugin.activated.title"),
                buildOnboardingText(),
                NotificationType.INFORMATION
            )
            .notify(project)
    }

    companion object {
        private const val PREFS_KEY = "ci.detective.onboarding.shown"
        private const val PREFS_NODE = "ci-detective"
        private const val NOTIFICATION_GROUP = "CI Detective"
        private const val PLUGIN_NAME = "CI Detective for Gitlab"

        fun getInstance(project: Project): GitlabCiOnboardingService =
            project.service()

        fun buildOnboardingText(): String = buildString {
            append(CiDetectiveBundle.message("onboarding.plugin.activated.text.intro"))
            append("<br><br>")
            append("<b>${CiDetectiveBundle.message("onboarding.plugin.activated.text.features")}:</b><br>")
            append("• <b>Ctrl+Left‑Click</b> ")
            append(CiDetectiveBundle.message("onboarding.plugin.activated.text.include"))
            append("<br>")
            append("• <b>Ctrl+Left‑Click</b> ")
            append(CiDetectiveBundle.message("onboarding.plugin.activated.text.extends"))
            append("<br>")
            append("• <b>Alt+F7</b> — ")
            append(CiDetectiveBundle.message("onboarding.plugin.activated.text.find.usages"))
            append("<br>")
            append("• <b>Ctrl+Q</b> — ")
            append(CiDetectiveBundle.message("onboarding.plugin.activated.text.preview"))
            append("<br><br>")
            append(CiDetectiveBundle.message("onboarding.plugin.activated.text.configure.tokens"))
            append(" <b>${CiDetectiveBundle.message("menu.settings")} \u2192 ")
            append("${CiDetectiveBundle.message("menu.tools")} \u2192 ")
            append(PLUGIN_NAME)
            append("</b>")
        }
    }
}