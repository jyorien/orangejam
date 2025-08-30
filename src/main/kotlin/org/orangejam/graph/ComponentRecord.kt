package org.orangejam.graph

import com.google.common.collect.HashMultimap
import tiktok.knit.plugin.InternalName
import tiktok.knit.plugin.PropAccName
import tiktok.knit.plugin.buildListCompat
import tiktok.knit.plugin.element.BoundComponentClass
import tiktok.knit.plugin.element.ProvidesMethod
import tiktok.knit.plugin.printable
import java.util.LinkedList
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

typealias CompositeRecords = HashMultimap<InternalName, ComponentRecord>

data class CompositeHop(
    val ownerClass: InternalName,
    val propName: PropAccName,
    val targetClass: InternalName,
    val isPublic: Boolean
)

class ComponentRecord(
    val name: InternalName,
    val steps: List<CompositeHop>,
) {

    companion object {
        fun from(records: Collection<ComponentRecord>): CompositeRecords {
            val recordHashMultimap: CompositeRecords = HashMultimap.create()
            for (record in records) {
                recordHashMultimap.put(record.name, record)
            }
            return recordHashMultimap
        }
    }
}

fun BoundComponentClass.getCompositeRecords(): CompositeRecords {
    val allComposite = allComposite(LinkedList(), true)
    return ComponentRecord.from(allComposite)
}

private fun BoundComponentClass.allComposite(
    currentWay: LinkedList<CompositeHop>,
    includePrivate: Boolean,
): List<ComponentRecord> = buildListCompat {
    for ((acc, compositeComponent) in compositeComponents) {
        val hop = CompositeHop(
            ownerClass = this@allComposite.internalName,
            propName   = acc,
            targetClass = compositeComponent.component.internalName,
            isPublic   = compositeComponent.isPublic
        )

        currentWay.addLast(hop)

        if (compositeComponent.isPublic || includePrivate) {
            add(
                ComponentRecord(
                    name = compositeComponent.component.internalName,
                    steps = currentWay.toList()
                )
            )
            addAll(compositeComponent.component.allComposite(currentWay, includePrivate = false))
        }

        currentWay.removeLast()
    }
}