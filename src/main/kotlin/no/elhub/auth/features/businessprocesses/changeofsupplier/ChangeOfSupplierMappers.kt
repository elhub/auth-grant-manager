package no.elhub.auth.features.businessprocesses.changeofsupplier

import no.elhub.auth.features.documents.create.model.CreateDocumentModel
import no.elhub.auth.features.requests.create.model.CreateRequestModel

fun CreateRequestModel.toChangeOfSupplierBusinessModel(): ChangeOfSupplierBusinessModel =
    ChangeOfSupplierBusinessModel(
        requestedBy = this.meta.requestedBy,
        requestedFrom = this.meta.requestedFrom,
        requestedTo = this.meta.requestedTo,
        requestedFromName = this.meta.requestedFromName,
        requestedForMeteringPointId = this.meta.requestedForMeteringPointId,
        requestedForMeteringPointAddress = this.meta.requestedForMeteringPointAddress,
        balanceSupplierName = this.meta.balanceSupplierName,
        balanceSupplierContractName = this.meta.balanceSupplierContractName,
    )

fun CreateDocumentModel.toChangeOfSupplierBusinessModel(): ChangeOfSupplierBusinessModel =
    ChangeOfSupplierBusinessModel(
        requestedBy = this.meta.requestedBy,
        requestedFrom = this.meta.requestedFrom,
        requestedTo = this.meta.requestedTo,
        requestedFromName = this.meta.requestedFromName,
        requestedForMeteringPointId = this.meta.requestedForMeteringPointId,
        requestedForMeteringPointAddress = this.meta.requestedForMeteringPointAddress,
        balanceSupplierName = this.meta.balanceSupplierName,
        balanceSupplierContractName = this.meta.balanceSupplierContractName,
    )
