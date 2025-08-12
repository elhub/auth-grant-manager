package no.elhub.auth.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import java.io.File
import java.net.URI
import java.nio.file.Files
import kotlin.io.path.Path

fun validateJsonApiSpec(schemasLocation: String) {
    val objectMapper = jacksonObjectMapper()
    val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
    val schema = factory.getSchema(URI.create("https://json-schema.org/draft/2020-12/schema"))

    val schemasToCheck = Files.list(Path(schemasLocation))
        .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".schema.json") }
        .map { it.fileName.toString() }
        .toList()

    val outputs = schemasToCheck.map {
        it to schema.validate(objectMapper.readTree(File("$schemasLocation/$it").inputStream()))
    }

    val erroneousOutputs = outputs.filter { it.second.isNotEmpty() }
    if (erroneousOutputs.isNotEmpty()) {
        erroneousOutputs.forEach { (fileName, errors) ->
            println("👮 Found errors in $fileName:")
            errors.forEach { error ->
                println(" ❌ $error")
            }
        }
    } else {
        println("🎉 All JSON API schemas are valid.")
    }
}
