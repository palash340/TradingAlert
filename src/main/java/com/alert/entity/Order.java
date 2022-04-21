package com.alert.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Entity
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String instrumentId;

    private String instrumentSymbol;

    private double entryPrice;

    private LocalDateTime entryTime;

    private double exitPrice;

    private LocalDateTime exitTime;

    private boolean completed;

    private boolean softStopLossSignal = false;

    @Transient
    private Map<String, Double> superTrendValues = new HashMap<>();
}
