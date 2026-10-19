package no.elhub.auth.v1.domain

sealed interface AgentExternalIdentifier {
    val value: String

    @JvmInline
    value class OrganizationIdentifier(
        override val value: String,
    ) : AgentExternalIdentifier {
        init {
            if (!ORGANIZATION_IDENTIFIER_REGEX.matches(value)) {
                throw DomainError.InvalidExternalAgentIdentifier(value)
            }
        }
    }

    @JvmInline
    value class PersonIdentifier(
        override val value: String,
    ) : AgentExternalIdentifier {
        init {
            if (!PERSON_IDENTIFIER_REGEX.matches(value)) {
                throw DomainError.InvalidExternalAgentIdentifier(value)
            }
        }
    }

    @JvmInline
    value class MarketPartyIdentifier(
        override val value: String,
    ) : AgentExternalIdentifier {
        init {
            if (!MARKET_PARTY_IDENTIFIER_REGEX.matches(value)) {
                throw DomainError.InvalidExternalAgentIdentifier(value)
            }
        }
    }

    companion object {
        fun create(value: String): AgentExternalIdentifier = when (value.length) {
            9 -> OrganizationIdentifier(value)
            11 -> PersonIdentifier(value)
            13 -> MarketPartyIdentifier(value)
            else -> throw DomainError.InvalidExternalAgentIdentifier(value)
        }
    }
}

private val ORGANIZATION_IDENTIFIER_REGEX = Regex("[0-9]{9}")
private val PERSON_IDENTIFIER_REGEX = Regex("[0-9]{11}")
private val MARKET_PARTY_IDENTIFIER_REGEX = Regex("[0-9]{13}")
