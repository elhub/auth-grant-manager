package no.elhub.auth.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path

fun validateJsonApiSpec(schemasLocation: String) {
    val objectMapper = jacksonObjectMapper()

    val registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)

    val metaSchema: Schema = registry.getSchema(
        SchemaLocation.of("https://json-schema.org/draft/2020-12/schema")
    )

    val schemasToCheck = Files.list(Path(schemasLocation))
        .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".schema.json") }
        .map { it.fileName.toString() }
        .toList()

    val outputs = schemasToCheck.map { fileName ->
        val node = objectMapper.readTree(File("$schemasLocation/$fileName"))
        fileName to metaSchema.validate(node.toString(), InputFormat.JSON)
    }

    val erroneousOutputs = outputs.filter { it.second.isNotEmpty() }

    if (erroneousOutputs.isNotEmpty()) {
        erroneousOutputs.forEach { (fileName, errors) ->
            println("👮 Found errors in $fileName:")
            errors.forEach { error -> println(" ❌ $error") }
        }
    } else {
        println("🎉 All JSON API schemas are valid.")
    }
}
