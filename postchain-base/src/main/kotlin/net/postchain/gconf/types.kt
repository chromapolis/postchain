package net.postchain.gconf

import javax.security.auth.login.Configuration
import kotlin.reflect.KClass


interface GTree {
    val rootNode: GNode

    fun addConfig(config: Configuration): Void
    fun getNode(name: String): GNode
}


interface GNode {

    val father: GNode
    val babies: Array<GNode> // children is a keyword
    val name: String
    val value: Any


    fun <T: Any> setValue(value: T, clazz: KClass<out T>)
    // is there a smarter way to get the type?
    // fun <T: Any> cast(any: Any, clazz: KClass<out T>): T = clazz.javaObjectType.cast(any)
    // https://stackoverflow.com/questions/41219748/dynamic-cast-in-kotlin

    fun <T: Any> getValueWithType(): T
    fun addBaby(node: GNode)
    fun removeBaby(node: GNode)
}
