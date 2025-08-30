package org.orangejam.navigation

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.orangejam.graph.Node

class DiInjectionLineMarkerProvider : LineMarkerProvider, DumbAware {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        val project = elements.firstOrNull()?.project ?: return
        if (DumbService.isDumb(project)) return

        val graph = project.getService(GraphProvider::class.java).graph()
        val seen = HashSet<PsiElement>()

        for (el in elements) {
            run {
                val prop = el.getStrictParentOfType<KtProperty>() ?: return@run
                if (!prop.hasDelegate()) return@run
                val delegateName = (prop.delegate?.expression as? KtNameReferenceExpression)?.getReferencedName()
                if (delegateName != "di") return@run
                if (prop.initializer != null) return@run

                val ownerFqn = prop.containingClassOrObject?.fqName?.asString() ?: return@run
                val ownerSlash = ownerFqn.replace('.', '/')
                val getterName = "get" + (prop.name ?: return@run).replaceFirstChar { it.uppercase() }

                val anchor = prop.nameIdentifier ?: prop
                if (!seen.add(anchor)) return@run

                val providers = graph.edges.mapNotNull { e ->
                    val inj = e.destination.data as? Node.InjectionField ?: return@mapNotNull null
                    if (inj.containerClass == ownerSlash && inj.field == getterName)
                        e.source.data as? Node.ProviderMethod
                    else null
                }.distinctBy { "${it.containerClass}#${it.functionName}:${it.signature}" }

                if (providers.isEmpty()) return@run

                val targets = providers.mapNotNull { pm -> resolveProviderPsi(anchor, pm) }
                if (targets.isEmpty()) return@run

                val builder = NavigationGutterIconBuilder
                    .create(AllIcons.Gutter.ImplementedMethod)
                    .setTargets(targets)
                    .setTooltipText("Go to DI provider${if (targets.size > 1) "s" else ""}")
                    .setPopupTitle("Providers")

                result.add(builder.createLineMarkerInfo(anchor))
            }

            run {
                val prop = el.getStrictParentOfType<KtProperty>() ?: return@run

                val isComponent = prop.annotationEntries.any { ann ->
                    val t = ann.typeReference?.text ?: return@any false
                    t == "Component" || t.endsWith(".Component")
                }
                if (!isComponent) return@run

                val ownerFqn = prop.containingClassOrObject?.fqName?.asString() ?: return@run
                val ownerSlash = ownerFqn.replace('.', '/')

                val compFqn = resolveComponentTypeFqn(prop) ?: return@run
                val componentSlash = compFqn.replace('.', '/')

                val anchor = prop.nameIdentifier ?: prop.typeReference ?: prop
                if (!seen.add(anchor)) return@run

                val providers = graph.edges.mapNotNull { e ->
                    val pm = e.source.data as? Node.ProviderMethod ?: return@mapNotNull null
                    val inj = e.destination.data as? Node.InjectionField ?: return@mapNotNull null
                    if (pm.containerClass == componentSlash && inj.containerClass == ownerSlash) pm else null
                }.distinctBy { "${it.containerClass}#${it.functionName}:${it.signature}" }

                if (providers.isEmpty()) return@run

                val targets = providers.mapNotNull { pm -> resolveProviderPsi(anchor, pm) }
                if (targets.isEmpty()) return@run

                val builder = NavigationGutterIconBuilder
                    .create(AllIcons.Gutter.ImplementedMethod)
                    .setTargets(targets)
                    .setTooltipText("Providers from $compFqn used here (${targets.size})")
                    .setPopupTitle("Component providers")

                result.add(builder.createLineMarkerInfo(anchor))
            }
        }
    }

    private fun resolveProviderPsi(context: PsiElement, pm: Node.ProviderMethod): PsiElement? {
        val project = context.project
        val classFqn = pm.containerClass.replace('/', '.')
        val scope = GlobalSearchScope.allScope(project)
        val psiClass = JavaPsiFacade.getInstance(project).findClass(classFqn, scope) ?: return null

        var method = psiClass.findMethodsByName(pm.functionName, false).firstOrNull()

        if (method == null) {
            val getterName = if (pm.functionName.startsWith("get") && pm.functionName.length > 3)
                pm.functionName
            else
                "get" + pm.functionName.replaceFirstChar { it.uppercase() }
            method = psiClass.findMethodsByName(getterName, false).firstOrNull()
        }

        val nav = method?.navigationElement
        return when (nav) {
            is KtPropertyAccessor -> nav.property.nameIdentifier ?: nav.property
            is KtProperty        -> nav.nameIdentifier ?: nav
            is KtNamedFunction   -> nav.nameIdentifier ?: nav
            else                 -> method
        }
    }

    private fun resolveComponentTypeFqn(prop: KtProperty): String? {
        // 1) declared type: `: Comp`
        (prop.typeReference?.typeElement as? KtUserType)?.referenceExpression?.let { ref ->
            when (val target = ref.mainReference.resolve()) {
                is KtClassOrObject -> return target.fqName?.asString()
                is com.intellij.psi.PsiClass -> return target.qualifiedName
            }
        }
        (prop.initializer as? KtCallExpression)?.calleeExpression?.let { callee ->
            val ref = callee as? KtNameReferenceExpression ?: return@let
            when (val target = ref.mainReference.resolve()) {
                is KtClassOrObject -> return target.fqName?.asString()
                is com.intellij.psi.PsiClass -> return target.qualifiedName
            }
        }
        return null
    }
}
