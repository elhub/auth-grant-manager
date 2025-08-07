package no.elhub.auth.domain.document

interface SigningService {
    fun addPadesSignature(pdfByteArray: ByteArray): ByteArray
}
