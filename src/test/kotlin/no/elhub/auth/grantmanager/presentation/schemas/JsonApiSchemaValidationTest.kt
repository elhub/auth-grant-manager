package no.elhub.auth.grantmanager.presentation.schemas

import com.github.erosb.jsonsKema.JsonParser
import com.github.erosb.jsonsKema.SchemaLoader
import com.github.erosb.jsonsKema.Validator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class JsonApiSchemaValidationTest : FunSpec({

    /**
     * This test validates that all JSON data files conforms to the JSON:API v1.1 specification.
     * The JSON:API v1.1 schema is downloaded from the following sources:
     *      - Issue tracker: https://github.com/json-api/json-api/issues/1788
     *      - Schema repository: https://github.com/VGirol/json-api/blob/schema-1.1/_schemas/1.1/schema.json
     *      - Raw schema URL: https://raw.githubusercontent.com/VGirol/json-api/schema-1.1/_schemas/1.1/schema.json
     *  The schema is stored locally in json-api-1.1-schema.json to avoid network latency and ensure reliable testing.
     */

    val jsonApiSchemaPath = Paths.get("src/test/resources/json-api-1.1-schema.json")
    if (!Files.exists(jsonApiSchemaPath)) {
        throw IllegalStateException("Schema file not found: json-api-1.1-schema.json")
    }

    val schema1 = SchemaLoader.forURL(jsonApiSchemaPath.toUri().toString()).load()
    val validator = Validator.forSchema(schema1)

    val schemaDirUrl = object {}.javaClass.getResource("/requests")
        ?: throw IllegalStateException("The folder /requests does not exist in the classpath.")

    val schemaDir = File(schemaDirUrl.toURI())
    require(schemaDir.exists() && schemaDir.isDirectory) {
        "The folder ${schemaDir.canonicalPath} does not exist or is not a directory."
    }

    schemaDir.walkTopDown()
        .filter { it.extension == "json" }
        .forEach { schemaFile ->
            test("`${schemaFile.name}` conforms to JSON:API v1.1") {
                val instance = JsonParser(schemaFile.readText()).parse()
                val res = validator.validate(instance)

                // validate will return null if the json data comp
                res.shouldBeNull()
            }
        }
})
