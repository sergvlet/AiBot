package com.chicu.aibot.strategy.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Candle {
    private Instant timestamp;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
}