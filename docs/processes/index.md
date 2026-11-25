# Processes

This section of the documentation describes the business processes of the `auth-grant-manager` service. Each process is
documented in its own page, with a description of the purpose, preconditions, sequence of steps, and expected outcomes.

The `auth-grant-manager` has two business processes, both of which are used to collect authorization from a person with
authority to approve or reject a request.

| Process                                             | Purpose                                                                      |
|-----------------------------------------------------|------------------------------------------------------------------------------|
| [Authorization request](authorization-request.md)   | Record a person's approval or rejection electronically through Elhub MyPage. |
| [Authorization document](authorization-document.md) | Verify a signed PDF as evidence of approval through a vendor's system.       |

Both processes can create [authorization grants](../resources/authorization-grant.md); though not all grants can
be created by both processes.
