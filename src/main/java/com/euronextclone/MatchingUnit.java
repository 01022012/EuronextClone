package com.euronextclone;

import hu.akarnokd.reactive4java.base.Action1;
import hu.akarnokd.reactive4java.reactive.DefaultObservable;
import hu.akarnokd.reactive4java.reactive.Observable;
import hu.akarnokd.reactive4java.reactive.Observer;
import hu.akarnokd.reactive4java.reactive.Reactive;

import java.io.Closeable;
import java.util.List;

public class MatchingUnit implements Observable<Trade>
{
    public enum ContinuousTradingProcess {PreOpeningPhase, OpeningAuction, MainTradingSession, PreCloseingPhase, ClosingAuction, TradingAtLastPhase, AfterHoursTrading };

    private final OrderBook buyOrderBook;
    private final OrderBook sellOrderBook;
    private double imp = Double.MIN_VALUE;
    private ContinuousTradingProcess currentContinuousTradingProcess = ContinuousTradingProcess.MainTradingSession;

    /** The observable helper. */
    private final DefaultObservable<Trade> notifier = new DefaultObservable<Trade>();

    public MatchingUnit()
    {
        buyOrderBook = new OrderBook(Order.OrderSide.Buy);
        sellOrderBook = new OrderBook(Order.OrderSide.Sell);

        buyOrderBook.register(Reactive.toObserver(new Action1<Trade>() {
            public void invoke(Trade value) {
                notifier.next(value);
            }
        }));
        sellOrderBook.register(Reactive.toObserver(new Action1<Trade>() {
            public void invoke(Trade value) {
                notifier.next(value);
            }
        }));
    }

    public void auction() {
        // TODO: match everything you can from the books
    }

    public List<Order> getOrders(final Order.OrderSide side) {
        return getBook(side).getOrders();
    }

    public void addOrder(final Order.OrderSide side, final String broker, final int quantity, final OrderPrice price) {
        final OrderBook book = getBook(side);
        book.add(new Order(broker, quantity, price, side));
    }

    public void newOrder(final Order.OrderSide side, final String broker, final int quantity, final OrderPrice price)
    {
        final Order order = new Order(broker, quantity, price, side);
        final OrderBook orderBook = add(side, order);

        if (currentContinuousTradingProcess != ContinuousTradingProcess.PreOpeningPhase) {
            final OrderBook matchOrderBook = side != Order.OrderSide.Buy ? buyOrderBook : sellOrderBook;
                if (!matchOrderBook.match(order, currentContinuousTradingProcess))
                {
                    orderBook.remove(order);
                }
            }
    }

    private OrderBook add(final Order.OrderSide side, final Order order) {
        final OrderBook orderBook = side != Order.OrderSide.Buy ? sellOrderBook : buyOrderBook;
        orderBook.add(order);

        // Think IMP only used in Auctions - Need to confirm
        if (currentContinuousTradingProcess == ContinuousTradingProcess.OpeningAuction ||
                currentContinuousTradingProcess == ContinuousTradingProcess.ClosingAuction) {
            calcIndicativeMatchPrice();
        }

        return orderBook;
    }

    private void calcIndicativeMatchPrice() {
        if (buyOrderBook.getIMP().getOrderPrice().hasPrice() && sellOrderBook.getIMP().getOrderPrice().hasPrice()) {
            if (buyOrderBook.getIMP().getQuantity() > sellOrderBook.getIMP().getQuantity()) {
                imp = buyOrderBook.getIMP().getOrderPrice().value();
            } else {
                imp = sellOrderBook.getIMP().getOrderPrice().value();
            }
        } else if (!buyOrderBook.getIMP().getOrderPrice().hasPrice() && sellOrderBook.getIMP().getOrderPrice().hasPrice()) {
            imp = sellOrderBook.getIMP().getOrderPrice().value();
        } else if (buyOrderBook.getIMP().getOrderPrice().hasPrice() && !sellOrderBook.getIMP().getOrderPrice().hasPrice()) {
            imp = buyOrderBook.getIMP().getOrderPrice().value();
        }
    }

    public int orderBookDepth(final Order.OrderSide side)
    {
        final OrderBook orders = getBook(side);
        return orders.orderBookDepth();
    }

    public String getBestLimit(final Order.OrderSide side)
    {
        return side != Order.OrderSide.Buy ? sellOrderBook.getBestLimit().toString() : buyOrderBook.getBestLimit().toString();
    }

    public void dump()
    {
        System.out.println();
        System.out.println("Buy Book:");
        buyOrderBook.dump();
        System.out.println("Sell Book:");
        sellOrderBook.dump();
        System.out.println();
    }

    public Closeable register(Observer<? super Trade> observer) {
        return notifier.register(observer);
    }

    private OrderBook getBook(final Order.OrderSide side) {
        return side != Order.OrderSide.Buy ? sellOrderBook : buyOrderBook;
    }
}
