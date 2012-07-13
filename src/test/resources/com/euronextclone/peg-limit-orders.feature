Feature: Pegged orders with limit

  Scenario: Peg Order Limit Fill Trade
    Given the following orders are submitted in this order:
      | Broker | Side | Quantity | Order Type   | Price | Limit |
      | A      | Buy  | 200      | Limit        | 11.5  |       |
      | B      | Buy  | 150      | PegWithLimit | 11.5  | 11.6  |
      | C      | Sell | 200      | Limit        | 11.5  |       |
    Then the following trades are generated:
      | Buying broker | Selling broker | Quantity | Price |
      | A             | C              | 200      | 11.5  |
    And remaining buy order book depth is 0
    And remaining sell order book depth is 0

  Scenario: New Buy And Sell Order
    Given the following orders are submitted in this order:
      | Broker | Side | Quantity | Order Type   | Price | Limit |
      | A      | Buy  | 200      | Limit        | 11.5  |       |
      | B      | Buy  | 150      | PegWithLimit | 11.5  | 11.6  |
      | B      | Buy  | 70       | Peg          | 11.5  |       |
      | B      | Buy  | 125      | Limit        | 10.5  |       |
      | C      | Sell | 130      | Limit        | 11.8  |       |
      | C      | Sell | 350      | Limit        | 11.9  |       |
      | D      | Sell | 275      | Limit        | 12.0  |       |
      | E      | Buy  | 200      | Limit        | 11.7  |       |
    Then remaining buy order book depth is 5
    And remaining sell order book depth is 3

  Scenario: New Sell Order Test
    Given the following orders are submitted in this order:
      | Broker | Side | Quantity | Order Type   | Price | Limit |
      | A      | Buy  | 200      | Limit        | 11.5  |       |
      | B      | Buy  | 150      | PegWithLimit | 11.5  | 11.6  |
      | B      | Buy  | 70       | Peg          | 11.5  |       |
      | B      | Buy  | 125      | Limit        | 10.5  |       |
      | C      | Sell | 130      | Limit        | 11.8  |       |
      | C      | Sell | 350      | Limit        | 11.9  |       |
      | D      | Sell | 275      | Limit        | 12.0  |       |
      | E      | Buy  | 200      | Limit        | 11.7  |       |
      | A      | Sell | 270      | Limit        | 11.7  |       |
    Then the following trades are generated:
      | Buying broker | Selling broker | Quantity | Price |
      | E             | A              | 200      | 11.7  |
      | B             | A              | 70       | 11.7  |
    And remaining buy order book depth is 3
    And remaining sell order book depth is 3