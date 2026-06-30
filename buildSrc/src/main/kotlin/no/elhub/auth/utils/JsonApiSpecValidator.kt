package no.elhub.auth.utils

import com.github.erosb.jsonsKema.AggregateSchemaLoadingException
import com.github.erosb.jsonsKema.JsonParser
import com.github.erosb.jsonsKema.SchemaLoader
import com.github.erosb.jsonsKema.SchemaLoaderConfig
import com.github.erosb.jsonsKema.SchemaLoadingException
import java.io.File
import java.net.URI
import java.nio.file.Files
import kotlin.io.path.Path

fun validateJsonApiSpec(schemasLocation: String) {
    val schemasToCheck = Files.list(Path(schemasLocation))
        .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".schema.json") }
        .map { it.fileName.toString() }
        .toList()

    val additionalMappings = schemasToCheck.associate { fileName ->
        URI("mem://input/$fileName") to File("$schemasLocation/$fileName").readText()
    }
    val config = SchemaLoaderConfig.createDefaultConfig(additionalMappings)

    val errors = mutableMapOf<String, List<String>>()

    schemasToCheck.forEach { fileName ->
        val content = File("$schemasLocation/$fileName").readText()
        try {
            SchemaLoader(JsonParser(content).parse(), config).load()
        } catch (e: AggregateSchemaLoadingException) {
            errors[fileName] = e.causes.map { it.message ?: "Unknown error" }
        } catch (e: SchemaLoadingException) {
            errors[fileName] = listOf(e.message ?: "Unknown schema loading error")
        } catch (e: Exception) {
            errors[fileName] = listOf("Failed to parse: ${e.message}")
        }
    }

    if (errors.isNotEmpty()) {
        errors.forEach { (fileName, fileErrors) ->
            println("👮 Found errors in $fileName:")
            fileErrors.forEach { error -> println(" ❌ $error") }
        }
        error("JSON API schema validation failed")
    } else {
        println("🎉 All JSON API schemas are valid.")
    }
}
