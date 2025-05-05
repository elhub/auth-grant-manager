package no.elhub.auth.features.requests

import com.github.erosb.jsonsKema.JsonParser
import com.github.erosb.jsonsKema.SchemaLoader
import com.github.erosb.jsonsKema.Validator
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import no.elhub.auth.config.AUTHORIZATION_REQUEST
import java.nio.file.Files
import java.nio.file.Paths

class AuthorizationRequestValidationTest : DescribeSpec({

    describe("GET $AUTHORIZATION_REQUEST") {
        val schemaUrl = object {}.javaClass.getResource("/schemas/authorization-request-list.schema.json")
            ?: throw IllegalStateException("Schema file not found: authorization-request-list.schema.json")

        val schema = SchemaLoader
            .forURL(schemaUrl.toURI().toString())
            .load()

        it("should validate that a valid JSON document complies with the custom JSON schema") {
            val validator = Validator.forSchema(schema)
            val valid = Files.readString(Paths.get("src/test/resources/requests/valid/get-authorization-request-valid.json"))

            val instance = JsonParser(valid).parse()
            val result = validator.validate(instance)

            // validate will return null when there are no errors to the json data
            result.shouldBeNull()
        }

        it("should detect errors in an invalid JSON document that does not comply with the custom JSON schema") {
            val validator = Validator.forSchema(schema)

            // the invalid json file has lower case enums
            val valid = Files.readString(Paths.get("src/test/resources/requests/invalid/get-authorization-request-invalid.json"))

            val instance = JsonParser(valid).parse()
            val result = validator.validate(instance)

            // validate will not return null when there are errors to the json data
            result.shouldNotBeNull()
        }
    }

    describe("PATCH $AUTHORIZATION_REQUEST") {
        xit("should validate that a valid JSON document complies with the custom JSON schema") {
            // TODO the external reference $id does not work since https://api.elhub.no/schemas/authorization-request.json does not respond with json ??
            val schemaUrl = object {}.javaClass.getResource("/schemas/authorization-request-update.schema.json")
                ?: throw IllegalStateException("Schema file not found: authorization-request-update.schema.json")

            val schema = SchemaLoader
                .forURL(schemaUrl.toURI().toString())
                .load()

            val validator = Validator.forSchema(schema)
            val valid = Files.readString(Paths.get("src/test/resources/requests/valid/patch-authorization-request-valid-request-body.json"))

            val instance = JsonParser(valid).parse()
            val result = validator.validate(instance)

            // validate will return null when there are no errors to the json data
            result.shouldBeNull()
        }
    }

    describe("GET $AUTHORIZATION_REQUEST/{id}") {
        val schemaUrl = object {}.javaClass.getResource("/schemas/authorization-request-read.schema.json")
            ?: throw IllegalStateException("Schema file not found: authorization-request-read.schema.json")

        val schema = SchemaLoader
            .forURL(schemaUrl.toURI().toString())
            .load()

        it("should validate that a valid JSON document complies with the custom JSON schema") {
            val validator = Validator.forSchema(schema)
            val valid = Files.readString(Paths.get("src/test/resources/requests/valid/get-authorization-request-id-valid.json"))

            val instance = JsonParser(valid).parse()
            val result = validator.validate(instance)

            // validate will return null when there are no errors to the json data
            result.shouldBeNull()
        }

        it("should detect errors in an invalid JSON document that does not comply with the custom JSON schema") {
            val validator = Validator.forSchema(schema)

            // the invalid json file has lower case enums
            val valid = Files.readString(Paths.get("src/test/resources/requests/invalid/get-authorization-request-id-invalid.json"))

            val instance = JsonParser(valid).parse()
            val result = validator.validate(instance)

            // validate will not return null when there are errors to the json data
            result.shouldNotBeNull()
        }
    }
})
