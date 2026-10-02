package osu.mps.core.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/**
 * JSON element wrapper that tracks the current [path] while navigating the tree.
 *
 * Usage:
 * ```
 * val root = JsonCursor.parse(jsonText)
 * val matchId = root["match"]["id"].requireInt()
 * val events = root["events"].mapArray { event -> ... }
 * ```
 */
class JsonCursor internal constructor(
    val element: JsonElement,
    val path: JsonPath,
) {
    val pathString: String get() = path.toString()

    val isNull: Boolean get() = element is JsonNull
    val isObject: Boolean get() = element is JsonObject
    val isArray: Boolean get() = element is JsonArray
    val isPrimitive: Boolean get() = element is JsonPrimitive

    val jsonObject: JsonObject? get() = element as? JsonObject
    val jsonArray: JsonArray? get() = element as? JsonArray
    val jsonPrimitive: JsonPrimitive? get() = element as? JsonPrimitive

    val contentOrNull: String? get() = jsonPrimitive?.contentOrNull
    val shortOrNull: Short? get() = jsonPrimitive?.intOrNull?.toShort()
    val intOrNull: Int? get() = jsonPrimitive?.intOrNull
    val longOrNull: Long? get() = jsonPrimitive?.longOrNull
    val floatOrNull: Float? get() = jsonPrimitive?.floatOrNull
    val doubleOrNull: Double? get() = jsonPrimitive?.doubleOrNull
    val booleanOrNull: Boolean? get() = jsonPrimitive?.booleanOrNull
    val instantOrNull: Instant? get() = jsonPrimitive?.contentOrNull?.let(Instant::parse)

    operator fun get(key: String): JsonCursor {
        val obj = requireObjectElement()
        val childElement = obj[key] ?: throw JsonCursorException.missing(path.field(key))
        return JsonCursor(childElement, path.field(key))
    }

    fun opt(key: String): JsonCursor? {
        val obj = jsonObject ?: return null
        val childElement = obj[key] ?: return null
        return JsonCursor(childElement, path.field(key))
    }

    fun containsKey(key: String): Boolean = jsonObject?.containsKey(key) == true

    operator fun get(index: Int): JsonCursor {
        val arr = requireArrayElement()
        val childElement = arr.getOrNull(index)
            ?: throw JsonCursorException.missing(path.index(index))
        return JsonCursor(childElement, path.index(index))
    }

    fun requireObject(): JsonObject {
        return requireObjectElement()
    }

    fun requireArray(): JsonArray {
        return requireArrayElement()
    }

    fun requireShort(): Short = shortOrNull ?: throw JsonCursorException.invalidType(path, "short", element)
    fun requireInt(): Int = intOrNull ?: throw JsonCursorException.invalidType(path, "integer", element)
    fun requireLong(): Long = longOrNull ?: throw JsonCursorException.invalidType(path, "integer", element)
    fun requireString(): String = contentOrNull ?: throw JsonCursorException.invalidType(path, "string", element)
    fun requireBoolean(): Boolean = booleanOrNull ?: throw JsonCursorException.invalidType(path, "boolean", element)
    fun requireInstant(): Instant = instantOrNull ?: throw JsonCursorException.invalidType(path, "instant", element)

    fun <T> mapArray(transform: (JsonCursor) -> T): List<T> {
        val arr = requireArrayElement()
        return arr.mapIndexed { index, item ->
            transform(JsonCursor(item, path.index(index)))
        }
    }

    fun <T> flatMapArray(transform: (JsonCursor) -> Iterable<T>): List<T> {
        val arr = requireArrayElement()
        return arr.flatMapIndexed { index, item ->
            transform(JsonCursor(item, path.index(index)))
        }
    }

    fun <T> mapNotNullArray(transform: (JsonCursor) -> T?): List<T> {
        val arr = requireArrayElement()
        return arr.mapIndexedNotNull { index, item ->
            transform(JsonCursor(item, path.index(index)))
        }
    }

    fun filterArray(predicate: (JsonCursor) -> Boolean): List<JsonCursor> {
        val arr = requireArrayElement()
        return arr.mapIndexedNotNull { index, item ->
            JsonCursor(item, path.index(index)).takeIf(predicate)
        }
    }

    fun forEachArray(action: (JsonCursor) -> Unit) {
        val arr = requireArrayElement()
        arr.forEachIndexed { index, item ->
            action(JsonCursor(item, path.index(index)))
        }
    }

    fun <T> mapObject(transform: (String, JsonCursor) -> T): List<T> {
        val obj = requireObjectElement()
        return obj.map { (key, value) ->
            transform(key, JsonCursor(value, path.field(key)))
        }
    }

    private fun requireObjectElement(): JsonObject =
        jsonObject ?: throw JsonCursorException.invalidType(path, "object", element)

    private fun requireArrayElement(): JsonArray =
        jsonArray ?: throw JsonCursorException.invalidType(path, "array", element)

    companion object {
        fun root(element: JsonElement): JsonCursor = JsonCursor(element, JsonPath.Root)

        fun parse(jsonText: String, json: Json = Json {
            ignoreUnknownKeys = true
        }
        ): JsonCursor = root(json.parseToJsonElement(jsonText))
    }
}

sealed class JsonPath {
    data object Root : JsonPath() {
        override fun toString(): String = "$"
    }

    data class Field(val parent: JsonPath, val name: String) : JsonPath() {
        override fun toString(): String = "${parent}.${name}"
    }

    data class Index(val parent: JsonPath, val index: Int) : JsonPath() {
        override fun toString(): String = "${parent}[$index]"
    }

    fun field(name: String): JsonPath = Field(this, name)
    fun index(index: Int): JsonPath = Index(this, index)
}

class JsonCursorException(
    val jsonPath: String,
    message: String,
) : Exception("$jsonPath: $message") {

    companion object {
        fun missing(path: JsonPath): JsonCursorException =
            JsonCursorException(path.toString(), "missing field")

        fun invalidType(path: JsonPath, expected: String, actual: JsonElement): JsonCursorException =
            JsonCursorException(path.toString(), "expected $expected, got ${actual.kindName()}")
    }
}

private fun JsonElement.kindName(): String = when (this) {
    is JsonNull -> "null"
    is JsonObject -> "object"
    is JsonArray -> "array"
    is JsonPrimitive -> when {
        isString -> "string"
        longOrNull != null || intOrNull != null || doubleOrNull != null -> "number"
        booleanOrNull != null -> "boolean"
        else -> "primitive"
    }
    else -> javaClass.simpleName
}

inline fun <reified T : Enum<T>> JsonCursor.requireEnum(
    crossinline dbValueOf: (String) -> T?,
    enumName: String = T::class.simpleName ?: "enum",
): T {
    val raw = requireString()
    return dbValueOf(raw) ?: throw JsonCursorException(
        pathString,
        "unknown $enumName value: $raw",
    )
}
