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

package com.detective.util

const val MAX_DEPTH = 10
const val INCLUDE_LOCAL_KEY = "local"
const val INCLUDE_FILE_KEY = "file"
const val INCLUDE_REMOTE_KEY = "remote"
const val INCLUDE_TEMPLATE_KEY = "template"
 const val INCLUDE_COMPONENT_KEY = "component"
const val EXTENDS_KEY = "extends"
const val GITHUB_DOMAIN = "github.com"
const val CACHE_DIR_PATH = ".idea/gitlab-ci-cache"
const val CACHE_DIR_NAME = "gitlab-ci-cache"
const val INCLUDE_KEY = "include"
const val PROJECT_KEY = "project"
const val REF_KEY = "ref"
const val MAIN_KEY = "main"
const val HEAD_REF = "HEAD"

val INCLUDE_DIRECTIVES = setOf(
    INCLUDE_LOCAL_KEY,
    INCLUDE_FILE_KEY,
    INCLUDE_REMOTE_KEY,
    INCLUDE_TEMPLATE_KEY,
    INCLUDE_COMPONENT_KEY
)