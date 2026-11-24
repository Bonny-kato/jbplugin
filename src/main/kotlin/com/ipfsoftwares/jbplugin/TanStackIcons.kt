package com.ipfsoftwares.jbplugin

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Plugin chrome icons. Logo is for branding (plugin list / tool window);
 * gutter uses the platform world icon via AllIcons.Nodes.PpWeb.
 */
object TanStackIcons {
    @JvmField
    val ToolWindow: Icon = IconLoader.getIcon("/icons/tanstackToolWindow.svg", TanStackIcons::class.java)
}
