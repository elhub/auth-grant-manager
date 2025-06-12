package no.elhub.auth.features.documents

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import no.elhub.auth.extensions.PostgresTestContainerExtension
import no.elhub.auth.model.AuthorizationDocument
import no.elhub.auth.model.AuthorizationDocumentScopes
import no.elhub.auth.model.AuthorizationScopes
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipData
import no.elhub.devxp.jsonapi.model.JsonApiRelationshipToOne
import no.elhub.devxp.jsonapi.request.JsonApiRequestResourceObjectWithRelationships
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class AuthorizationDocumentRepositoryTest :
    DescribeSpec({
        extensions(PostgresTestContainerExtension)
        val repository = AuthorizationDocumentRepository

        beforeTest {
            Database.connect(
                url = "jdbc:postgresql://localhost:5432/auth",
                driver = "org.postgresql.Driver",
                user = "postgres",
                password = "postgres",
            )
        }

        describe("Insert Document") {
            it("should insert a document and its scopes with correct references") {
                // Given
                val document =
                    AuthorizationDocument.of(
                        PostAuthorizationDocumentRequest(
                            data = JsonApiRequestResourceObjectWithRelationships(
                                type = "authorization_document",
                                attributes = DocumentRequestAttributes(meteringPoint = "1234"),
                                relationships = DocumentRelationships(
                                    requestedBy = JsonApiRelationshipToOne(
                                        data = JsonApiRelationshipData(type = "User", id = "12345678901")
                                    ),
                                    requestedTo = JsonApiRelationshipToOne(
                                        data = JsonApiRelationshipData(type = "User", id = "12345678901")
                                    )
                                )
                            )
                        ),
                        byteArrayOf(),
                    )

                // When
                repository.insertDocument(document)

                // Then
                val documentExists = repository.getDocumentFile(document.id)
                documentExists shouldNotBe null

                val authorizationDocumentScopeRow =
                    transaction {
                        AuthorizationDocumentScopes
                            .selectAll()
                            .where { AuthorizationDocumentScopes.authorizationDocumentId eq document.id }
                            .map { it }
                            .singleOrNull()
                    }
                authorizationDocumentScopeRow shouldNotBe null

                val authorizationScopeRow =
                    transaction {
                        AuthorizationScopes
                            .selectAll()
                            .where { AuthorizationScopes.id eq (authorizationDocumentScopeRow!![AuthorizationDocumentScopes.authorizationScopeId]) }
                            .singleOrNull()
                    }
                authorizationScopeRow shouldNotBe null
            }
        }
    })
