package no.elhub.auth.features.common.scope

import arrow.core.getOrElse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.elhub.auth.features.common.PostgresTestContainer
import no.elhub.auth.features.common.PostgresTestContainerExtension
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedScopeRepositoryTest : FunSpec({
    extension(PostgresTestContainerExtension())
    val scopeRepo = ExposedScopeRepository()

    beforeSpec {
        Database.connect(
            url = PostgresTestContainer.JDBC_URL,
            driver = PostgresTestContainer.DRIVER,
            user = PostgresTestContainer.USERNAME,
            password = PostgresTestContainer.PASSWORD,
        )
    }

    beforeTest {
        transaction {
            AuthorizationScopeTable.deleteAll()
        }
    }

    test("create new scope when not existing") {
        val scope = CreateAuthorizationScope(
            authorizedResourceType = ElhubResource.MeteringPoint,
            authorizedResourceId = "707057500400123456",
            permissionType = PermissionType.ChangeOfSupplier,
        )

        transaction {
            val scopeId = scopeRepo.findOrCreateScope(scope).getOrElse { error(it) }

            val found = AuthorizationScopeTable
                .selectAll()
                .where { AuthorizationScopeTable.id eq scopeId }
                .singleOrNull()

            found shouldNotBe null
            found!![AuthorizationScopeTable.authorizedResourceId] shouldBe scope.authorizedResourceId
        }
    }

    test("return existing scope (idempotent)") {
        val scope = CreateAuthorizationScope(
            authorizedResourceType = ElhubResource.MeteringPoint,
            authorizedResourceId = "707057500400123456",
            permissionType = PermissionType.ChangeOfSupplier,
        )

        transaction {
            val first = scopeRepo.findOrCreateScope(scope).getOrElse { error(it) }
            val second = scopeRepo.findOrCreateScope(scope).getOrElse { error(it) }

            first shouldBe second
        }
    }

    test("different resourceTypes create separate scopes") {
        val scope1 = CreateAuthorizationScope(
            authorizedResourceType = ElhubResource.MeteringPoint,
            authorizedResourceId = "1",
            permissionType = PermissionType.ChangeOfSupplier,
        )

        val scope2 = CreateAuthorizationScope(
            authorizedResourceType = ElhubResource.Organization,
            authorizedResourceId = "2",
            permissionType = PermissionType.ChangeOfSupplier,
        )

        transaction {
            val id1 = scopeRepo.findOrCreateScope(scope1).getOrElse { error(it) }
            val id2 = scopeRepo.findOrCreateScope(scope2).getOrElse { error(it) }

            println("nisse1: $id1")
            println("nisse2: $id2")

            id1 shouldNotBe id2
        }
    }

    test("multiple concurrent inserts of same scope yields single row") {
        val scope = CreateAuthorizationScope(
            authorizedResourceType = ElhubResource.MeteringPoint,
            authorizedResourceId = "707057500400123470",
            permissionType = PermissionType.ChangeOfSupplier,
        )

        repeat(10) {
            transaction {
                scopeRepo.findOrCreateScope(scope).getOrElse { error(it) }
            }
        }

        transaction {
            val count = AuthorizationScopeTable.selectAll().count()
            count shouldBe 1
        }
    }
})
