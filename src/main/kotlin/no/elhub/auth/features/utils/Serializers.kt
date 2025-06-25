package no.elhub.auth.features.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

object Serializers {
    object InstantSerializer : KSerializer<Instant> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: Instant) {
            encoder.encodeString(value.toString())
        }
        override fun deserialize(decoder: Decoder): Instant =
            Instant.parse(decoder.decodeString())
    }
}
