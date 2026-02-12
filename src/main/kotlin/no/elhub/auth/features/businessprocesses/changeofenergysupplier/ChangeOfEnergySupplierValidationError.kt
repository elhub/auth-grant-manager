package no.elhub.auth.features.businessprocesses.changeofenergysupplier

import kotlinx.serialization.Serializable
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeValidationError

fun ChangeOfEnergySupplierValidationError.toBusinessError(): BusinessProcessError =
    when (this) {
        is ChangeOfEnergySupplierValidationError.UnexpectedError -> BusinessProcessError.Unexpected(detail = this.message)
        ChangeOfEnergySupplierValidationError.MeteringPointNotFound -> BusinessProcessError.NotFound(detail = this.message)
        else -> BusinessProcessError.Validation(detail = this.message)
    }

@Serializable
sealed class ChangeOfEnergySupplierValidationError(
    override val code: String,
    override val message: String,
) : RequestTypeValidationError {
    @Serializable
    data object MissingRequestedFromName :
        ChangeOfEnergySupplierValidationError("missing_requested_from_name", "Requested from name is missing")

    @Serializable
    data object MissingBalanceSupplierName :
        ChangeOfEnergySupplierValidationError("missing_balance_supplier_name", "Balance supplier name is missing")

    @Serializable
    data object MissingBalanceSupplierContractName :
        ChangeOfEnergySupplierValidationError("missing_balance_supplier_contract_name", "Balance supplier contract name is missing")

    @Serializable
    data object MissingMeteringPointId :
        ChangeOfEnergySupplierValidationError("missing_metering_point_id", "Metering point id is missing")

    @Serializable
    data object InvalidMeteringPointId :
        ChangeOfEnergySupplierValidationError("invalid_metering_point_id", "Metering point id is invalid")

    @Serializable
    data object MeteringPointNotFound :
        ChangeOfEnergySupplierValidationError("metering_point_not_found", "Metering point not found")

    @Serializable
    data object MeteringPointBlockedForSwitching :
        ChangeOfEnergySupplierValidationError("metering_point_blocked_for_switching", "Metering point is blocked for switching energy supplier")

    @Serializable
    data object MissingMeteringPointAddress :
        ChangeOfEnergySupplierValidationError("missing_metering_point_address", "Metering point address is missing")

    @Serializable
    data object MissingRequestedBy :
        ChangeOfEnergySupplierValidationError("missing_requested_by", "Requested by is missing")

    @Serializable
    data object InvalidRequestedBy :
        ChangeOfEnergySupplierValidationError("invalid_requested_by", "Requested by has invalid format")

    @Serializable
    data object RequestedByNotFound :
        ChangeOfEnergySupplierValidationError("requested_by_not_found", "Requested by not found")

    @Serializable
    data object NotActiveRequestedBy :
        ChangeOfEnergySupplierValidationError("not_active_requested_by", "Requested by is not an active party in Elhub")

    @Serializable
    data object MatchingRequestedBy :
        ChangeOfEnergySupplierValidationError("matching_requested_by", "Requested by matches the current balance supplier of the metering point")

    @Serializable
    data object MissingRequestedFrom :
        ChangeOfEnergySupplierValidationError("missing_requested_from", "Requested from is missing")

    @Serializable
    data object InvalidRequestedFrom :
        ChangeOfEnergySupplierValidationError("invalid_requested_from", "Requested from has invalid format")

    @Serializable
    data object RequestedFromNotFound :
        ChangeOfEnergySupplierValidationError("requested_from_not_found", "Requested from id not found")

    @Serializable
    data object RequestedFromNotMeteringPointEndUser :
        ChangeOfEnergySupplierValidationError(
            "requested_from_not_metering_point_end_user",
            "Requested from is not registered as end user of the metering point"
        )

    @Serializable
    data object InvalidRedirectURI :
        ChangeOfEnergySupplierValidationError("invalid_redirect_uri", "Redirect URI has invalid format")

    @Serializable
    data object RequestedToRequestedFromMismatch :
        ChangeOfEnergySupplierValidationError("requested_to_requested_from_mismatch", "Requested to and requested from are not the same party")

    @Serializable
    data object UnexpectedError :
        ChangeOfEnergySupplierValidationError("unexpected_error", "Unexpected error occurred")
}
