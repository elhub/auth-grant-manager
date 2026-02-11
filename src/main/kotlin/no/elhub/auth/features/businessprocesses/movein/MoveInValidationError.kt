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
    data object StartDateNotBackInTime :
        MoveInValidationError("start_date_not_back_in_time", "Start date must be today or back in time")

    @Serializable
    data object MissingMeteringPointId :
        MoveInValidationError("missing_metering_point_id", "Metering point id is missing")

    @Serializable
    data object InvalidMeteringPointId :
        MoveInValidationError("invalid_metering_point_id", "Metering point id is invalid")

    @Serializable
    data object MeteringPointNotFound :
        MoveInValidationError("metering_point_not_found", "Metering point not found")

    @Serializable
    data object RequestedFromIsMeteringPointEndUser :
        MoveInValidationError("requested_from_is_metering_point_end_user", "Requested from is already registered as end user of the metering point")

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

    @Serializable
    data object RequestedFromNotFound :
        MoveInValidationError("requested_from_not_found", "Requested from id not found")

    @Serializable
    data object RequestedByNotFound :
        MoveInValidationError("requested_by_not_found", "Requested by not found")

    @Serializable
    data object NotActiveRequestedBy :
        MoveInValidationError("not_active_requested_by", "Requested by is not an active party in Elhub")
}
