package com.ipfsoftwares.jbplugin.usages

import com.intellij.icons.AllIcons
import com.intellij.find.FindManager
import com.intellij.find.findUsages.FindUsagesManager
import com.intellij.find.impl.FindManagerBase
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.usages.ConfigurableUsageTarget
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageTargetProvider
import com.ipfsoftwares.jbplugin.JbPluginBundle
import javax.swing.Icon

/**
 * Ensures Alt+F7 from a TanStack route definition opens **route link usages**,
 * instead of TypeScript's default symbol usages (often only `routeTree.gen.ts`).
 */
class TanStackRouteUsageTargetProvider : UsageTargetProvider {

    override fun getTargets(editor: Editor, file: PsiFile): Array<UsageTarget>? {
        val offset = editor.caretModel.offset
        val el = file.findElementAt(offset)
            ?: file.findElementAt((offset - 1).coerceAtLeast(0))
            ?: return null
        return targetsFor(el)
    }

    override fun getTargets(psiElement: PsiElement): Array<UsageTarget>? =
        targetsFor(psiElement)

    private fun targetsFor(element: PsiElement): Array<UsageTarget>? {
        val paths = RouteFindUsagesSupport.pathsFor(element) ?: return null
        val anchor = RouteFindUsagesSupport.preferredAnchor(element) ?: element
        return arrayOf(TanStackRouteUsageTarget(anchor, paths))
    }
}

/**
 * Usage target labeled as a TanStack route; [findUsages] always uses our handler.
 */
class TanStackRouteUsageTarget(
    element: PsiElement,
    private val routePaths: List<String>,
) : PsiElementUsageTarget, ConfigurableUsageTarget, ItemPresentation {

    private val pointer: SmartPsiElementPointer<PsiElement> =
        SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)

    private val project: Project = element.project
    private val preferredPath: String =
        routePaths.firstOrNull { it.startsWith("/") && !it.contains("/_") }
            ?: routePaths.firstOrNull()
            ?: "route"

    override fun getElement(): PsiElement? = pointer.element

    override fun isValid(): Boolean = pointer.element?.isValid == true

    override fun findUsages() {
        val el = pointer.element ?: return
        val handler = TanStackRouteFindUsagesHandler(el, routePaths)
        findUsagesManager(project).findUsages(
            handler.primaryElements,
            handler.secondaryElements,
            handler,
            handler.findUsagesOptions,
            /* toEditor = */ false,
        )
    }

    override fun findUsagesInEditor(editor: FileEditor) {
        findUsages()
    }

    override fun highlightUsages(file: PsiFile, editor: com.intellij.openapi.editor.Editor, clearHighlights: Boolean) {
        // Fall back to full find-usages tool window; highlight-in-file is optional.
        findUsages()
    }

    override fun getName(): String = preferredPath

    override fun getPresentation(): ItemPresentation = this

    override fun getPresentableText(): String =
        JbPluginBundle.message("usages.target.presentable", preferredPath)

    override fun getLocationString(): String =
        pointer.element?.containingFile?.name.orEmpty()

    override fun getIcon(unused: Boolean): Icon = AllIcons.Nodes.PpWeb

    override fun navigate(requestFocus: Boolean) {
        val el = pointer.element ?: return
        if (el is Navigatable && el.canNavigate()) {
            el.navigate(requestFocus)
        }
    }

    override fun canNavigate(): Boolean =
        (pointer.element as? Navigatable)?.canNavigate() == true

    override fun canNavigateToSource(): Boolean = canNavigate()

    override fun isReadOnly(): Boolean = false

    override fun getFiles(): Array<com.intellij.openapi.vfs.VirtualFile> {
        val vf = pointer.element?.containingFile?.virtualFile ?: return emptyArray()
        return arrayOf(vf)
    }

    override fun update() {}

    override fun showSettings() {
        findUsages()
    }

    override fun getShortcut(): KeyboardShortcut? =
        KeymapUtil.getPrimaryShortcut("FindUsages") as? KeyboardShortcut

    override fun getLongDescriptiveName(): String =
        JbPluginBundle.message("usages.target.long", preferredPath)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TanStackRouteUsageTarget) return false
        return routePaths == other.routePaths &&
            pointer.element == other.pointer.element
    }

    override fun hashCode(): Int = routePaths.hashCode() * 31 + (pointer.element?.hashCode() ?: 0)

    companion object {
        fun findUsagesManager(project: Project): FindUsagesManager {
            val fm = FindManager.getInstance(project)
            if (fm is FindManagerBase) {
                return fm.findUsagesManager
            }
            return FindUsagesManager(project)
        }
    }
}
