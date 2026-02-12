# Frequently Asked Questions (FAQ)

---

## General Questions

### How do I gain access to the test environment?

* You need to be registered in Maskinporten (https://api.elhub.no/maskinporten/getting-started)
* The IPs of your test environments need to be whitelisted by Elhub.
  * See [elhub.no](https://elhub.no/fagomrader/markedsprosesser/samtykkekontroll?article=test-av-samtykkekontroll)

### How do I test the authorization flows with business processes?

The recommended way to test the business processes for ChangeOfEnergySupplierForPerson and MoveInAndChangeOfEnergySupplierForPerson is
[documented here](https://elhub.no/fagomrader/markedsprosesser/samtykkekontroll?article=test-av-samtykkekontroll).

### Are there any costs with associated with using the auth-grant-manager API?

No, there are no costs associated with using the auth-grant-manager API.

If you are using the authorization document flow, you will need to use an approved third-party service to generate the
signed documents. The cost of using this service will depend on the provider you choose.

If you are using the authorization request flow, the validations occur in our system and there are no additional costs
associated with this flow.

### Are there any limitations on the number of API calls I can make?

Yes, there are rate limits in place to ensure the stability and performance of the API. The specific rate limits may
vary based on your usage and the type of API calls you are making. We will provide more details on the rate limits
in [the API documentation](https://api.elhub.no).

---

## Market Process Specific Questions: ChangeOfEnergySupplierForPerson and MoveInAndChangeOfEnergySupplierForPerson

### What are the two different document/request types for?

The two different types of documents/requests correspond to the different market processes (BRS-NO-101, BRS-NO-102,
and BRS-NO-103). ChangeOfEnergySupplierForPerson is used for BRS-NO-101, while MoveInAndChangeOfEnergySupplierForPerson is
used for BRS-NO-102 and BRS-NO-103.

### What do the different meta fields mean in the request/document?

* **requestedBy**: This is the entity that is requesting the authorization. In this context, it is always the Market Participant (GLN).
* **requestedFrom**: This is the entity that the authorization request/document is being sent requested from. In this
    context, it is always the end user (identified by their national ID number).
* **requestedTo**: This is the entity that the authorization request/document is being sent to (for approval). In
    the current implementation, this is always the same as requestedFrom. However, in the future, we may support
    sending the request/document to a different entity for approval (e.g. a person with power of attorney for the
    end user).

### What is validated in the authorization request/document?

The following fields are validated when the authorization request/document is processed:

* **balanceSupplierName**: Must be defined.
* **balanceSupplierContractName**: We validate that the contract name exists in [strompris.no](https://strompris.no/) (same data in test and
    production), but the request will not be rejected if there is no match.
* **requestedFrom**: We validate that the entity is the end user of the metering point when the message type is
    "ChangeOfEnergySupplierForPerson". The opposite is checked for "MoveInAndChangeOfEnergySupplierForPerson".
* **requestedFromName**: Must be defined.
* **requestedForMeteringPointId**: We validate that the metering point exists in Elhub.
* **requestedForMeteringPointAddress**: Must be defined.
* **redirectURI**: For Authorization Request flow, it must be a valid URI, and we validate that the redirectURI is
    registered in the Ediel portal for the requesting Market Participant. If not, the request will be rejected.
    For Authorization Document flow, this field is not used.
* **requestedBy**: We validate that the GLN is an active Market Participant in Elhub and (for ChangeOfEnergySupplierForPerson)
    that it is not the current supplier in the metering point.
* **startDate**: For `documentType: MoveInAndChangeOfEnergySupplierForPerson`, omit `startDate` when the move-in is in the future (BRS-102). If the move-in is today or in the past (BRS-103), you must set `startDate`.

### Move-in in the future vs today/past (BRS-102 vs BRS-103)

For move-in scenarios you should use `documentType: MoveInAndChangeOfEnergySupplierForPerson`. The difference is how you handle `startDate`:

* **Future move-in (BRS-102):** Do not include `startDate` in the request. We will validate that the date is not set to a date in the future if present.
* **Today or past move-in (BRS-103):** You must include `startDate`.

`documentType: ChangeOfEnergySupplierForPerson` applies only to the current end user on the metering point and is not used for move-in cases.

### What do I do if I need help setting up test data?

* See information and contact details [here](https://elhub.no/fagomrader/markedsprosesser/samtykkekontroll?article=test-av-samtykkekontroll).

---
