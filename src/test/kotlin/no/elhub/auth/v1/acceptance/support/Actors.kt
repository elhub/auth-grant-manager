package no.elhub.auth.v1.acceptance.support

/**
 * Test-local descriptions of the parties in a scenario. Plain data: declaring an actor registers nothing.
 * Fields only needed by some stubs are optional; a stub that needs a missing field fails fast.
 */
data class EnergySupplier(
    val organizationNumber: String,
    val gln: String,
    val name: String? = null,
    val redirectUrl: String? = null,
)

data class Organization(
    val number: String,
    val name: String? = null,
)

data class Person(
    val id: String,
    val nationalIdentityNumber: String? = null,
)

val exampleSupplier = EnergySupplier("999888770", "3004300000019", "Example Energy Supplier")
val customerOrganization = Organization("999888771", "Example Customer AS")
val exampleApprover = Person("d6784082-8344-e733-e053-02058d0a6752")

/** The API caller for this supplier: its Maskinporten token and sender GLN. */
suspend fun EnergySupplier.caller() = Caller.MarketEntity(
    organizationNumber = organizationNumber,
    senderGln = gln,
    token = AcceptanceTestInfrastructure.testTokens.marketEntity(organizationNumber),
)

/** The API caller for this person, optionally acting on behalf of [onBehalfOf]. */
suspend fun Person.caller(onBehalfOf: Organization? = null) = Caller.EndUser(
    personId = id,
    token = AcceptanceTestInfrastructure.testTokens.endUser(id),
    onBehalfOfOrganization = onBehalfOf?.number,
)
