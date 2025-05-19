# User Guide

The Authorization Grant Manager handles the process for requesting and maintaining authorizations in the ELhub system.
between individuals and organizations. This service offers a REST API that is used when a client application needs to
request authorization/consent from a user to carry out an operation in Elhub on their behalf.

## Context

### Who is this for?

This API can be used by market actors who need to collect consent from end users for operations in Elhub. The physical
sender of the API request must be registered in the Elhub actor registry as a Maskinporten client.

### Consent Flows

The Authorization Grant Manager API provides two primary interaction flows for retrieving consents:
AuthorizationRequest and AuthorizationDocument. Both flows can be used to request consent from end users.

The AuthorizationRequest flow is used to initiate and manage a request for authorization/consent through the Elhub
MyPage. This flow has the user being redirected to the Authorization approval page in Elhub MyPage (requiring a login
through IDPorten) where the user can approve or deny the request. Upon doing so, the user is redirected back to the
client application.

![Dynamic Consent Flow](./assets/dynamic-diagram-request.png)

THe AuthorizationDocument flow is used to request consent from end users without the need for a redirect to Elhub.












The API provides endpoints for managing authorization requests, documents, and grants. Below is a guide on how to use the API effectively.

in order to carry out an operation in Elhub on their behalf or sign an authorization document. The API provides endpoints for managing authorization requests, documents, and grants. Below is a guide on how to use the API effectively.
It is used when a client application needs to request authorization from a user in order to carry out an operation in
Elhub on their behalf


or sign an authorization document. The API provides endpoints for managing authorization requests, documents, and grants. Below is a guide on how to use the API effectively.
adresses the need for requesting and maintaining authorizations between individuals
and organizations


API is used when a client application needs to request authorization from a user in order to
carry out an operation in Elhub on their behalf.



or sign an
authorization document. The API provides endpoints for managing authorization requests, documents, and grants. Below is a guide on how to use the API effectively.


