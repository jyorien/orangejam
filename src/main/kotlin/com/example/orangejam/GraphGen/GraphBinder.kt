package com.example.orangejam.GraphGen

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import tiktok.knit.plugin.InheritJudgement
import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.KnitContext
import tiktok.knit.plugin.MetadataContainer
import tiktok.knit.plugin.PropAccName
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.BoundComponentMapping
import tiktok.knit.plugin.element.BoundCompositeComponent
import tiktok.knit.plugin.element.ComponentClass
import tiktok.knit.plugin.element.ComponentMapping
import tiktok.knit.plugin.element.CompositeComponent
import tiktok.knit.plugin.element.InjectedGetter
import tiktok.knit.plugin.element.KnitGenericType
import tiktok.knit.plugin.element.KnitSingleton
import tiktok.knit.plugin.element.KnitType
import tiktok.knit.plugin.element.ProvidesMethod
import tiktok.knit.plugin.element.attach2BoundMapping
import tiktok.knit.plugin.fqn
import tiktok.knit.plugin.globalProvidesInternalName
import tiktok.knit.plugin.injection.GlobalInjectionContainer
import tiktok.knit.plugin.writer.ComponentWriter
import tiktok.knit.plugin.writer.GlobalProvidesWriter
import java.io.File
import kotlin.collections.iterator

class GraphBoundComponentClass(
    internalName: InternalName,
    parents: List<BoundCompositeComponent>,
    typeParams: List<KnitGenericType>,
    provides: List<ProvidesMethod>,
    compositeComponents: Map<PropAccName, BoundCompositeComponent>, // property access name -> component
    injectedGetters: List<InjectedGetter>,
    singletons: List<KnitSingleton>,
    isInterface: Boolean,
) {
    val boundComponent = BoundComponentClass(internalName, parents, typeParams, provides, compositeComponents, injectedGetters, singletons, isInterface)

}
typealias GraphBoundComponentMapping = MutableMap<InternalName, GraphBoundComponentClass>

// GraphContext class is copied from the knit-asm module with all the necessary functions - zi heng

// Right now still trying to figure out what this class does but it seem to be the code that resolve the dependency
class GraphContext(
    val nodes: List<ClassNode>,
    allComponents: List<ComponentClass>,
) : KnitContext {
    override val componentMap: MutableMap<InternalName, ComponentClass> =
        allComponents.associateBy { it.internalName }.toMutableMap()

    override val boundComponentMap: MutableMap<InternalName, BoundComponentClass> = mutableMapOf()

    override val globalInjectionContainer: GlobalInjectionContainer = GlobalInjectionContainer(
        componentMap.values.toList().asTestBound(),
    )

    override val inheritJudgement: InheritJudgement = BuiltinInheritJudgement

    private fun buildBindingForAll(
        context: KnitContext,
        inheritJudgement: InheritJudgement,
        componentMapping: ComponentMapping,
    ) {
        val componentMap = context.componentMap
        val boundComponentMap = context.boundComponentMap
        for (component in componentMap.values) {
            // detectDuplication(component)
            val bound = component.attach2BoundMapping(
                componentMapping, boundComponentMap,
            )
            InjectionBinder.checkComponent(inheritJudgement, bound)
            val injections = InjectionBinder.buildInjectionsForComponent(
                bound, context.globalInjectionContainer, inheritJudgement,
            )
            bound.injections = injections
            boundComponentMap[component.internalName] = bound
        }
    }

    init {
        // knit main
        buildBindingForAll(
            this, BuiltinInheritJudgement, BuiltinComponentMapping(componentMap),
        )
    }

    class BuiltinComponentMapping(
        private val componentMap: Map<InternalName, ComponentClass>,
    ) : ComponentMapping {
        override fun invoke(internalName: InternalName): ComponentClass {
            val existed = componentMap[internalName]
            if (existed != null) return existed
            val clazz = Class.forName(internalName.fqn)
            val parentNames: MutableList<String> = clazz.interfaces.map { it.name }.toMutableList()
            val superName = clazz.superclass?.name
            if (superName != null) parentNames += superName
            val parents = parentNames.map { CompositeComponent(KnitType.from(it.replace(".", "/"))) }
            return ComponentClass(internalName, parents)
        }
    }

    fun toClassLoader(): DelegateClassLoader {
        // FastNewInstance
        val componentWriter = ComponentWriter(this)
        val globalWriter = GlobalProvidesWriter(this)
        for (node in nodes) {
            if (node.name == globalProvidesInternalName) {
                globalWriter.write(node)
            } else {
                componentWriter.write(node)
            }
        }
        return childLoader(nodes)
    }
}

object BuiltinInheritJudgement : InheritJudgement {
    override fun inherit(thisName: InternalName, parentName: InternalName): Boolean {
        return try {
            val thisClass = Class.forName(Type.getType("L$thisName;").className)
            val parentClass = Class.forName(Type.getType("L$parentName;").className)
            parentClass.isAssignableFrom(thisClass)
        } catch (e: ClassNotFoundException) {
            // ignore result
            false
        }
    }
}

class DelegateClassLoader(
    val context: KnitContext, parent: ClassLoader, classNodes: List<ClassNode>
) : ClassLoader(parent) {
    private val nodeNames = classNodes.associateBy { Type.getObjectType(it.name).className }
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(this) {
            if (name !in nodeNames) {
                if (!name.startsWith("knit")) return parent.loadClass(name)
                val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
                ClassReader(name).accept(classWriter, ClassReader.EXPAND_FRAMES)
                val byteArray = classWriter.toByteArray()
                return defineClass(name, byteArray, 0, byteArray.size)
            }
            val c = findLoadedClass(name)
            return c ?: transformedClass(name)
        }
    }

    private fun transformedClass(name: String): Class<*> {
        val arr = getClassContent(name)
        return defineClass(name, arr, 0, arr.size)
    }

    fun getClassContent(name: String): ByteArray {
        val visitor = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        val node = requireNotNull(nodeNames[name])
        node.accept(visitor)
        return visitor.toByteArray()
    }

    fun dump(dir: File = File("").absoluteFile) {
        for ((_, node) in nodeNames) {
            val visitor = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
            val file = File(dir, "${node.name}.class")
            if (!file.parentFile.exists()) file.parentFile.mkdirs()
            if (file.exists()) file.delete()
            file.createNewFile()
            node.accept(visitor)
            file.writeBytes(visitor.toByteArray())
        }
    }
}

fun KnitContext.childLoader(classNodes: List<ClassNode>): DelegateClassLoader {
    return DelegateClassLoader(this, this::class.java.classLoader, classNodes)
}

fun List<MetadataContainer>.toContext(): GraphContext {
    val nodes = map { it.node }
    val components = map { it.toComponent() }
    return GraphContext(nodes, components)
}

fun List<ComponentClass>.asTestBound(map: BoundComponentMapping = hashMapOf()): List<BoundComponentClass> {
    val componentMapping = asComponentMapping()
    return map {
        it.attach2BoundMapping(componentMapping, map)
    }
}

fun List<ComponentClass>.asComponentMapping(): ComponentMapping {
    return GraphContext.BuiltinComponentMapping(associateBy { it.internalName })
}