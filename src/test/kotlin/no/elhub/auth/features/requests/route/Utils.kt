package no.elhub.auth.features.requests.route

import org.jetbrains.exposed.v1.jdbc.batchInsert
import no.elhub.auth.features.requests.common.AuthorizationRequestPropertyTable
import org.jetbrains.exposed.v1.jdbc.insert
import no.elhub.auth.features.requests.common.AuthorizationRequestTable
import java.util.UUID
import java.time.ZoneOffset
import java.time.OffsetDateTime
import no.elhub.auth.features.requests.common.DatabaseRequestStatus
import no.elhub.auth.features.grants.AuthorizationScope
import no.elhub.auth.features.common.toTimeZoneOffsetDateTimeAtStartOfDay
import no.elhub.auth.features.common.CreateScopeData
import arrow.core.left
import arrow.core.right

import org.jetbrains.exposed.v1.jdbc.transactions.transaction

import no.elhub.auth.features.requests.create.model.defaultRequestValidTo
import no.elhub.auth.features.grants.common.CreateGrantProperties

import no.elhub.auth.features.requests.create.command.RequestCommand

import no.elhub.auth.features.requests.AuthorizationRequest

import no.elhub.auth.features.requests.create.command.RequestMetaMarker

import no.elhub.auth.features.requests.create.requesttypes.RequestTypeValidationError

import no.elhub.auth.features.requests.create.model.CreateRequestModel

import io.ktor.server.application.Application
import org.koin.ktor.plugin.koinModule
import no.elhub.auth.features.requests.create.RequestBusinessHandler

import no.elhub.auth.features.common.AuthPersonsTestContainer
import no.elhub.auth.features.common.commonModule
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import no.elhub.auth.features.requests.module
import no.elhub.auth.module as applicationModule
import io.ktor.server.config.MapApplicationConfig

const val REQUESTED_FROM_NIN = "02916297702"
const val REQUESTED_TO_NIN = "14810797496"

fun ApplicationTestBuilder.setUpAuthorizationRequestTestApplication() {
    client = createClient {
        install(ContentNegotiation) {
            json()
        }
    }

    application {
        applicationModule()
        testRequestBusinessModule()
        commonModule()
        module()
    }

    environment {
        config =
            MapApplicationConfig(
                "ktor.database.username" to "app",
                "ktor.database.password" to "app",
                "ktor.database.url" to "jdbc:postgresql://localhost:5432/auth",
                "ktor.database.driverClass" to "org.postgresql.Driver",
                "featureToggle.enableEndpoints" to "true",
                "authPersons.baseUri" to AuthPersonsTestContainer.baseUri(),
                "pdp.baseUrl" to "http://localhost:8085"
            )
    }
}

fun Application.testRequestBusinessModule() {
    koinModule {
        single<RequestBusinessHandler> { TestRequestBusinessHandler() }
    }
}

class TestRequestBusinessHandler : RequestBusinessHandler {
    override suspend fun validateAndReturnRequestCommand(createRequestModel: CreateRequestModel) =
        when (createRequestModel.requestType) {
            AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson -> {
                val meta = createRequestModel.meta
                if (meta.requestedFromName.isBlank()) {
                    TestRequestValidationError.MissingRequestedFromName.left()
                } else {
                    RequestCommand(
                        type = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson,
                        requestedFrom = meta.requestedFrom,
                        requestedBy = meta.requestedBy,
                        requestedTo = meta.requestedTo,
                        validTo = defaultRequestValidTo().toTimeZoneOffsetDateTimeAtStartOfDay(),
                        scopes = listOf(
                            CreateScopeData(
                                authorizedResourceType = AuthorizationScope.AuthorizationResource.MeteringPoint,
                                authorizedResourceId = meta.requestedForMeteringPointId,
                                permissionType = AuthorizationScope.PermissionType.ChangeOfEnergySupplierForPerson
                            )
                        ),
                        meta = TestRequestMeta(
                            requestedFromName = meta.requestedFromName,
                            requestedForMeteringPointId = meta.requestedForMeteringPointId,
                            requestedForMeteringPointAddress = meta.requestedForMeteringPointAddress,
                            balanceSupplierName = meta.balanceSupplierName,
                            balanceSupplierContractName = meta.balanceSupplierContractName,
                            redirectURI = meta.redirectURI,
                        ),
                    ).right()
                }
            }

            else -> TestRequestValidationError.UnsupportedRequestType.left()
        }

    override fun getCreateGrantProperties(request: AuthorizationRequest): CreateGrantProperties =
        CreateGrantProperties(
            validFrom = no.elhub.auth.features.requests.create.model.today(),
            validTo = defaultRequestValidTo(),
        )
}


fun insertAuthorizationRequest(
    status: DatabaseRequestStatus = DatabaseRequestStatus.Pending,
    validToDate: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC).plusDays(10),
    properties: Map<String, String> = emptyMap()
): UUID {
    val requestId = UUID.randomUUID()
    val requestedById = UUID.fromString("22222222-2222-2222-2222-222222222222")
    val requestedFromId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    val requestedToId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    transaction {
        AuthorizationRequestTable.insert {
            it[id] = requestId
            it[requestType] = AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson
            it[requestStatus] = status
            it[requestedBy] = requestedById
            it[requestedFrom] = requestedFromId
            it[requestedTo] = requestedToId
            it[approvedBy] = null
            it[validTo] = validToDate
        }

        if (properties.isNotEmpty()) {
            AuthorizationRequestPropertyTable.batchInsert(properties.entries) { (key, value) ->
                this[AuthorizationRequestPropertyTable.requestId] = requestId
                this[AuthorizationRequestPropertyTable.key] = key
                this[AuthorizationRequestPropertyTable.value] = value
            }
        }
    }

    return requestId
}

sealed class TestRequestValidationError : RequestTypeValidationError {
    data object MissingRequestedFromName : TestRequestValidationError() {
        override val code: String = "missing_requested_from_name"
        override val message: String = "Requested from name is missing"
    }

    data object UnsupportedRequestType : TestRequestValidationError() {
        override val code: String = "unsupported_request_type"
        override val message: String = "Unsupported request type"
    }
}
data class TestRequestMeta(
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val redirectURI: String,
) : RequestMetaMarker {
    override fun toMetaAttributes(): Map<String, String> =
        mapOf(
            "requestedFromName" to requestedFromName,
            "requestedForMeteringPointId" to requestedForMeteringPointId,
            "requestedForMeteringPointAddress" to requestedForMeteringPointAddress,
            "balanceSupplierName" to balanceSupplierName,
            "balanceSupplierContractName" to balanceSupplierContractName,
            "redirectURI" to redirectURI,
        )
}

