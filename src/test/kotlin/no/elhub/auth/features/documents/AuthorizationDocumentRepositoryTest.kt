package no.elhub.auth.features.documents

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import no.elhub.auth.extensions.PostgresTestContainerExtension
import no.elhub.auth.model.AuthorizationDocument
import no.elhub.auth.model.AuthorizationDocumentScopes
import no.elhub.auth.model.AuthorizationScopes
import no.elhub.auth.model.RelationshipLink
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
                        PostAuthorizationDocument.Request(
                            data =
                                PostAuthorizationDocument.Request.Data(
                                    type = "authorization_document",
                                    attributes =
                                        PostAuthorizationDocument.Request.Data.Attributes(
                                            meteringPoint = "1234",
                                        ),
                                    relationships =
                                        PostAuthorizationDocument.Request.Data.Relationships(
                                            requestedBy = RelationshipLink(RelationshipLink.DataLink("12345678901", "User")),
                                            requestedTo = RelationshipLink(RelationshipLink.DataLink("98765432109", "User")),
                                        ),
                                ),
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
