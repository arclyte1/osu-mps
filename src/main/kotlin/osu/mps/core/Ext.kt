package osu.mps.core

import java.time.Instant

fun String.toInstant(): Instant = Instant.parse(this)

infix operator fun Instant.plus(other: Instant): Instant = plusMillis(other.toEpochMilli())

infix operator fun Instant.minus(other: Instant): Instant = minusMillis(other.toEpochMilli())