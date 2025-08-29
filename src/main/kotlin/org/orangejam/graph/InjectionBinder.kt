package org.orangejam.graph

import tiktok.knit.plugin.FuncName
import tiktok.knit.plugin.InheritJudgement
import tiktok.knit.plugin.buildListCompat
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.exactSingleInjection
import tiktok.knit.plugin.injection.CPF
import tiktok.knit.plugin.injection.ComponentInjections
import tiktok.knit.plugin.injection.DefaultIF
import tiktok.knit.plugin.injection.FactoryIF
import tiktok.knit.plugin.injection.FindInjectionContext
import tiktok.knit.plugin.injection.GlobalInjectionContainer
import tiktok.knit.plugin.injection.Injection
import tiktok.knit.plugin.injection.InjectionFactory
import tiktok.knit.plugin.injection.InjectionFactoryContext
import tiktok.knit.plugin.injection.MultiBindingIF
import tiktok.knit.plugin.injection.checker.ComponentChecker
import tiktok.knit.plugin.injection.checker.ProvidesParentChecker
import tiktok.knit.plugin.injection.ignoreItSelfWithParent
import tiktok.knit.plugin.injection.method

// Copied from asm-plugin, but modified to allow multiple binding (for graph analysis)

//typealias ComponentMultiInjections = MutableMap<FuncName, List<Injection>>

object InjectionBinder {
    private val injectionFactories: Array<InjectionFactory> = arrayOf(
        DefaultIF, FactoryIF, MultiBindingIF,
    )

    private val componentChecker: Array<ComponentChecker> = arrayOf(
        ProvidesParentChecker,
    )

    fun checkComponent(
        inheritJudgement: InheritJudgement, component: BoundComponentClass
    ) = componentChecker.forEach { it.check(inheritJudgement, component) }

    fun buildInjectionFrom(
        findingContext: FindInjectionContext,
    ): List<Result<Injection>> = buildListCompat {
        for (injectionFactory in injectionFactories) {
            var singleInjections = injectionFactory.build(findingContext)
            if (!findingContext.multiProvides) {
                // if onlyCollectionProvides, they shouldn't occur in normal search
                singleInjections = singleInjections.filterNot {
                    it.getOrNull()?.providesMethod?.onlyCollectionProvides == true
                }
            }
            if (singleInjections.isNotEmpty()) addAll(singleInjections)
        }
    }

    @Deprecated("factory context replacement", ReplaceWith("buildInjectionsForComponent"))
    fun buildInjectionsForComponent(
        component: BoundComponentClass,
        globalContainer: GlobalInjectionContainer,
        inheritJudgement: InheritJudgement = InheritJudgement.AlwaysFalse,
    ) = buildInjectionsForComponent(
        component, globalContainer,
        InjectionFactoryContext(inheritJudgement),
    )

    fun buildInjectionsForComponent(
        component: BoundComponentClass,
        globalContainer: GlobalInjectionContainer,
        factoryContext: InjectionFactoryContext,
    ): ComponentInjections {
        val injectionMap = hashMapOf<FuncName, Injection>()
//        val injectionMap = hashMapOf<FuncName, List<Injection>>() // Multimap for multiple binding
        val injectedGetters = component.injectedGetters
        val allProvides = CPF.all(component, true) + globalContainer.all
        for (injectedGetter in injectedGetters) {
            val (_, funcName: FuncName, fieldType: KnitType) = injectedGetter
            val allProvidesForSingleInjection = allProvides.ignoreItSelfWithParent(injectedGetter)
            val findingContext = FindInjectionContext(
                factoryContext, component,
                fieldType, allProvidesForSingleInjection, true,
            )
            val injections = buildInjectionFrom(findingContext)
            injectionMap[funcName] = injections.first().getOrNull()!! // FOR testing
//            injectionMap[funcName] = injections.exactSingleInjection(
//                component, fieldType,
//            ) {
//                CPF.all(component, true).map { it.method }
//            }.getOrThrow()
//            injections.exactSingleInjection(
//                component, fieldType,
//            ) {
//                CPF.all(component, true).map { it.method }
//            }.getOrThrow()
        }
        return injectionMap
    }
}