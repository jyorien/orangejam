package org.orangejam.navigation

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.orangejam.graph.Node

class DiProviderLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        if (elements.isEmpty()) return
        val project = elements.first().project
        if (DumbService.isDumb(project)) return

        val graph = project.getService(GraphProvider::class.java).graph()

        for (el in elements) {
            // Work on the function *name* leaf to avoid duplicates
            val fn = (el.parent as? KtNamedFunction) ?: continue
            if (el != fn.nameIdentifier) continue

            // @Provides? (accept FQN or short)
            val isProvides = fn.annotationEntries.any { ann ->
                val t = ann.typeReference?.text ?: return@any false
                t == "knit.Provides" || t == "Provides" || t.endsWith(".Provides")
            }
            if (!isProvides) continue

            val clsFqn = fn.containingClassOrObject?.fqName?.asString() ?: continue
            val keyContainer = clsFqn.replace('.', '/')
            val keyName = fn.name ?: continue
            val keyArity = fn.valueParameters.size

            val injections = graph.edges.mapNotNull { e ->
                val pm = e.source.data as? Node.ProviderMethod ?: return@mapNotNull null
                if (pm.containerClass == keyContainer &&
                    pm.functionName == keyName //&&
//                    pm.parameterCount == keyArity
                ) e.destination.data as? Node.InjectionField else null
            }
            if (injections.isEmpty()) continue

            val scope = GlobalSearchScope.allScope(project)
            val targets = injections.mapNotNull { inj ->
                val ownerFqn = inj.containerClass.replace('/', '.')
                val propName = inj.field.removePrefix("get").replaceFirstChar { it.lowercase() }

                val psiClass = JavaPsiFacade.getInstance(project).findClass(ownerFqn, scope) ?: return@mapNotNull null
                val ktOwner = psiClass.navigationElement as? KtClassOrObject ?: return@mapNotNull null

                val prop = (
                        ktOwner.body?.properties?.firstOrNull { it.name == propName }
                            ?: ktOwner.companionObjects
                                .asSequence()
                                .flatMap { it.body?.properties.orEmpty().asSequence() }
                                .firstOrNull { it.name == propName }
                        ) ?: return@mapNotNull null

                prop.nameIdentifier ?: prop
            }
            if (targets.isEmpty()) continue

            val builder = NavigationGutterIconBuilder
                .create(AllIcons.Gutter.ImplementedMethod)
                .setTargets(targets)
                .setTooltipText("Injected in ${targets.size} place${if (targets.size == 1) "" else "s"}")
                .setPopupTitle("Injected properties")

            result.add(builder.createLineMarkerInfo(el))
        }
    }
}