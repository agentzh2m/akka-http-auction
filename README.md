# Akka HTTP Auction

you need sbt to build you can easily do `sbt run` to run the software
the server is expose at 8080 

We expose the following api

* Create a new auction. `(POST /auction?create)` with an empty body. If successful, this will return 200 status code with the following json in the body:

```
{ "auction_id": <a unique identifier for this new auction> }

```

* Make a bid. `(POST /auction/<auction_id>)` with the following json in the body:

```
{ "bid_price": <your bid price a decimal number>,
  "bidder_id": <your identifier, any string> }

```

 * If you are the current winning bid, it will return:

```
{ "status": "won" }

```

 * If you are not the current winning bid, it will return:
```
{ "status": "lost",
"current_price": <price of the highest bid> }
```

* Get the current winner of an auction. `(GET /auction/<auction_id>)` returns

```
{ "bid_winner_id": <bidder_id of the winner>,
  "current_price": <price of the highest bid> }
```

* Conclude an auction. `(DELETE /auction/<auction_id>)` should conclude and remove this
auction. No one can bid on this auction after this point. It should return

```
{ "bid_winner_id": <bidder_id of the winner>,
"current_price": <price of the highest bid> }
```



