package org.orangejam

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * Very small UI bridge:
 * - Tool window registers a refresh callback.
 * - File watcher (or any code) calls refresh(), which invokes that callback.
 */
@Service(Service.Level.PROJECT)
class GraphUiService(@Suppress("UNUSED_PARAMETER") private val project: Project) {

    @Volatile
    private var refreshCallback: (() -> Unit)? = null

    fun registerRefreshCallback(callback: () -> Unit) {
        this.refreshCallback = callback
    }
}
