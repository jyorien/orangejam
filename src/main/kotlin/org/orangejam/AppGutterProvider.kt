package org.orangejam

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.util.Function
import org.jetbrains.kotlin.psi.KtClass
import org.orangejam.services.GraphCacheService
import org.orangejam.services.GraphIndexService


// shows a gutter on any eligible class
class AppGutterProvider : LineMarkerProvider {
    private val log = Logger.getInstance(AppGutterProvider::class.java)

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val ktClass = element as? KtClass ?: return null
        val nameId = ktClass.nameIdentifier ?: return null
        val fqn = ktClass.fqName?.asString() ?: return null

        val module = ModuleUtilCore.findModuleForPsiElement(ktClass) ?: return null
        val project = ktClass.project

        // check if cached
        val indexSvc = project.getService(GraphIndexService::class.java)
        val cacheSvc = project.getService(GraphCacheService::class.java)

        val eligible = indexSvc.isEligible(module, fqn) {
            cacheSvc.getOrBuild(module)
        }
        if (!eligible) return null

        return LineMarkerInfo(
            nameId,
            nameId.textRange,
            AllIcons.Actions.Minimap,
            Function<PsiElement, String> { "Show Dependency Graph for $fqn" },
            { _, _ ->
                log.warn("Orangejam gutter clicked on class: $fqn")
                SubgraphRunner.generateForClass(project, module, fqn)
            },
            GutterIconRenderer.Alignment.RIGHT,
            { "Orangejam – Show Dependency Graph" }
        )
    }
}
