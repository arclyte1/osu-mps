package osu.mps.core.ratelimiter

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.util.AttributeKey

sealed class RequestPriority(open val value: Int) : Comparable<RequestPriority> {

    object Low : RequestPriority(0)
    object Normal : RequestPriority(1)
    object High : RequestPriority(2)
    data class Custom(override val value: Int) : RequestPriority(3)

    override fun compareTo(other: RequestPriority): Int {
        return value.compareTo(other.value)
    }
}

val requestPriorityKey = AttributeKey<RequestPriority>("RequestPriority")

fun HttpRequestBuilder.osuPriority(priority: RequestPriority) {
    attributes.put(requestPriorityKey, priority)
}

fun HttpRequestBuilder.osuPriorityOrNull(): RequestPriority? =
    attributes.getOrNull(requestPriorityKey)
