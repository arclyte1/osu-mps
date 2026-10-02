package osu.mps.data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.BatchUpsertStatement
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.time.Instant

internal fun String?.toInstantOrNull(): Instant? = this?.let(Instant::parse)

internal fun <E> Table.upsertCount(items: Array<out E>, body: BatchUpsertStatement.(E) -> Unit): Int {
    if (items.isEmpty()) return 0

    val statement = BatchUpsertStatement(
        table = this,
        onUpdate = null,
        onUpdateExclude = null,
        where = null,
        shouldReturnGeneratedValues = false,
    )
    items.forEach { item ->
        statement.addBatch()
        statement.body(item)
    }
    statement.execute(TransactionManager.current())
    return statement.insertedCount
}
