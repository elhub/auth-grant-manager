package no.elhub.auth.features.businessprocesses.movein

import kotlinx.serialization.Serializable
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeValidationError

@Serializable
sealed class MoveInValidationError(
    override val code: String,
    override val message: String,
) : RequestTypeValidationError {
    @Serializable
    data object MissingRequestedFromName :
        MoveInValidationError("missing_requested_from_name", "Requested from name is missing")

    @Serializable
    data object MissingBalanceSupplierName :
        MoveInValidationError("missing_balance_supplier_name", "Balance supplier name is missing")

    @Serializable
    data object MissingBalanceSupplierContractName :
        MoveInValidationError("missing_balance_supplier_contract_name", "Balance supplier contract name is missing")

    @Serializable
    data object MissingStartDate :
        MoveInValidationError("missing_start_date", "Start date is missing")

    @Serializable
    data object MissingMeteringPointId :
        MoveInValidationError("missing_metering_point_id", "Metering point id is missing")

    @Serializable
    data object InvalidMeteringPointId :
        MoveInValidationError("invalid_metering_point_id", "Metering point id is invalid")

    @Serializable
    data object MissingMeteringPointAddress :
        MoveInValidationError("missing_metering_point_address", "Metering point address is missing")

    @Serializable
    data object MissingRequestedBy :
        MoveInValidationError("missing_requested_by", "Requested by is missing")

    @Serializable
    data object InvalidRequestedBy :
        MoveInValidationError("invalid_requested_by", "Requested by has invalid format")

    @Serializable
    data object MissingRequestedFrom :
        MoveInValidationError("missing_requested_from", "Requested from is missing")

    @Serializable
    data object InvalidRequestedFrom :
        MoveInValidationError("invalid_requested_from", "Requested from has invalid format")
}
