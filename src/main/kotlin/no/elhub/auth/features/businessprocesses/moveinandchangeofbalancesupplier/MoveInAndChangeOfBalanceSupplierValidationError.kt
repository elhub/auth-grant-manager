package no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier

import kotlinx.serialization.Serializable
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeValidationError

fun MoveInAndChangeOfBalanceSupplierValidationError.toBusinessError(): BusinessProcessError =
    when (this) {
        is MoveInAndChangeOfBalanceSupplierValidationError.UnexpectedError -> BusinessProcessError.Unexpected(
            detail = this.message
        )

        MoveInAndChangeOfBalanceSupplierValidationError.MeteringPointNotFound,
        MoveInAndChangeOfBalanceSupplierValidationError.RequestedByNotFound,
        MoveInAndChangeOfBalanceSupplierValidationError.RequestedFromNotFound -> BusinessProcessError.NotFound(
            detail = this.message
        )

        else -> BusinessProcessError.Validation(detail = this.message)
    }

@Serializable
sealed class MoveInAndChangeOfBalanceSupplierValidationError(
    override val code: String,
    override val message: String,
) : RequestTypeValidationError {
    @Serializable
    data object MissingRequestedFromName :
        MoveInAndChangeOfBalanceSupplierValidationError(
            "missing_requested_from_name",
            "Requested from name is missing"
        )

    @Serializable
    data object MissingBalanceSupplierName :
        MoveInAndChangeOfBalanceSupplierValidationError(
            "missing_balance_supplier_name",
            "Balance supplier name is missing"
        )

    @Serializable
    data object MissingBalanceSupplierContractName :
        MoveInAndChangeOfBalanceSupplierValidationError(
            "missing_balance_supplier_contract_name",
            "Balance supplier contract name is missing"
        )

    @Serializable
    data object MoveInDateNotBackInTime :
        MoveInAndChangeOfBalanceSupplierValidationError(
            "start_date_not_back_in_time",
            "Start date must be today or back in time"
        )

    @Serializable
    data object MissingMeteringPointId :
        MoveInAndChangeOfBalanceSupplierValidationError(
            "missing_metering_point_id",
            "Metering point id is missing"
        )

    @Serializable
    data object InvalidMeteringPointId :
        MoveInAndChangeOfBalanceSupplierValidationError(
            "invalid_metering_point_id",
            "Metering point id is invalid"
        )

    @Serializable
    data object MeteringPointNotFound :
        MoveInAndChangeOfBalanceSupplierValidationError("metering_point_not_found", "Metering point not found")

    @Serializable
    data object RequestedFromIsMeteringPointEndUser :
        MoveInAndChangeOfBalanceSupplierValidationError(
            "requested_from_is_metering_point_end_user",
            "Requested from is already registered as end user of the metering point"
        )

    @Serializable
    data object MissingMeteringPointAddress :
        MoveInAndChangeOfBalanceSupplierValidationError(
            "missing_metering_point_address",
            "Metering point address is missing"
        )

    @Serializable
    data object MissingRequestedBy :
        MoveInAndChangeOfBalanceSupplierValidationError("missing_requested_by", "Requested by is missing")

    @Serializable
    data object InvalidRequestedBy :
        MoveInAndChangeOfBalanceSupplierValidationError(
            "invalid_requested_by",
            "Requested by has invalid format"
        )

    @Serializable
    data object MissingRequestedFrom :
        MoveInAndChangeOfBalanceSupplierValidationError("missing_requested_from", "Requested from is missing")

    @Serializable
    data object InvalidRequestedFrom :
        MoveInAndChangeOfBalanceSupplierValidationError(
            "invalid_requested_from",
            "Requested from has invalid format"
        )

    @Serializable
    data object RequestedFromNotFound :
        MoveInAndChangeOfBalanceSupplierValidationError(
            "requested_from_not_found",
            "Requested from id not found"
        )

    @Serializable
    data object RequestedToRequestedFromMismatch :
        MoveInAndChangeOfBalanceSupplierValidationError(
            "requested_to_requested_from_mismatch",
            "Requested to and requested from are not the same party"
        )

    @Serializable
    data object RequestedByNotFound :
        MoveInAndChangeOfBalanceSupplierValidationError("requested_by_not_found", "Requested by not found")

    @Serializable
    data object NotActiveRequestedBy :
        MoveInAndChangeOfBalanceSupplierValidationError(
            "not_active_requested_by",
            "Requested by is not an active party in Elhub"
        )

    @Serializable
    data object ContractsNotFound :
        MoveInAndChangeOfBalanceSupplierValidationError(
            "contracts_not_found",
            "Contracts not found in strømpris.no for provided organization number"
        )

    @Serializable
    data object InvalidBalanceSupplierContractName :
        MoveInAndChangeOfBalanceSupplierValidationError(
            "invalid_balance_supplier_contract_name",
            "Balance supplier contract name has no matches in strømpris.no"
        )

    @Serializable
    data object UnexpectedError :
        MoveInAndChangeOfBalanceSupplierValidationError("unexpected_error", "Unexpected error occurred")
}
