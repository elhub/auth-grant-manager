package no.elhub.auth.schemas.requests

import com.github.erosb.jsonsKema.JsonParser
import com.github.erosb.jsonsKema.Schema
import com.github.erosb.jsonsKema.SchemaLoader
import com.github.erosb.jsonsKema.SchemaLoaderConfig
import com.github.erosb.jsonsKema.ValidationFailure
import com.github.erosb.jsonsKema.Validator
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths

class AuthorizationRequestSchemaValidationTest : DescribeSpec({

    describe("GET /authorization-requests") {
        val authReqListSchema = loadSchemaFromFile("/schemas/authorization-requests-get-response.schema.json")

        it("should validate that response complies with the custom JSON schema") {
            val jsonDataPath = "/requests/get-authorization-response.json"
            val result = validateJsonData(authReqListSchema, jsonDataPath)
            checkValidateResult(result)
        }
    }

    describe("PATCH /authorization-requests") {
        val authReqUpdateSchema = loadSchemaFromFile("/schemas/authorization-request-patch-request.json", true)
        val authReqReadSchema = loadSchemaFromFile("/schemas/authorization-request-response.schema.json", true)

        it("should validate that request complies with the custom JSON schema") {
            val jsonDataPath = "/requests/patch-authorization-request-request-body.json"
            val result = validateJsonData(authReqUpdateSchema, jsonDataPath)
            checkValidateResult(result)
        }

        it("should validate that response complies with the custom JSON schema") {
            val jsonDataPath = "/requests/patch-authorization-request-response-body.json"
            val result = validateJsonData(authReqReadSchema, jsonDataPath)
            checkValidateResult(result)
        }
    }

    describe("POST /authorization-requests") {
        val authReqCreateSchema = loadSchemaFromFile("/schemas/authorization-request-post-request.schema.json", true)
        val authReqReadSchema = loadSchemaFromFile("/schemas/authorization-request-response.schema.json", true)

        it("should validate that request complies with the custom JSON schema") {
            val jsonDataPath = "/requests/post-authorization-request-request-body.json"
            val result = validateJsonData(authReqCreateSchema, jsonDataPath)
            checkValidateResult(result)
        }

        it("should validate that response complies with the custom JSON schema") {
            val jsonDataPath = "/requests/post-authorization-request-response-body.json"
            val result = validateJsonData(authReqReadSchema, jsonDataPath)
            checkValidateResult(result)
        }
    }

    describe("GET /authorization-requests/{id}") {
        val authReqReadSchema = loadSchemaFromFile("/schemas/authorization-request-response.schema.json")

        it("should validate that response complies with the custom JSON schema") {
            val jsonDataPath = "/requests/get-authorization-request-id.json"
            val result = validateJsonData(authReqReadSchema, jsonDataPath)
            checkValidateResult(result)
        }
    }
})

/**
 * Some of our JSON schemas reference other schemas, and this method handles resolving those references by using the json-sKema library
 * This library does not automatically resolve external reference. More information here:
 * https://github.com/erosb/json-sKema/blob/master/README.md#pre-registering-schemas-by-uri-before-schema-loading
 */
fun loadSchemaFromFile(schemaPath: String, hasReference: Boolean? = false): Schema {
    val schemaUrl = object {}.javaClass.getResource(schemaPath)
        ?: throw IllegalStateException("Schema file not found: $schemaPath")

    if (hasReference == true) {
        val authorizationRequestSchema = Files.readString(Paths.get("src/main/resources/schemas/authorization-request.schema.json"))
        val baseDefinitionSchema = Files.readString(Paths.get("src/main/resources/schemas/base-definitions.schema.json"))

        val schemaLoaderConfig = SchemaLoaderConfig.createDefaultConfig(
            mapOf(
                // pre-register these references with local file paths to ensure proper resolution during schema validation
                URI("authorization-request.schema.json") to authorizationRequestSchema,
                URI("base-definitions.schema.json") to baseDefinitionSchema
            )
        )

        val schemaContent = Files.readString(Paths.get(schemaUrl.toURI()))
        val parsedSchema = JsonParser(schemaContent).parse()

        return SchemaLoader(parsedSchema, schemaLoaderConfig).load()
    } else {
        return SchemaLoader
            .forURL(schemaUrl.toURI().toString())
            .load()
    }
}

fun validateJsonData(schema: Schema, jsonDataFilePath: String): ValidationFailure? {
    val validator = Validator.forSchema(schema)
    val valid = Files.readString(Paths.get("src/test/resources$jsonDataFilePath"))

    val instance = JsonParser(valid).parse()
    return validator.validate(instance)
}

fun checkValidateResult(result: ValidationFailure?) {
    // validate will return null when there are no errors to the json data
    result.shouldBeNull()
}
