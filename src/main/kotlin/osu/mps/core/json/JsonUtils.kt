package osu.mps.core.json

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull


val JsonElement.contentOrNull: String?
    get() = (this as? JsonPrimitive)?.contentOrNull

val JsonElement.intOrNull: Int?
    get() = (this as? JsonPrimitive)?.intOrNull

val JsonElement.longOrNull: Long?
    get() = (this as? JsonPrimitive)?.longOrNull

val JsonElement.shortOrNull: Short?
    get() = (this as? JsonPrimitive)?.intOrNull?.toShort()

val JsonElement.booleanOrNull: Boolean?
    get() = (this as? JsonPrimitive)?.booleanOrNull

val JsonElement.floatOrNull: Float?
    get() = (this as? JsonPrimitive)?.floatOrNull

operator fun JsonElement.get(key: String): JsonElement? {
    return (this as? JsonObject)?.get(key)
}

