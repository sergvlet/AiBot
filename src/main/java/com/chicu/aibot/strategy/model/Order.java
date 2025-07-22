package com.chicu.aibot.strategy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
}