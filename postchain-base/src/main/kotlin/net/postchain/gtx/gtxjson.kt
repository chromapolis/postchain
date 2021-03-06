// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package net.postchain.gtx

import net.postchain.common.toHex
import net.postchain.core.ProgrammerMistake
import com.google.gson.*
import java.lang.reflect.Type

class GTXValueAdapter : JsonDeserializer<GTXValue>, JsonSerializer<GTXValue> {

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): GTXValue {
        if (json.isJsonPrimitive) {
            val prim = json.asJsonPrimitive
            if (prim.isBoolean)
                return gtx(if (prim.asBoolean) 1L else 0L)
            else if (prim.isNumber)
                return gtx(prim.asLong)
            else if (prim.isString)
                return gtx(prim.asString)
            else throw ProgrammerMistake("Can't deserialize JSON primitive")
        } else if (json.isJsonArray) {
            val arr = json.asJsonArray
            return gtx(*arr.map({ deserialize(it, typeOfT, context) }).toTypedArray())
        } else if (json.isJsonNull) {
            return GTXNull
        } else if (json.isJsonObject) {
            val obj = json.asJsonObject
            val mut = mutableMapOf<String, GTXValue>()
            obj.entrySet().forEach {
                mut[it.key] = deserialize(it.value, typeOfT, context)
            }
            return gtx(mut)
        } else throw ProgrammerMistake("Could not deserialize JSON element")
    }

    private fun encodeDict(d: GTXValue, t: Type, c: JsonSerializationContext): JsonObject {
        val o = JsonObject()
        for ((k, v) in d.asDict()) {
            o.add(k, serialize(v, t, c))
        }
        return o
    }

    private fun encodeArray(d: GTXValue, t: Type, c: JsonSerializationContext): JsonArray {
        val a = JsonArray()
        for (v in d.asArray()) {
            a.add(serialize(v, t, c))
        }
        return a
    }

    override fun serialize(v: GTXValue, t: Type, c: JsonSerializationContext): JsonElement {
        when (v.type) {
            GTXValueType.INTEGER -> return JsonPrimitive(v.asInteger())
            GTXValueType.STRING -> return JsonPrimitive(v.asString())
            GTXValueType.NULL -> return JsonNull.INSTANCE
            GTXValueType.BYTEARRAY -> return JsonPrimitive(v.asByteArray().toHex())
            GTXValueType.DICT -> return encodeDict(v, t, c)
            GTXValueType.ARRAY -> return encodeArray(v, t, c)
        }
    }
}

fun make_gtx_gson(): Gson {
    return GsonBuilder().
            registerTypeAdapter(GTXValue::class.java, GTXValueAdapter()).
            serializeNulls().
            create()!!
}

fun gtxToJSON(gtxValue: GTXValue, gson: Gson): String {
    return gson.toJson(gtxValue, GTXValue::class.java)
}