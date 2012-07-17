package com.euronextclone;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import hu.akarnokd.reactive4java.base.Action1;
import hu.akarnokd.reactive4java.reactive.DefaultObservable;
import hu.akarnokd.reactive4java.reactive.Observable;
import hu.akarnokd.reactive4java.reactive.Observer;
import hu.akarnokd.reactive4java.reactive.Reactive;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.TreeSet;

public class MatchingUnit implements Observable<Trade> {

    public void startAuction() {
        currentContinuousTradingProcess = ContinuousTradingProcess.OpeningAuction;
    }

    public enum ContinuousTradingProcess {PreOpeningPhase, OpeningAuction, MainTradingSession, PreCloseingPhase, ClosingAuction, TradingAtLastPhase, AfterHoursTrading}

    private final OrderBook buyOrderBook;
    private final OrderBook sellOrderBook;
    private ContinuousTradingProcess currentContinuousTradingProcess = ContinuousTradingProcess.MainTradingSession;
    private double referencePrice;

    /**
     * The observable helper.
     */
    private final DefaultObservable<Trade> notifier = new DefaultObservable<Trade>();

    public MatchingUnit() {
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

    public void setReferencePrice(double referencePrice) {
        this.referencePrice = referencePrice;
    }

    public Double getIndicativeMatchingPrice() {

        final List<Double> eligiblePrices = getListOfEligiblePrices();
        final List<Integer> cumulativeBuy = getCumulativeQuantity(eligiblePrices, buyOrderBook, Order.OrderSide.Buy);
        final List<Integer> cumulativeSell = getCumulativeQuantity(eligiblePrices, sellOrderBook, Order.OrderSide.Sell);
        final List<VolumeAtPrice> totalTradeableVolume = getTotalTradeableVolume(eligiblePrices, cumulativeBuy, cumulativeSell);

        final Optional<VolumeAtPrice> max = tryGetSingleMaxTradeableVolumeIndex(totalTradeableVolume);
        if (max.isPresent()) {
            return max.get().price;
        }

        return null;
    }

    private static class VolumeAtPrice {
        private double price;
        private int buyVolume;
        private int sellVolume;

        public VolumeAtPrice(double price, int buyVolume, int sellVolume) {
            this.price = price;
            this.buyVolume = buyVolume;
            this.sellVolume = sellVolume;
        }

        public double getTradeableVolume() {
            return Math.min(buyVolume, sellVolume);
        }

        public static final Comparator<VolumeAtPrice> TRADEABLE_VOLUME_COMPARATOR = new Comparator<VolumeAtPrice>() {

            @Override
            public int compare(VolumeAtPrice volumeAtPrice, VolumeAtPrice volumeAtPrice1) {
                Double tradeableVolume1 = volumeAtPrice.getTradeableVolume();
                Double tradeableVolume2 = volumeAtPrice1.getTradeableVolume();
                return tradeableVolume1.compareTo(tradeableVolume2);
            }
        };
    }

    private Optional<VolumeAtPrice> tryGetSingleMaxTradeableVolumeIndex(List<VolumeAtPrice> totalTradeableVolume) {

        final double maxVolume = Collections.max(totalTradeableVolume, VolumeAtPrice.TRADEABLE_VOLUME_COMPARATOR).getTradeableVolume();

        FluentIterable<VolumeAtPrice> maxVolumeOnly = FluentIterable.from(totalTradeableVolume).filter(new Predicate<VolumeAtPrice>() {
            @Override
            public boolean apply(@Nullable VolumeAtPrice input) {
                return input.getTradeableVolume() == maxVolume;
            }
        });

        // TODO: this is simply picking last max hit, should not succeed if multiple max price levels exist
        return maxVolumeOnly.last();
    }

    private List<Integer> getCumulativeQuantity(
            final List<Double> eligiblePrices,
            final OrderBook book,
            final Order.OrderSide side) {

        final List<Integer> quantities = new ArrayList<Integer>(eligiblePrices.size());

        final ListIterator<Order> current = book.getOrders().listIterator();
        int cumulative = 0;

        for (final Double price : eligiblePrices) {
            while (current.hasNext()) {
                final Order order = current.next();
                final OrderTypeLimit limit = order.getOrderTypeLimit();

                if (limit.canTrade(price, side)) {
                    cumulative += order.getQuantity();
                } else {
                    current.previous();
                }
            }
            quantities.add(cumulative);
        }
        return quantities;
    }

    private List<VolumeAtPrice> getTotalTradeableVolume(
            final List<Double> eligiblePrices,
            final List<Integer> cumulativeBuy,
            final List<Integer> cumulativeSell) {

        final List<VolumeAtPrice> tradeableVolume = new ArrayList<VolumeAtPrice>();
        final Iterator<Double> priceIterator = eligiblePrices.iterator();
        final Iterator<Integer> buy = cumulativeBuy.iterator();
        final Iterator<Integer> sell = cumulativeSell.iterator();

        while (priceIterator.hasNext()) {
            final double price = priceIterator.next();
            final int buyVolume = buy.next();
            final int sellVolume = sell.next();
            tradeableVolume.add(new VolumeAtPrice(price, buyVolume, sellVolume));
        }

        return tradeableVolume;
    }

    private List<Double> getListOfEligiblePrices() {

        final TreeSet<Double> prices = new TreeSet<Double>();
        prices.add(referencePrice);
        prices.addAll(getLimitPrices(buyOrderBook));
        prices.addAll(getLimitPrices(sellOrderBook));

        return new ArrayList<Double>(prices);
    }

    private Collection<? extends Double> getLimitPrices(final OrderBook book) {

        return FluentIterable.from(book.getOrders()).filter(new Predicate<Order>() {
            @Override
            public boolean apply(Order input) {
                return input.getOrderTypeLimit().hasLimit();
            }
        }).transform(new Function<Order, Double>() {
            @Override
            public Double apply(Order input) {
                return input.getOrderTypeLimit().getLimit();
            }
        }).toImmutableSet();
    }

    public void auction() {
        while (!buyOrderBook.getOrders().isEmpty()) {
            if (!tryMatchOrder(buyOrderBook.getOrders().get(0))) {
                break;
            }
        }
    }

    public List<Order> getOrders(final Order.OrderSide side) {
        return getBook(side).getOrders();
    }

    // Auction add
    public void addOrder(final Order.OrderSide side, final String broker, final int quantity, final OrderTypeLimit orderTypeLimit) {
        final Order order = new Order(broker, quantity, orderTypeLimit, side);
        getBook(side).add(order);

        if (currentContinuousTradingProcess == ContinuousTradingProcess.MainTradingSession) {
            tryMatchOrder(order);
        }
    }

    private boolean tryMatchOrder(final Order order) {
        final Order.OrderSide side = order.getSide();
        final OrderBook book = getBook(side);
        final int startQuantity = order.getQuantity();
        final OrderBook counterBook = getCounterBook(side);

        if (!counterBook.match(order, null)) {
            book.remove(order);
        }

        return startQuantity != order.getQuantity();
    }

    public int orderBookDepth(final Order.OrderSide side) {
        final OrderBook orders = getBook(side);
        return orders.orderBookDepth();
    }

    public String getBestLimit(final Order.OrderSide side) {
        return side != Order.OrderSide.Buy ? sellOrderBook.getBestLimit().toString() : buyOrderBook.getBestLimit().toString();
    }

    public void dump() {
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

    private OrderBook getCounterBook(final Order.OrderSide side) {
        return side != Order.OrderSide.Buy ? buyOrderBook : sellOrderBook;
    }
}