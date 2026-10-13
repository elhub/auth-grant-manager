package no.elhub.auth.v1.domain

fun validMeteringPointId(value: String): MeteringPointId =
    (MeteringPointId.parse(value) as MeteringPointIdParseResult.Valid).id
