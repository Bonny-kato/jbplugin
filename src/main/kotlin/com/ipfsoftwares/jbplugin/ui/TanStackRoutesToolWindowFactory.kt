package com.ipfsoftwares.jbplugin.ui

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.ipfsoftwares.jbplugin.TanStackIcons
import com.ipfsoftwares.jbplugin.route.PhysicalRoute
import com.ipfsoftwares.jbplugin.route.RouteIndexService
import com.ipfsoftwares.jbplugin.route.RouteUsage
import com.ipfsoftwares.jbplugin.route.RouteUsageFinder
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Tool window: unique physical TanStack routes + usages for the selected route.
 */
class TanStackRoutesToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.setIcon(TanStackIcons.ToolWindow)
        val panel = RoutesPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private class RoutesPanel(private val project: Project) : JPanel(BorderLayout()) {
        private var allRoutes: List<PhysicalRoute> = emptyList()
        private val routeModel = CollectionListModel<PhysicalRoute>()
        private val routeList = JBList(routeModel)
        private val usageModel = CollectionListModel<RouteUsage>()
        private val usageList = JBList(usageModel)
        private val filterField = SearchTextField()
        private val status = JBLabel(" ")
        private val usageStatus = JBLabel("Select a route to see usages")
        private var usageRequestId = 0

        init {
            border = JBUI.Borders.empty(8)

            routeList.selectionMode = ListSelectionModel.SINGLE_SELECTION
            routeList.cellRenderer = RouteCellRenderer()
            routeList.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount >= 2) openSelectedRoute()
                }
            })
            routeList.addListSelectionListener {
                if (!it.valueIsAdjusting) loadUsagesForSelection()
            }
            routeList.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER) openSelectedRoute()
                }
            })

            usageList.selectionMode = ListSelectionModel.SINGLE_SELECTION
            usageList.cellRenderer = UsageCellRenderer()
            usageList.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount >= 2) openSelectedUsage()
                }
            })
            usageList.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER) openSelectedUsage()
                }
            })

            filterField.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = applyFilter()
                override fun removeUpdate(e: DocumentEvent?) = applyFilter()
                override fun changedUpdate(e: DocumentEvent?) = applyFilter()
            })

            val refresh = JButton("Refresh").apply {
                addActionListener { reload() }
            }
            val north = JPanel(BorderLayout(8, 0)).apply {
                add(filterField, BorderLayout.CENTER)
                add(refresh, BorderLayout.EAST)
            }

            val routesPane = JPanel(BorderLayout()).apply {
                add(JBLabel("Physical routes"), BorderLayout.NORTH)
                add(JBScrollPane(routeList), BorderLayout.CENTER)
                add(status, BorderLayout.SOUTH)
            }
            val usagesPane = JPanel(BorderLayout()).apply {
                add(JBLabel("Usages"), BorderLayout.NORTH)
                add(JBScrollPane(usageList), BorderLayout.CENTER)
                add(usageStatus, BorderLayout.SOUTH)
            }

            val splitter = JBSplitter(true, 0.55f).apply {
                firstComponent = routesPane
                secondComponent = usagesPane
                dividerWidth = 3
            }

            add(north, BorderLayout.NORTH)
            add(splitter, BorderLayout.CENTER)
            reload()
        }

        private fun reload() {
            val service = RouteIndexService.getInstance(project)
            service.invalidate()
            val index = service.getIndex()
            allRoutes = index.physicalRoutes()
            applyFilter()
            if (allRoutes.isEmpty()) {
                status.text = "No routes indexed. Generate routeTree.gen.ts and Refresh."
                usageModel.removeAll()
                usageStatus.text = "No routes"
            }
        }

        private fun applyFilter() {
            val q = filterField.text.orEmpty()
            val filtered = if (q.isBlank()) allRoutes else allRoutes.filter { it.matchesFilter(q) }
            val previous = routeList.selectedValue?.file?.url
            routeModel.removeAll()
            routeModel.add(filtered)
            status.text = when {
                allRoutes.isEmpty() -> "0 physical routes"
                q.isNotBlank() -> "${filtered.size} of ${allRoutes.size} physical routes"
                else -> "${allRoutes.size} physical route(s)"
            }
            if (previous != null) {
                val idx = filtered.indexOfFirst { it.file.url == previous }
                if (idx >= 0) routeList.selectedIndex = idx
            }
        }

        private fun loadUsagesForSelection() {
            val route = routeList.selectedValue
            if (route == null) {
                usageModel.removeAll()
                usageStatus.text = "Select a route to see usages"
                return
            }

            val requestId = ++usageRequestId
            usageModel.removeAll()
            usageStatus.text = "Searching usages for ${route.path}…"

            ProgressManager.getInstance().run(object : Task.Backgroundable(
                project,
                "Find TanStack route usages",
                true,
            ) {
                private var found: List<RouteUsage> = emptyList()

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = false
                    found = RouteUsageFinder.findUsages(project, route.allPaths, indicator)
                }

                override fun onSuccess() {
                    if (requestId != usageRequestId) return
                    if (project.isDisposed) return
                    usageModel.removeAll()
                    usageModel.add(found)
                    usageStatus.text = when {
                        found.isEmpty() -> "No usages found for ${route.path}"
                        else -> "${found.size} usage(s) of ${route.path}"
                    }
                }

                override fun onCancel() {
                    if (requestId != usageRequestId) return
                    usageStatus.text = "Usage search cancelled"
                }
            })
        }

        private fun openSelectedRoute() {
            val route = routeList.selectedValue ?: return
            OpenFileDescriptor(project, route.file).navigate(true)
        }

        private fun openSelectedUsage() {
            val usage = usageList.selectedValue ?: return
            OpenFileDescriptor(project, usage.file, usage.offset).navigate(true)
        }
    }

    private class RouteCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is PhysicalRoute) {
                val rel = shortFileLabel(value)
                text = "${value.path}   →  $rel"
                icon = TanStackIcons.Route
                toolTipText = buildString {
                    append(value.file.path)
                    if (value.alternatePaths.isNotEmpty()) {
                        append("\nAlso: ")
                        append(value.alternatePaths.take(8).joinToString(", "))
                        if (value.alternatePaths.size > 8) append(", …")
                    }
                }
            }
            return c
        }

        private fun shortFileLabel(route: PhysicalRoute): String {
            val path = route.file.path.replace('\\', '/')
            val marker = "/routes/"
            val idx = path.lastIndexOf(marker)
            return if (idx >= 0) path.substring(idx + 1) else route.file.name
        }
    }

    private class UsageCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is RouteUsage) {
                text = value.displayLabel
                toolTipText = "${value.file.path}:${value.lineNumber}"
            }
            return c
        }
    }
}
