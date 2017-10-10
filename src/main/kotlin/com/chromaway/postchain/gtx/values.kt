package com.chromaway.postchain.gtx

import com.chromaway.postchain.gtx.messages.DictPair
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import com.chromaway.postchain.gtx.messages.GTXValue as RawGTXValue

// Note: order is same as in ASN.1, thus we can use same integer ids.
enum class GTXValueType {
    NULL, BYTEARRAY, STRING, INTEGER, DICT, ARRAY
}

interface GTXValue {
    val type: GTXValueType
    operator fun get(i: Int): GTXValue
    operator fun get(s: String): GTXValue?
    fun asString(): String
    fun asArray(): Array<out GTXValue>
    fun getSize(): Int
    fun isNull(): Boolean
    fun asDict(): Map<String, GTXValue>
    fun asInteger(): Long
    fun asByteArray(): ByteArray
    fun getRawGTXValue(): RawGTXValue
}

fun wrapValue(r: RawGTXValue): GTXValue {
    when (r.choiceID) {
        RawGTXValue.null_Chosen -> return NullGTXValue()
        RawGTXValue.byteArrayChosen -> return ByteArrayGTXValue(r.byteArray)
        RawGTXValue.stringChosen -> return StringGTXValue(r.string)
        RawGTXValue.integerChosen -> return IntegerGTXValue(r.integer)
        RawGTXValue.dictChosen -> return DictGTXValue(r.dict.associateBy({ it.name }, { wrapValue(it.value) }))
        RawGTXValue.arrayChosen -> return ArrayGTXValue(r.array.map({ wrapValue(it) }).toTypedArray())
    }
    throw Error("Unknown type identifier")
}

fun decodeGTXValue(bytes: ByteArray): GTXValue {
    return wrapValue(RawGTXValue.der_decode(ByteArrayInputStream(bytes)))
}

fun encodeGTXValue(v: GTXValue): ByteArray {
    val outs = ByteArrayOutputStream()
    v.getRawGTXValue().der_encode(outs)
    return outs.toByteArray()
}

val GTXNull = NullGTXValue()

// helper methods:
fun gtx(i: Long): GTXValue { return IntegerGTXValue(i) }
fun gtx(s: String): GTXValue { return StringGTXValue(s) }
fun gtx(ba: ByteArray): GTXValue { return ByteArrayGTXValue(ba) }
fun gtx(vararg a: GTXValue): GTXValue { return ArrayGTXValue(a) }
fun gtx(vararg pairs: Pair<String, GTXValue>): GTXValue { return DictGTXValue(mapOf(*pairs)) }
fun gtx(dict: Map<String, GTXValue>): GTXValue { return DictGTXValue(dict) }

// example:
// gtx("arg1" to gtx(5), "arg2" to GTX_NULL)

abstract class AbstractGTXValue: GTXValue {
    override operator fun get(i: Int): GTXValue {
        throw Error("Type error: array expected")
    }

    override operator fun get(s: String): GTXValue? {
        throw Error("Type error: dict expected")
    }

    override fun asString(): String {
        throw Error("Type error: string expected")
    }

    override fun asArray(): Array<out GTXValue> {
        throw Error("Type error: array expected")
    }

    override fun getSize(): Int {
        throw Error("Type error: array expected")
    }

    override fun isNull(): Boolean {
        return false
    }

    override fun asDict(): Map<String, GTXValue> {
        throw Error("Type error: dict expected")
    }

    override fun asInteger(): Long {
        throw Error("Type error: integer expected")
    }

    override fun asByteArray(): ByteArray {
        throw Error("Type error: byte array expected")
    }
}

class ArrayGTXValue(val array: Array<out GTXValue>): AbstractGTXValue() {
    override val type = GTXValueType.ARRAY

    override operator fun get(i: Int): GTXValue {
        return array[i]
    }

    override fun asArray(): Array<out GTXValue> {
        return array
    }

    override fun getSize(): Int {
        return array.size
    }

    override fun getRawGTXValue(): com.chromaway.postchain.gtx.messages.GTXValue {
        return RawGTXValue.array(Vector<RawGTXValue>(
                array.map { it.getRawGTXValue() }
        ))
    }
}

fun makeDictPair (name: String, value: RawGTXValue): DictPair {
    val dp = DictPair()
    dp.name = name
    dp.value = value
    return dp
}

class DictGTXValue(val dict: Map<String, GTXValue>): AbstractGTXValue() {
    override val type = GTXValueType.DICT

    override operator fun get(s: String): GTXValue? {
        return dict[s]
    }

    override fun asDict(): Map<String, GTXValue> {
        return dict
    }

    override fun getRawGTXValue(): com.chromaway.postchain.gtx.messages.GTXValue {
        return RawGTXValue.dict(
                Vector<DictPair>(
                    dict.entries.map { makeDictPair(it.key, it.value.getRawGTXValue()) }
                ))
    }
}

class NullGTXValue: AbstractGTXValue() {
    override val type: GTXValueType = GTXValueType.NULL
    override fun isNull(): Boolean {
        return true
    }

    override fun getRawGTXValue(): com.chromaway.postchain.gtx.messages.GTXValue {
        return RawGTXValue.null_(null)
    }
}

class IntegerGTXValue(val integer: Long): AbstractGTXValue() {
    override val type: GTXValueType = GTXValueType.INTEGER
    override fun asInteger(): Long {
        return integer
    }

    override fun getRawGTXValue(): RawGTXValue {
        return RawGTXValue.integer(integer)
    }
}

class StringGTXValue(val string: String): AbstractGTXValue() {
    override val type: GTXValueType = GTXValueType.STRING
    override fun asString(): String {
        return string
    }

    override fun getRawGTXValue(): RawGTXValue {
        return RawGTXValue.string(string)
    }
}

class ByteArrayGTXValue(val bytearray: ByteArray): AbstractGTXValue() {
    override val type: GTXValueType = GTXValueType.BYTEARRAY
    override fun asByteArray(): ByteArray {
        return bytearray
    }
    override fun getRawGTXValue(): RawGTXValue {
        return RawGTXValue.byteArray(bytearray)
    }
}
