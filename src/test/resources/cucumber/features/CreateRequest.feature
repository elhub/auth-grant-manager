Feature: Create Change Supplier Request
        As an energy provider
        I want to get consent from a potential customer
        So that I can move their subscription to me

        Scenario: Request consent through Elhub
                Given that the potential customer is not already a customer with me
                When I request consent
                Then a change supplier request should be created

        Scenario: Request consent myself
                Given that the potential customer is not already a customer with me
                When I request consent
                And specify that a consent form should be generated
                Then a change supplier request should be created
                And a consent form should be returned

        Scenario: Potential customer is already a customer with me
                Given that the potential customer is already a customer with me
                When I request consent
                Then a "New supplier same as old supplier" error should be returned

        Scenario: Metering point does not exist
                Given that the specified metering point does not exist
                When I request consent
                Then a "Metering point does not exist" error should be returned
