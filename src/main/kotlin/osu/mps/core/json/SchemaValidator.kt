package osu.mps.core.json

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import org.slf4j.LoggerFactory
import osu.mps.osu.GetMatchResponseSchemaValidator
import osu.mps.osu.GetMatchResponseSchemaValidator.logger
import osu.mps.osu.GetMatchResponseSchemaValidator.objectMapper


abstract class SchemaValidator(
    val schemaRes: String
) {

    protected val schema: JsonSchema = loadSchema()
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val objectMapper = ObjectMapper()

    fun validate(jsonText: String): SchemaValidationResult {
        val jsonNode: JsonNode = objectMapper.readTree(jsonText)
        val messages = schema.validate(jsonNode)

        val warnings = mutableListOf<SchemaValidationIssue>()
        val errors = mutableListOf<SchemaValidationIssue>()

        for (message in messages) {
            val issue = message.toIssue()
            if (message.isAdditionalPropertyWarning()) {
                warnings.add(issue)
            } else {
                errors.add(issue)
            }
        }

        if (warnings.isNotEmpty()) {
            logger.warn(
                "Schema warnings ({}): {}",
                warnings.size,
                warnings.joinToString("; ") { "${it.path}: ${it.message}" },
            )
        }

        return SchemaValidationResult(warnings = warnings, errors = errors)
    }

    private fun loadSchema(): JsonSchema {
        val schemaStream = checkNotNull(
            javaClass.getResourceAsStream(schemaRes)
        ) { "Schema resource $schemaRes not found" }
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(schemaStream)
    }

    protected fun ValidationMessage.toIssue(): SchemaValidationIssue =
        SchemaValidationIssue(
            path = instanceLocation.toString().ifEmpty { "$" },
            messageKey = messageKey ?: type ?: code ?: "unknown",
            message = message,
        )

    protected fun ValidationMessage.isAdditionalPropertyWarning(): Boolean {
        if (messageKey == "additionalProperties") return true
        return message.contains("additional properties", ignoreCase = true)
    }
}

data class SchemaValidationIssue(
    val path: String,
    val messageKey: String,
    val message: String,
)

data class SchemaValidationResult(
    val warnings: List<SchemaValidationIssue>,
    val errors: List<SchemaValidationIssue>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}
