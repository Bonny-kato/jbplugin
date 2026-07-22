package com.ipfsoftwares.jbplugin.ui

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.ipfsoftwares.jbplugin.route.RouteIndexService
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Simple tool window listing indexed TanStack routes so the plugin is visible
 * and routes can be opened with a double-click.
 */
class TanStackRoutesToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = RoutesPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private class RoutesPanel(private val project: Project) : JPanel(BorderLayout()) {
        private val model = DefaultListModel<String>()
        private val list = JBList(model)
        private val status = JBLabel(" ")

        init {
            border = JBUI.Borders.empty(8)
            list.selectionMode = ListSelectionModel.SINGLE_SELECTION
            list.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount >= 2) openSelected()
                }
            })

            val refresh = JButton("Refresh routes").apply {
                addActionListener { reload() }
            }
            val north = JPanel(BorderLayout()).apply {
                add(JBLabel("TanStack routes (from routeTree.gen.ts)"), BorderLayout.CENTER)
                add(refresh, BorderLayout.EAST)
            }
            add(north, BorderLayout.NORTH)
            add(JBScrollPane(list), BorderLayout.CENTER)
            add(status, BorderLayout.SOUTH)
            reload()
        }

        private fun reload() {
            val service = RouteIndexService.getInstance(project)
            service.invalidate()
            val index = service.getIndex()
            model.clear()
            val paths = index.allPaths()
            paths.forEach { model.addElement(it) }
            status.text = if (paths.isEmpty()) {
                "No routes indexed. Generate routeTree.gen.ts (e.g. run dev) and Refresh."
            } else {
                "${paths.size} route path(s) indexed"
            }
        }

        private fun openSelected() {
            val path = list.selectedValue ?: return
            val vf = RouteIndexService.getInstance(project).resolve(path) ?: return
            OpenFileDescriptor(project, vf).navigate(true)
        }
    }
}
