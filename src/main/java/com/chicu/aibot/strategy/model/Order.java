package com.chicu.aibot.strategy.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class Order {

    public enum Side { BUY, SELL }

    private String id;
    private String symbol;
    private Side side;
    private double price;
    private double volume;

    // флаги состояния
    private boolean filled;
    private boolean cancelled;
    private boolean closed;
    @Getter
    private boolean rejected;

    // --- Конструкторы ---

    public Order() {
    }

    public Order(String id, String symbol, Side side,
                 double price, double volume,
                 boolean filled, boolean cancelled, boolean closed) {
        this(id, symbol, side, price, volume, filled, cancelled, closed, false);
    }


    public Order(String id, String symbol, Side side,
                 double price, double volume,
                 boolean filled, boolean cancelled, boolean closed, boolean rejected) {
        this.id = id;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.volume = volume;
        this.filled = filled;
        this.cancelled = cancelled;
        this.closed = closed;
        this.rejected = rejected;
    }

    // --- Утилиты ---

    public boolean isOpen() {
        return !filled && !cancelled && !closed && !rejected;
    }

}
