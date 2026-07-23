package com.ipfsoftwares.jbplugin.route

import com.intellij.util.messages.Topic

/**
 * Notifies UI and other subscribers when the TanStack route index was rebuilt
 * (e.g. after `routeTree.gen.ts` or route files change).
 */
fun interface RouteIndexListener {

    fun routesChanged()

    companion object {
        @Topic.ProjectLevel
        val TOPIC: Topic<RouteIndexListener> = Topic.create(
            "TanStack route index changed",
            RouteIndexListener::class.java,
        )
    }
}
