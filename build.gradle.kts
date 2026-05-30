import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
}

group = "com.detective"
version = "1.0.0"

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    val coroutinesVersion = project.property("coroutinesVersion") as String
    val snakeyamlVersion = project.property("snakeyamlVersion") as String
    val okhttpVersion = project.property("okhttpVersion") as String
    val gsonVersion = project.property("gsonVersion") as String
    val junitVersion = project.property("junitVersion") as String

    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("org.jetbrains.plugins.yaml")
    }

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.yaml:snakeyaml:$snakeyamlVersion")
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.google.code.gson:gson:$gsonVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.junit.vintage:junit-vintage-engine:${junitVersion}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        name = "CI Detective for Gitlab CI"
        version = "1.0.0"

        ideaVersion {
            sinceBuild = "223"
            untilBuild = "261.*"
        }
    }
}

tasks {
    instrumentCode {
        enabled = false
    }
    instrumentTestCode {
        enabled = false
    }
    patchPluginXml {
        sinceBuild.set("223")
        untilBuild.set("261.*")
    }
    test {
        useJUnitPlatform()
    }
}