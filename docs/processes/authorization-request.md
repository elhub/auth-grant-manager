# Authorization request

## Purpose

Collect an electronic approval or rejection from a person with authority to decide. The approval is collected through Elhub MyPage.

## Preconditions

* The requester (requestedBy) is a market entity with a valid Elhub role that allows it to request the authorization type.
* The principal (requestedPrincipal) is an agent that has the authority to approve or reject the request.

## Sequence

```mermaid
sequenceDiagram
    participant EU as End User
    participant ME as Market Entity
    participant AGM as auth-grant-manager
    participant EMP as Elhub MyPage
    participant EMIF as EMIF
    EU ->> ME: Initiates transaction
    ME ->> AGM: POST /authorization-requests
    AGM ->> ME: 201 Created<br>(requestId)
    ME ->> EMP: Redirect to MyPage with requestId
    EU ->> EMP: Log in and accept or reject the request
    EMP ->> AGM: PATCH /authorization-requests/{requestId}
    Note right of EMP: Update request with new status.
    AGM ->> EMP: 200 OK
    EMP ->> ME: Redirect to chosen Market Entity URI
    ME ->> AGM: GET /authorization-requests/{requestId}
    AGM ->> ME: 200 OK<br>(request status and grants)
    ME ->> EMIF: Send BRS with authorizationGrantId
```

The market entity creates an authorization request in Elhub. Using the ID of the request, the market entity can then
redirect the end user to Elhub MyPage, where the end user can log in and approve or reject the request. The end user
can also log in to MyPage directly to see any pending requests. After the end user makes a decision, the market
entity can retrieve the updated request status and any resulting grants. The market entity can then use the grant ID
to carry out  relevant actions in the Elhub system, such as sending a BRS message to EMIF.

## Outcome

If an authorization request is approved, the system records the decision, the signatory as `approvedBy`, and creates
the grants defined by the authorization type in the same transaction. If the request is rejected, the system records
the decision language and creates no grants. A request can only be decided once; concurrent or repeated decisions
cannot create a second set of grants.


## Related Resources

* [AuthorizationRequest](../resources/authorization-request.md)
