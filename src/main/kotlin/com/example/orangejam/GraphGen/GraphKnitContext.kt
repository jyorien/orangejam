package com.example.orangejam.GraphGen

import tiktok.knit.plugin.InheritJudgement
import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.element.ComponentClass
import tiktok.knit.plugin.injection.GlobalInjectionContainer

interface GraphKnitContext {
    val componentMap: MutableMap<InternalName, ComponentClass>
    val boundComponentMap: MutableMap<InternalName, GraphBoundComponentClass>
    val globalInjectionContainer: GlobalInjectionContainer
    val inheritJudgement: InheritJudgement
}