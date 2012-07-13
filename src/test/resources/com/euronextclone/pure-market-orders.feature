Feature: Pure market orders

  Background:
    Given the following orders are submitted in this order:
      | Broker | Side | Quantity | Order Type | Price |
      | A      | Sell | 100      | Limit      | 10.2  |
      | B      | Sell | 60       | Limit      | 10.3  |

  Scenario: Trading Session Example
    Given the following orders are submitted in this order:
      | Broker | Side | Quantity | Order Type  | Price |
      | C      | Buy  | 110      | MarketOrder |       |
    Then the following trades are generated:
      | Buying broker | Selling broker | Quantity | Price |
      | C             | A              | 100      | 10.2  |
      | C             | B              | 10       | 10.3  |
    Then remaining buy order book depth is 0
    And remaining sell order book depth is 1