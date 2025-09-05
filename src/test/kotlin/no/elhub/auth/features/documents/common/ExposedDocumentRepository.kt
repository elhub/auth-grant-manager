package no.elhub.auth.features.documents.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import no.elhub.auth.features.common.PostgresTestContainerExtension
import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.grants.common.AuthorizationScopeTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*

class ExposedDocumentRepositoryTests :
    FunSpec({
        extensions(PostgresTestContainerExtension)
        val repository = ExposedDocumentRepository()

        beforeSpec {
            Database.connect(
                url = "jdbc:postgresql://localhost:5432/auth",
                driver = "org.postgresql.Driver",
                user = "postgres",
                password = "postgres",
            )
        }

        context("Insert Document") {
            test("Should insert a document and its scopes with correct references") {
                // Given
                val document =
                    AuthorizationDocument(
                        id = UUID.randomUUID(),
                        title = "Title",
                        pdfBytes = byteArrayOf(),
                        type = AuthorizationDocument.Type.ChangeOfSupplierConfirmation,
                        status = AuthorizationDocument.Status.Pending,
                        requestedBy = "1234567890",
                        requestedTo = "987654321",
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now()
                    )

                // When
                repository.insert(document)

                // Then
                val documentExists = repository.find(document.id)
                documentExists shouldNotBe null

                val authorizationDocumentScopeRow =
                    transaction {
                        AuthorizationDocumentScopeTable
                            .selectAll()
                            .where { AuthorizationDocumentScopeTable.authorizationDocumentId eq document.id }
                            .map { it }
                            .singleOrNull()
                    }
                authorizationDocumentScopeRow shouldNotBe null

                val authorizationScopeRow =
                    transaction {
                        AuthorizationScopeTable
                            .selectAll()
                            .where { AuthorizationScopeTable.id eq (authorizationDocumentScopeRow!![AuthorizationDocumentScopeTable.authorizationScopeId]) }
                            .singleOrNull()
                    }
                authorizationScopeRow shouldNotBe null
            }
        }
    })
