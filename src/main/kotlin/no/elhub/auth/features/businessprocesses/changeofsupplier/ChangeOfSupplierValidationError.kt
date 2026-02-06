package no.elhub.auth.features.businessprocesses.changeofsupplier

import kotlinx.serialization.Serializable
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeValidationError

@Serializable
sealed class ChangeOfSupplierValidationError(
    override val code: String,
    override val message: String,
) : RequestTypeValidationError {
    @Serializable
    data object MissingRequestedFromName :
        ChangeOfSupplierValidationError("missing_requested_from_name", "Requested from name is missing")

    @Serializable
    data object MissingBalanceSupplierName :
        ChangeOfSupplierValidationError("missing_balance_supplier_name", "Balance supplier name is missing")

    @Serializable
    data object MissingBalanceSupplierContractName :
        ChangeOfSupplierValidationError("missing_balance_supplier_contract_name", "Balance supplier contract name is missing")

    @Serializable
    data object MissingMeteringPointId :
        ChangeOfSupplierValidationError("missing_metering_point_id", "Metering point id is missing")

    @Serializable
    data object InvalidMeteringPointId :
        ChangeOfSupplierValidationError("invalid_metering_point_id", "Metering point id is invalid")

    @Serializable
    data object MeteringPointNotFound :
        ChangeOfSupplierValidationError("metering_point_not_found", "Metering point not found")

    @Serializable
    data object MissingMeteringPointAddress :
        ChangeOfSupplierValidationError("missing_metering_point_address", "Metering point address is missing")

    @Serializable
    data object MissingRequestedBy :
        ChangeOfSupplierValidationError("missing_requested_by", "Requested by is missing")

    @Serializable
    data object InvalidRequestedBy :
        ChangeOfSupplierValidationError("invalid_requested_by", "Requested by has invalid format")

    @Serializable
    data object MissingRequestedFrom :
        ChangeOfSupplierValidationError("missing_requested_from", "Requested from is missing")

    @Serializable
    data object InvalidRequestedFrom :
        ChangeOfSupplierValidationError("invalid_requested_from", "Requested from has invalid format")

    @Serializable
    data object RequestedFromNotFound :
        ChangeOfSupplierValidationError("requested_to_not_found", "Requested to id not found")

    @Serializable
    data object RequestedFromNotMeteringPointEndUser :
        ChangeOfSupplierValidationError("requested_from_not_metering_point_end_user", "Requested from is not registered as end user of the metering point")

    @Serializable
    data object InvalidRedirectURI :
        ChangeOfSupplierValidationError("invalid_redirect_uri", "Redirect URI has invalid format")
}
