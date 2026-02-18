package no.elhub.auth.features.businessprocesses.moveinandchangeofenergysupplier

import kotlinx.serialization.Serializable
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeValidationError

fun MoveInAndChangeOfEnergySupplierValidationError.toBusinessError(): BusinessProcessError =
    when (this) {
        is MoveInAndChangeOfEnergySupplierValidationError.UnexpectedError -> BusinessProcessError.Unexpected(detail = this.message)

        MoveInAndChangeOfEnergySupplierValidationError.MeteringPointNotFound,
        MoveInAndChangeOfEnergySupplierValidationError.RequestedByNotFound,
        MoveInAndChangeOfEnergySupplierValidationError.RequestedFromNotFound -> BusinessProcessError.NotFound(detail = this.message)

        else -> BusinessProcessError.Validation(detail = this.message)
    }

@Serializable
sealed class MoveInAndChangeOfEnergySupplierValidationError(
    override val code: String,
    override val message: String,
) : RequestTypeValidationError {
    @Serializable
    data object MissingRequestedFromName :
        MoveInAndChangeOfEnergySupplierValidationError("missing_requested_from_name", "Requested from name is missing")

    @Serializable
    data object MissingBalanceSupplierName :
        MoveInAndChangeOfEnergySupplierValidationError("missing_balance_supplier_name", "Balance supplier name is missing")

    @Serializable
    data object MissingBalanceSupplierContractName :
        MoveInAndChangeOfEnergySupplierValidationError("missing_balance_supplier_contract_name", "Balance supplier contract name is missing")

    @Serializable
    data object StartDateNotBackInTime :
        MoveInAndChangeOfEnergySupplierValidationError("start_date_not_back_in_time", "Start date must be today or back in time")

    @Serializable
    data object MissingMeteringPointId :
        MoveInAndChangeOfEnergySupplierValidationError("missing_metering_point_id", "Metering point id is missing")

    @Serializable
    data object InvalidMeteringPointId :
        MoveInAndChangeOfEnergySupplierValidationError("invalid_metering_point_id", "Metering point id is invalid")

    @Serializable
    data object MeteringPointNotFound :
        MoveInAndChangeOfEnergySupplierValidationError("metering_point_not_found", "Metering point not found")

    @Serializable
    data object RequestedFromIsMeteringPointEndUser :
        MoveInAndChangeOfEnergySupplierValidationError(
            "requested_from_is_metering_point_end_user",
            "Requested from is already registered as end user of the metering point"
        )

    @Serializable
    data object MissingMeteringPointAddress :
        MoveInAndChangeOfEnergySupplierValidationError("missing_metering_point_address", "Metering point address is missing")

    @Serializable
    data object MissingRequestedBy :
        MoveInAndChangeOfEnergySupplierValidationError("missing_requested_by", "Requested by is missing")

    @Serializable
    data object InvalidRequestedBy :
        MoveInAndChangeOfEnergySupplierValidationError("invalid_requested_by", "Requested by has invalid format")

    @Serializable
    data object MissingRequestedFrom :
        MoveInAndChangeOfEnergySupplierValidationError("missing_requested_from", "Requested from is missing")

    @Serializable
    data object InvalidRequestedFrom :
        MoveInAndChangeOfEnergySupplierValidationError("invalid_requested_from", "Requested from has invalid format")

    @Serializable
    data object RequestedFromNotFound :
        MoveInAndChangeOfEnergySupplierValidationError("requested_from_not_found", "Requested from id not found")

    @Serializable
    data object RequestedToRequestedFromMismatch :
        MoveInAndChangeOfEnergySupplierValidationError("requested_to_requested_from_mismatch", "Requested to and requested from are not the same party")

    @Serializable
    data object RequestedByNotFound :
        MoveInAndChangeOfEnergySupplierValidationError("requested_by_not_found", "Requested by not found")

    @Serializable
    data object NotActiveRequestedBy :
        MoveInAndChangeOfEnergySupplierValidationError("not_active_requested_by", "Requested by is not an active party in Elhub")

    @Serializable
    data object ContractsNotFound :
        MoveInAndChangeOfEnergySupplierValidationError("contracts_not_found", "Contracts not found in strømpris.no for provided organization number")

    @Serializable
    data object InvalidBalanceSupplierContractName :
        MoveInAndChangeOfEnergySupplierValidationError(
            "invalid_balance_supplier_contract_name",
            "Balance supplier contract name has no matches in strømpris.no"
        )

    @Serializable
    data object UnexpectedError :
        MoveInAndChangeOfEnergySupplierValidationError("unexpected_error", "Unexpected error occurred")
}
