package no.elhub.auth.features.businessprocesses.changeofenergysupplier

import kotlinx.serialization.Serializable
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeValidationError

fun ChangeOfBalanceSupplierValidationError.toBusinessError(): BusinessProcessError =
    when (this) {
        is ChangeOfBalanceSupplierValidationError.UnexpectedError -> BusinessProcessError.Unexpected(detail = this.message)

        ChangeOfBalanceSupplierValidationError.MeteringPointNotFound,
        ChangeOfBalanceSupplierValidationError.RequestedByNotFound,
        ChangeOfBalanceSupplierValidationError.RequestedFromNotFound -> BusinessProcessError.NotFound(detail = this.message)

        else -> BusinessProcessError.Validation(detail = this.message)
    }

@Serializable
sealed class ChangeOfBalanceSupplierValidationError(
    override val code: String,
    override val message: String,
) : RequestTypeValidationError {
    @Serializable
    data object MissingRequestedFromName :
        ChangeOfBalanceSupplierValidationError("missing_requested_from_name", "Requested from name is missing")

    @Serializable
    data object MissingBalanceSupplierName :
        ChangeOfBalanceSupplierValidationError("missing_balance_supplier_name", "Balance supplier name is missing")

    @Serializable
    data object MissingBalanceSupplierContractName :
        ChangeOfBalanceSupplierValidationError("missing_balance_supplier_contract_name", "Balance supplier contract name is missing")

    @Serializable
    data object MissingMeteringPointId :
        ChangeOfBalanceSupplierValidationError("missing_metering_point_id", "Metering point id is missing")

    @Serializable
    data object InvalidMeteringPointId :
        ChangeOfBalanceSupplierValidationError("invalid_metering_point_id", "Metering point id is invalid")

    @Serializable
    data object MeteringPointNotFound :
        ChangeOfBalanceSupplierValidationError("metering_point_not_found", "Metering point not found")

    @Serializable
    data object MeteringPointBlockedForSwitching :
        ChangeOfBalanceSupplierValidationError("metering_point_blocked_for_switching", "Metering point is blocked for switching balance supplier")

    @Serializable
    data object MissingMeteringPointAddress :
        ChangeOfBalanceSupplierValidationError("missing_metering_point_address", "Metering point address is missing")

    @Serializable
    data object MissingRequestedBy :
        ChangeOfBalanceSupplierValidationError("missing_requested_by", "Requested by is missing")

    @Serializable
    data object InvalidRequestedBy :
        ChangeOfBalanceSupplierValidationError("invalid_requested_by", "Requested by has invalid format")

    @Serializable
    data object RequestedByNotFound :
        ChangeOfBalanceSupplierValidationError("requested_by_not_found", "Requested by not found")

    @Serializable
    data object NotActiveRequestedBy :
        ChangeOfBalanceSupplierValidationError("not_active_requested_by", "Requested by is not an active party in Elhub")

    @Serializable
    data object MatchingRequestedBy :
        ChangeOfBalanceSupplierValidationError("matching_requested_by", "Requested by matches the current balance supplier of the metering point")

    @Serializable
    data object MissingRequestedFrom :
        ChangeOfBalanceSupplierValidationError("missing_requested_from", "Requested from is missing")

    @Serializable
    data object InvalidRequestedFrom :
        ChangeOfBalanceSupplierValidationError("invalid_requested_from", "Requested from has invalid format")

    @Serializable
    data object RequestedFromNotFound :
        ChangeOfBalanceSupplierValidationError("requested_from_not_found", "Requested from id not found")

    @Serializable
    data object RequestedFromNotMeteringPointEndUser :
        ChangeOfBalanceSupplierValidationError(
            "requested_from_not_metering_point_end_user",
            "Requested from is not registered as end user of the metering point"
        )

    @Serializable
    data object InvalidRedirectURI :
        ChangeOfBalanceSupplierValidationError("invalid_redirect_uri", "Redirect URI has invalid format")

    @Serializable
    data object RequestedToRequestedFromMismatch :
        ChangeOfBalanceSupplierValidationError("requested_to_requested_from_mismatch", "Requested to and requested from are not the same party")

    @Serializable
    data object ContractsNotFound :
        ChangeOfBalanceSupplierValidationError("contracts_not_found", "Contracts not found in strømpris.no for provided organization number")

    @Serializable
    data object InvalidBalanceSupplierContractName :
        ChangeOfBalanceSupplierValidationError("invalid_balance_supplier_contract_name", "Balance supplier contract name has no matches in strømpris.no")

    @Serializable
    data object UnexpectedError :
        ChangeOfBalanceSupplierValidationError("unexpected_error", "Unexpected error occurred")
}
