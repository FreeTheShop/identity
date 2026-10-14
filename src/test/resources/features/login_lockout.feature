Feature: Account lockout after repeated failed sign-ins
  To stop password guessing, an account locks for 15 minutes after 5 wrong passwords in a row,
  and the response never reveals whether an account exists or is locked.

  Background:
    Given a registered user "alice@example.com" with password "correct horse battery"

  Scenario: Five wrong passwords lock the account
    When someone signs in as "alice@example.com" with password "wrong password!" 5 times
    Then the account "alice@example.com" is locked for 15 minutes
    And signing in as "alice@example.com" with password "correct horse battery" is refused

  Scenario: Four wrong passwords then the right one
    When someone signs in as "alice@example.com" with password "wrong password!" 4 times
    And signing in as "alice@example.com" with password "correct horse battery" succeeds
    Then the account "alice@example.com" has no failed sign-ins

  Scenario: An unknown account gets the same answer as a wrong password
    When signing in as "nobody@example.com" with password "whatever password" is refused
    Then the refusal looks exactly like a wrong password
