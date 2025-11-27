package no.elhub.auth.features.requests.create.requesttypes.changeofsupplierconfirmation

import kotlinx.serialization.Serializable
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeValidationError

@Serializable
sealed class ChangeOfSupplierConfirmationValidationError(
    override val code: String,
    override val message: String,
) : RequestTypeValidationError() {
    @Serializable
    data object MissingRequestedFromName : ChangeOfSupplierConfirmationValidationError("missing_requested_from_name", "Requested from name is missing")

    @Serializable
    data object MissingBalanceSupplierConfirmationContractName : ChangeOfSupplierConfirmationValidationError(
        "missing_balance_supplier_contract_name",
        "Balance supplier contract name is missing",
    )

    @Serializable
    data object MissingMeteringPointId : ChangeOfSupplierConfirmationValidationError("missing_metering_point_id", "Metering point id is missing")

    @Serializable
    data object InvalidMeteringPointId : ChangeOfSupplierConfirmationValidationError("invalid_metering_point_id", "Metering point id is invalid")

    @Serializable
    data object MissingMeteringPointAddress : ChangeOfSupplierConfirmationValidationError("missing_metering_point_address", "Metering point address is missing")

    data object MissingRequestedBy : ChangeOfSupplierConfirmationValidationError("missing_requested_by", "Requested by is missing")

    data object InvalidRequestedBy : ChangeOfSupplierConfirmationValidationError("invalid_requested_by", "Requested by has invalid format")

    data object MissingRequestedFrom : ChangeOfSupplierConfirmationValidationError("missing_requested_from", "Requested from is missing")

    data object InvalidRequestedFrom : ChangeOfSupplierConfirmationValidationError("invalid_requested_from", "Requested from has invalid format")
}
