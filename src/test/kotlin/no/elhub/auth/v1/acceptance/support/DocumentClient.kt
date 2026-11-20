package no.elhub.auth.v1.acceptance.support

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import no.elhub.auth.v0.features.documents.EndUserSignatureTestHelper
import no.elhub.auth.v1.V1_DOCUMENTS_PATH
import no.elhub.auth.v1.V1_GRANTS_PATH

/** HTTP operations on AGM, without preparing external data or PDP decisions. */
class DocumentClient(private val api: Api) {
    suspend fun create(payload: String, by: Caller) =
        api.call(HttpMethod.Post, V1_DOCUMENTS_PATH, by, body = payload)

    suspend fun createPending(payload: String, by: Caller): String =
        create(payload, by).shouldHaveStatus(HttpStatusCode.Created).field("data.id")!!

    suspend fun get(id: String, by: Caller) =
        api.call(HttpMethod.Get, "$V1_DOCUMENTS_PATH/$id", by)

    suspend fun list(by: Caller) = api.call(HttpMethod.Get, V1_DOCUMENTS_PATH, by)

    suspend fun downloadPdf(id: String, by: Caller): ByteArray =
        api.call(HttpMethod.Get, "$V1_DOCUMENTS_PATH/$id.pdf", by).shouldHaveStatus(HttpStatusCode.OK).bodyBytes

    suspend fun uploadSignedPdf(id: String, signedPdf: ByteArray, by: Caller) =
        api.call(HttpMethod.Put, "$V1_DOCUMENTS_PATH/$id.pdf", by, pdf = signedPdf)

    suspend fun getGrant(id: String, by: Caller) =
        api.call(HttpMethod.Get, "$V1_GRANTS_PATH/$id", by)

    fun signWithBankId(pdf: ByteArray, signer: Person): ByteArray =
        EndUserSignatureTestHelper().sign(pdf, requireNotNull(signer.nationalIdentityNumber) { "signer needs a nationalIdentityNumber" })
}
