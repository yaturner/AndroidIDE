/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.templates.base.root

import com.adfa.constants.DEST_LOCAL_ANDROID_GRADLE_PLUGIN_VERSION
import com.adfa.constants.GRADLE_FOLDER_NAME
import com.adfa.constants.LOCAL_ANDROID_GRADLE_PLUGIN_DEPENDENCY_NAME
import com.adfa.constants.LOCAL_ANDROID_GRADLE_PLUGIN_JAR_NAME
import com.adfa.constants.LOCAL_ANDROID_GRADLE_PLUGIN_NAME
import com.adfa.constants.LOCAL_ANDROID_GRADLE_PLUGIN_NAME
import com.adfa.constants.LOCAL_ANDROID_GRADLE_PLUGIN_VERSION
import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.base.ProjectTemplateBuilder

internal fun ProjectTemplateBuilder.buildGradleSrcKts(): String {
  return """
    // Top-level build file where you can add configuration options common to all sub-projects/modules.
    plugins {
        id("com.android.application") apply false version "$DEST_LOCAL_ANDROID_GRADLE_PLUGIN_VERSION"
        id("com.android.library") apply false version "$DEST_LOCAL_ANDROID_GRADLE_PLUGIN_VERSION"
    }
    
    buildscript {
        dependencies {
            // Use the local plugin JAR for the Android Gradle plugin
            // if this will not work, use files alternative below
            //classpath("$LOCAL_ANDROID_GRADLE_PLUGIN_DEPENDENCY_NAME")
            //classpath(files("$GRADLE_FOLDER_NAME/$LOCAL_ANDROID_GRADLE_PLUGIN_JAR_NAME"))
    
            // Specify the Android Gradle plugin version if needed
            // classpath("com.android.tools.build:gradle:your-plugin-version")
        }
    }

    tasks.register("printClasspath") {
        doLast {
            println("Buildscript Classpath:")
            buildscript.configurations.forEach { config ->
                println("Configuration:" + config.name)
                config.files.forEach { file ->
                    println(" - " + file.absolutePath)
                }
            }
        }
    }
    
    tasks.register<Delete>("clean") {
        delete(rootProject.layout.buildDirectory)
    }
    
    gradle.taskGraph.whenReady {
        allTasks.forEach {
            println("hz " + it)
        }
        //if (allTasks.firstOrNull { it.name.contains("build") } != null || allTasks.firstOrNull { it.name.contains("assemble") } != null) {
            tasks.named("printClasspath").get().actions.forEach { action ->
                action.execute(tasks.named("printClasspath").get())
            }
        //}
    }
  """.trimIndent()
}

internal fun ProjectTemplateBuilder.buildGradleSrcGroovy(): String {
  return """
    // Top-level build file where you can add configuration options common to all sub-projects/modules.
    buildscript {
        repositories {
            google()  // Add Google's Maven repository
            mavenCentral()  // Add Maven Central repository (optional)
            flatDir {
              dirs("$GRADLE_FOLDER_NAME") // Directory containing your local JAR
            }
        }
        dependencies {
            // Use the local plugin JAR for the Android Gradle plugin
            // if this will not work, use files alternative below
            //classpath("$LOCAL_ANDROID_GRADLE_PLUGIN_DEPENDENCY_NAME")
            classpath(files("$GRADLE_FOLDER_NAME/$LOCAL_ANDROID_GRADLE_PLUGIN_JAR_NAME"))
    
            // Specify the Android Gradle plugin version if needed
            // classpath("com.android.tools.build:gradle:your-plugin-version")
        }
    }

    task clean(type: Delete) {
        delete rootProject.layout.buildDirectory
    }
  """.trimIndent()
}

private fun ProjectTemplateBuilder.ktPlugin() = if (data.language == Language.Kotlin) {
  if (data.useKts) ktPluginKts() else ktPluginGroovy()
} else ""

private fun ProjectTemplateBuilder.ktPluginKts(): String {
  return """id("org.jetbrains.kotlin.android") version "${data.version.kotlin}" apply false"""
}

private fun ProjectTemplateBuilder.ktPluginGroovy(): String {
  return "id 'org.jetbrains.kotlin.android' version '${data.version.kotlin}' apply false"
}
