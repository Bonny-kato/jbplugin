package com.ipfsoftwares.jbplugin.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.ipfsoftwares.jbplugin.TanStackIcons
import com.ipfsoftwares.jbplugin.route.PhysicalRoute
import com.ipfsoftwares.jbplugin.route.RouteIndexService
import com.ipfsoftwares.jbplugin.route.RouteUsage
import com.ipfsoftwares.jbplugin.route.RouteUsageFinder
import com.intellij.ui.SearchTextField
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder
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
        content.preferredFocusableComponent = panel.preferredFocusComponent
        toolWindow.contentManager.addContent(content)
    }

    private class RoutesPanel(private val project: Project) : JPanel(BorderLayout()) {
        private var allRoutes: List<PhysicalRoute> = emptyList()
        private val routeModel = CollectionListModel<PhysicalRoute>()
        private val routeList = JBList(routeModel)
        private val usageModel = CollectionListModel<RouteUsage>()
        private val usageList = JBList(usageModel)
        private val filterField = SearchTextField()
        private val routesCountLabel = secondaryLabel(" ")
        private val usagesCountLabel = secondaryLabel("Select a route")
        private var usageRequestId = 0

        val preferredFocusComponent: JComponent
            get() = filterField.textEditor

        init {
            border = JBUI.Borders.empty()
            background = UIUtil.getPanelBackground()

            configureRouteList()
            configureUsageList()
            configureFilterField()

            val toolbar = createToolbar()
            val header = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
                border = JBUI.Borders.empty(8, 10, 6, 8)
                isOpaque = false
                add(filterField, BorderLayout.CENTER)
                add(toolbar, BorderLayout.EAST)
            }

            val routesHeader = sectionHeader("Routes", routesCountLabel)
            val routesPane = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(0, 0, 2, 0)
                add(routesHeader, BorderLayout.NORTH)
                add(JBScrollPane(routeList).apply {
                    border = JBUI.Borders.empty()
                }, BorderLayout.CENTER)
            }

            val usagesHeader = sectionHeader("Usages", usagesCountLabel)
            val usagesPane = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(2, 0, 0, 0)
                add(usagesHeader, BorderLayout.NORTH)
                add(JBScrollPane(usageList).apply {
                    border = JBUI.Borders.empty()
                }, BorderLayout.CENTER)
            }

            val splitter = JBSplitter(true, 0.58f).apply {
                firstComponent = routesPane
                secondComponent = usagesPane
                dividerWidth = JBUI.scale(1)
                setHonorComponentsMinimumSize(true)
            }

            add(header, BorderLayout.NORTH)
            add(splitter, BorderLayout.CENTER)
            reload()
        }

        private fun configureRouteList() {
            routeList.selectionMode = ListSelectionModel.SINGLE_SELECTION
            routeList.cellRenderer = RouteCellRenderer()
            routeList.emptyText.text = "No matching routes"
            routeList.fixedCellHeight = JBUI.scale(36)
            routeList.border = JBUI.Borders.empty(2, 4)
            routeList.visibleRowCount = 12
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
                    when (e.keyCode) {
                        KeyEvent.VK_ENTER -> {
                            openSelectedRoute()
                            e.consume()
                        }
                        KeyEvent.VK_UP -> {
                            if (routeList.selectedIndex <= 0) {
                                focusFilter(selectAll = false)
                                e.consume()
                            }
                        }
                        KeyEvent.VK_DOWN -> {
                            val last = routeModel.size - 1
                            if (last >= 0 &&
                                routeList.selectedIndex == last &&
                                usageModel.size > 0
                            ) {
                                focusUsageList(selectFirst = true)
                                e.consume()
                            }
                        }
                        KeyEvent.VK_RIGHT -> {
                            if (usageModel.size > 0) {
                                focusUsageList(selectFirst = usageList.selectedIndex < 0)
                                e.consume()
                            }
                        }
                        KeyEvent.VK_ESCAPE -> {
                            focusFilter(selectAll = true)
                            e.consume()
                        }
                        KeyEvent.VK_BACK_SPACE -> {
                            // Edit the filter from the list (remove last character)
                            val editor = filterField.textEditor
                            val doc = editor.document
                            if (doc.length > 0) {
                                val pos = editor.caretPosition.coerceIn(0, doc.length)
                                val removeAt = if (pos > 0) pos - 1 else 0
                                if (doc.length > 0 && removeAt < doc.length) {
                                    doc.remove(removeAt, 1)
                                }
                            }
                            focusFilter(selectAll = false)
                            e.consume()
                        }
                    }
                }
                override fun keyTyped(e: KeyEvent) {
                    // Type-to-filter while the list is focused
                    val ch = e.keyChar
                    if (!e.isControlDown && !e.isMetaDown && !e.isAltDown &&
                        !Character.isISOControl(ch) && ch != KeyEvent.CHAR_UNDEFINED
                    ) {
                        focusFilter(selectAll = false)
                        filterField.textEditor.replaceSelection(ch.toString())
                        e.consume()
                    }
                }
            })
        }

        private fun configureUsageList() {
            usageList.selectionMode = ListSelectionModel.SINGLE_SELECTION
            usageList.cellRenderer = UsageCellRenderer()
            usageList.emptyText.text = "Select a route to see usages"
            usageList.fixedCellHeight = JBUI.scale(28)
            usageList.border = JBUI.Borders.empty(2, 4)
            usageList.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount >= 2) openSelectedUsage()
                }
            })
            usageList.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    when (e.keyCode) {
                        KeyEvent.VK_ENTER -> {
                            openSelectedUsage()
                            e.consume()
                        }
                        KeyEvent.VK_LEFT, KeyEvent.VK_ESCAPE -> {
                            focusRouteList(keepSelection = true)
                            e.consume()
                        }
                        KeyEvent.VK_UP -> {
                            if (usageList.selectedIndex <= 0) {
                                focusRouteList(keepSelection = true)
                                e.consume()
                            }
                        }
                    }
                }
            })
        }

        private fun configureFilterField() {
            filterField.textEditor.emptyText.text = "Filter routes by path or file…"
            filterField.border = JBUI.Borders.empty()
            // Prefer a comfortable height in the header
            filterField.minimumSize = Dimension(JBUI.scale(120), JBUI.scale(28))

            filterField.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = applyFilter()
                override fun removeUpdate(e: DocumentEvent?) = applyFilter()
                override fun changedUpdate(e: DocumentEvent?) = applyFilter()
            })

            // Keyboard: ↓ moves into the filtered routes list; Enter opens the selected/first route
            filterField.textEditor.addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    when (e.keyCode) {
                        KeyEvent.VK_DOWN -> {
                            if (routeModel.size > 0) {
                                focusRouteList(selectFirstIfNeeded = true)
                                e.consume()
                            }
                        }
                        KeyEvent.VK_ENTER -> {
                            if (routeModel.size > 0) {
                                if (routeList.selectedIndex < 0) {
                                    routeList.selectedIndex = 0
                                }
                                openSelectedRoute()
                                e.consume()
                            }
                        }
                        KeyEvent.VK_ESCAPE -> {
                            if (filterField.text.isNotEmpty()) {
                                filterField.text = ""
                                e.consume()
                            }
                        }
                    }
                }
            })
        }

        private fun createToolbar(): JComponent {
            val refreshAction: AnAction = object : DumbAwareAction(
                "Refresh",
                "Rebuild the TanStack route index from routeTree.gen.ts",
                AllIcons.Actions.Refresh,
            ) {
                override fun actionPerformed(e: AnActionEvent) = reload()
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            }
            val group = DefaultActionGroup().apply { add(refreshAction) }
            val toolbar = ActionManager.getInstance()
                .createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, group, true)
            toolbar.targetComponent = this
            toolbar.component.isOpaque = false
            return toolbar.component
        }

        private fun focusFilter(selectAll: Boolean) {
            val editor = filterField.textEditor
            editor.requestFocusInWindow()
            if (selectAll) {
                editor.selectAll()
            } else {
                val len = editor.document.length
                editor.caretPosition = len
            }
        }

        private fun focusRouteList(
            selectFirstIfNeeded: Boolean = false,
            keepSelection: Boolean = false,
        ) {
            if (routeModel.size == 0) return
            if (selectFirstIfNeeded && routeList.selectedIndex < 0) {
                routeList.selectedIndex = 0
            } else if (!keepSelection && routeList.selectedIndex < 0) {
                routeList.selectedIndex = 0
            }
            val idx = routeList.selectedIndex.coerceAtLeast(0)
            routeList.selectedIndex = idx
            routeList.ensureIndexIsVisible(idx)
            routeList.requestFocusInWindow()
        }

        private fun focusUsageList(selectFirst: Boolean) {
            if (usageModel.size == 0) return
            if (selectFirst || usageList.selectedIndex < 0) {
                usageList.selectedIndex = 0
            }
            usageList.ensureIndexIsVisible(usageList.selectedIndex)
            usageList.requestFocusInWindow()
        }

        private fun reload() {
            val service = RouteIndexService.getInstance(project)
            service.invalidate()
            val index = service.getIndex()
            allRoutes = index.physicalRoutes()
            applyFilter()
            if (allRoutes.isEmpty()) {
                routesCountLabel.text = "0"
                usageModel.removeAll()
                usagesCountLabel.text = "No routes indexed"
                usageList.emptyText.text = "Generate routeTree.gen.ts, then Refresh"
            } else {
                usageList.emptyText.text = "Select a route to see usages"
            }
        }

        private fun applyFilter() {
            val q = filterField.text.orEmpty()
            val filtered = if (q.isBlank()) allRoutes else allRoutes.filter { it.matchesFilter(q) }
            val previous = routeList.selectedValue?.file?.url
            routeModel.removeAll()
            routeModel.add(filtered)

            routesCountLabel.text = when {
                allRoutes.isEmpty() -> "0"
                q.isNotBlank() -> "${filtered.size} of ${allRoutes.size}"
                else -> "${allRoutes.size}"
            }

            routeList.emptyText.text = when {
                allRoutes.isEmpty() -> "No routes indexed — generate routeTree.gen.ts and Refresh"
                filtered.isEmpty() -> "No routes match “${q.trim()}”"
                else -> "No matching routes"
            }

            if (previous != null) {
                val idx = filtered.indexOfFirst { it.file.url == previous }
                if (idx >= 0) {
                    routeList.selectedIndex = idx
                } else {
                    routeList.clearSelection()
                    usageModel.removeAll()
                    usagesCountLabel.text = "Select a route"
                }
            } else if (filtered.isEmpty()) {
                routeList.clearSelection()
            }
        }

        private fun loadUsagesForSelection() {
            val route = routeList.selectedValue
            if (route == null) {
                usageModel.removeAll()
                usagesCountLabel.text = "Select a route"
                return
            }

            val requestId = ++usageRequestId
            usageModel.removeAll()
            usagesCountLabel.text = "Searching…"
            usageList.emptyText.text = "Searching usages…"

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
                    usagesCountLabel.text = when {
                        found.isEmpty() -> "None"
                        else -> "${found.size}"
                    }
                    usageList.emptyText.text = "No usages found for ${route.path}"
                }

                override fun onCancel() {
                    if (requestId != usageRequestId) return
                    usagesCountLabel.text = "Cancelled"
                    usageList.emptyText.text = "Usage search cancelled"
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

        companion object {
            private fun secondaryLabel(text: String): JBLabel =
                JBLabel(text).apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = JBColor.namedColor(
                        "Label.infoForeground",
                        JBColor(0x808080, 0x8C8C8C),
                    )
                    border = JBUI.Borders.emptyLeft(8)
                }

            private fun sectionHeader(title: String, trailing: JBLabel): JPanel =
                JPanel(BorderLayout()).apply {
                    isOpaque = false
                    border = JBUI.Borders.empty(6, 12, 4, 12)
                    val titleLabel = JBLabel(title).apply {
                        font = JBUI.Fonts.label().asBold()
                        foreground = UIUtil.getLabelForeground()
                    }
                    val row = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                        isOpaque = false
                        add(titleLabel)
                        add(trailing)
                    }
                    add(row, BorderLayout.WEST)
                    // subtle separator under section title
                    border = JBUI.Borders.compound(
                        JBUI.Borders.customLine(
                            JBColor.namedColor("Borders.color", JBColor.border()),
                            0,
                            0,
                            1,
                            0,
                        ),
                        JBUI.Borders.empty(8, 12, 6, 12),
                    )
                }
        }
    }

    /**
     * Two-line route row: path (primary) + file (secondary).
     */
    private class RouteCellRenderer : ListCellRenderer<PhysicalRoute> {
        private val panel = JPanel(BorderLayout(JBUI.scale(8), 0))
        private val iconLabel = JBLabel(AllIcons.Nodes.PpWeb)
        private val text = SimpleColoredComponent()

        init {
            panel.border = JBUI.Borders.empty(4, 8)
            iconLabel.verticalAlignment = SwingConstants.CENTER
            panel.add(iconLabel, BorderLayout.WEST)
            panel.add(text, BorderLayout.CENTER)
            text.ipad = JBUI.insets(0)
            text.border = EmptyBorder(0, 0, 0, 0)
        }

        override fun getListCellRendererComponent(
            list: JList<out PhysicalRoute>?,
            value: PhysicalRoute?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            text.clear()
            if (value != null) {
                text.append(value.path, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                text.append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                text.append(shortFileLabel(value), SimpleTextAttributes.GRAYED_ATTRIBUTES)
                panel.toolTipText = buildString {
                    append(value.file.path)
                    if (value.alternatePaths.isNotEmpty()) {
                        append("\nAlso: ")
                        append(value.alternatePaths.take(8).joinToString(", "))
                        if (value.alternatePaths.size > 8) append(", …")
                    }
                }
            }

            val bg = if (isSelected) {
                UIUtil.getListSelectionBackground(cellHasFocus)
            } else {
                UIUtil.getListBackground()
            }
            val fg = if (isSelected) {
                UIUtil.getListSelectionForeground(cellHasFocus)
            } else {
                UIUtil.getListForeground()
            }
            panel.background = bg
            panel.foreground = fg
            text.background = bg
            text.foreground = fg
            iconLabel.background = bg
            iconLabel.isOpaque = false
            panel.isOpaque = true
            return panel
        }

        private fun shortFileLabel(route: PhysicalRoute): String {
            val path = route.file.path.replace('\\', '/')
            val marker = "/routes/"
            val idx = path.lastIndexOf(marker)
            return if (idx >= 0) path.substring(idx + 1) else route.file.name
        }
    }

    private class UsageCellRenderer : ListCellRenderer<RouteUsage> {
        private val component = SimpleColoredComponent()

        init {
            component.border = JBUI.Borders.empty(3, 10)
            component.ipad = JBUI.insets(0)
            component.iconTextGap = JBUI.scale(6)
        }

        override fun getListCellRendererComponent(
            list: JList<out RouteUsage>?,
            value: RouteUsage?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            component.clear()
            component.icon = AllIcons.General.Locate
            if (value != null) {
                component.append(value.file.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                component.append(":${value.lineNumber}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                val snippet = value.lineText.trim().take(100)
                if (snippet.isNotEmpty()) {
                    component.append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    component.append(snippet, SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                component.toolTipText = "${value.file.path}:${value.lineNumber}"
            }

            val bg = if (isSelected) {
                UIUtil.getListSelectionBackground(cellHasFocus)
            } else {
                UIUtil.getListBackground()
            }
            component.background = bg
            component.foreground = if (isSelected) {
                UIUtil.getListSelectionForeground(cellHasFocus)
            } else {
                UIUtil.getListForeground()
            }
            component.isOpaque = true
            return component
        }
    }
}
