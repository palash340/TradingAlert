package com.alert.entity;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.time.LocalDateTime;

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
}
