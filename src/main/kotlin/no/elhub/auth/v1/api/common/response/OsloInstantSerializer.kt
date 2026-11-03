package no.elhub.auth.v1.api.common.response

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.elhub.auth.v1.common.toOsloOffsetString
import kotlin.time.Instant

object OsloInstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("OsloInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toOsloOffsetString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
