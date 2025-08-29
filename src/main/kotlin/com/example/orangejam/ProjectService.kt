package com.example.orangejam

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.plugins.gradle.util.GradleConstants

@Service(Service.Level.PROJECT)
class ProjectService(private val project: Project) {
    private val log = Logger.getInstance(ProjectService::class.java)

    init {
        thisLogger().warn("Project Service init.")
    }

    fun projectUsesKnit(): Boolean {
        val detected = DetectKnit.detect(project)
        log.debug("Detected knit for project '${project.name}': $detected")
        return detected
    }

    // get all gradle modules
    fun gradleModules(): List<Module> {
        val modules: Array<Module> = ModuleManager.getInstance(project).modules
        return modules
            .filter { m -> ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, m) }
            .sortedBy { it.name }
    }

    fun allModules(): List<Module> =
        ModuleManager.getInstance(project).modules.sortedBy { it.name }.toList()

    fun knitModules(): List<Module> =
        allModules().filter { module -> DetectKnit.scanModule(module) }
}


