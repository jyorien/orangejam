package org.orangejam.navigation

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.project.DumbService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.orangejam.graph.Node

class DiFindUsagesHandlerFactory : FindUsagesHandlerFactory() {


    override fun canFindUsages(element: PsiElement): Boolean =
        unwrapProvider(element) != null

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler {
        val t = unwrapProvider(element)!!

        return object : FindUsagesHandler(t.declaration) {

            override fun getSecondaryElements(): Array<PsiElement> {
                val targets = collectInjectedPropertyAnchors(t) ?: return PsiElement.EMPTY_ARRAY
                return targets.toTypedArray()
            }

            override fun processElementUsages(
                element: PsiElement,
                processor: Processor<in UsageInfo>,
                options: FindUsagesOptions
            ): Boolean {
                val ok = super.processElementUsages(element, processor, options)
                val targets = collectInjectedPropertyAnchors(t) ?: return ok
                targets.forEach { anchor -> processor.process(UsageInfo(anchor)) }
                return ok
            }

            private fun collectInjectedPropertyAnchors(t: Target): List<PsiElement>? {
                val project = t.declaration.project
                if (DumbService.isDumb(project)) return emptyList()

                val graph = project.getService(GraphProvider::class.java).graph()

                val injections = graph.edges.mapNotNull { e ->
                    val pm = e.source.data as? Node.ProviderMethod ?: return@mapNotNull null
                    if (pm.containerClass == t.containerSlash &&
                        pm.functionName == t.name
                    ) e.destination.data as? Node.InjectionField else null
                }
                if (injections.isEmpty()) return emptyList()

                val scope = GlobalSearchScope.allScope(project)

                val anchors = ArrayList<PsiElement>(injections.size)
                for (inj in injections) {
                    val ownerFqn = inj.containerClass.replace('/', '.')
                    val propName = inj.field.removePrefix("get").replaceFirstChar { it.lowercase() }

                    val psiClass = JavaPsiFacade.getInstance(project).findClass(ownerFqn, scope) ?: continue
                    val ktOwner = psiClass.navigationElement as? KtClassOrObject ?: continue

                    val prop = (
                            ktOwner.body?.properties?.firstOrNull { it.name == propName }
                                ?: ktOwner.companionObjects
                                    .asSequence()
                                    .flatMap { it.body?.properties.orEmpty().asSequence() }
                                    .firstOrNull { it.name == propName }
                            ) ?: continue

                    anchors += (prop.nameIdentifier ?: prop)
                }
                return anchors
            }
        }
    }

    private data class Target(
        val declaration: PsiElement,
        val containerSlash: String,
        val name: String,
        val arity: Int
    )

    private fun unwrapProvider(el: PsiElement): Target? {
        (el as? KtNamedFunction)?.let { fn ->
            if (isProvides(fn)) {
                val clsFqn = fn.containingClassOrObject?.fqName?.asString() ?: return null
                return Target(fn, clsFqn.replace('.', '/'), fn.name ?: return null, fn.valueParameters.size)
            }
        }
        (el.parent as? KtNamedFunction)?.let { fn ->
            if (isProvides(fn)) {
                val clsFqn = fn.containingClassOrObject?.fqName?.asString() ?: return null
                return Target(fn, clsFqn.replace('.', '/'), fn.name ?: return null, fn.valueParameters.size)
            }
        }
        (el.parent as? KtAnnotationEntry)?.parent?.parent?.let { maybeFn ->
            (maybeFn as? KtNamedFunction)?.let { fn ->
                if (isProvides(fn)) {
                    val clsFqn = fn.containingClassOrObject?.fqName?.asString() ?: return null
                    return Target(fn, clsFqn.replace('.', '/'), fn.name ?: return null, fn.valueParameters.size)
                }
            }
        }
        (el as? PsiMethod)?.let { m ->
            if (isProvides(m)) {
                val cls = m.containingClass ?: return null
                val fqn = cls.qualifiedName ?: return null
                return Target(m, fqn.replace('.', '/'), m.name, m.parameterList.parametersCount)
            }
        }
        (el.parent as? PsiMethod)?.let { m ->
            if (isProvides(m)) {
                val cls = m.containingClass ?: return null
                val fqn = cls.qualifiedName ?: return null
                return Target(m, fqn.replace('.', '/'), m.name, m.parameterList.parametersCount)
            }
        }
        return null
    }

    private fun isProvides(fn: KtNamedFunction): Boolean =
        fn.annotationEntries.any { ann ->
            val t = ann.typeReference?.text ?: return@any false
            t == "knit.Provides" || t == "Provides" || t.endsWith(".Provides")
        }

    private fun isProvides(m: PsiMethod): Boolean =
        m.modifierList.annotations.any { a ->
            val qn = a.qualifiedName ?: return@any false
            qn == "knit.Provides" || qn.endsWith(".Provides") }
}


