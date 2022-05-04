package com.alert.service;

import com.alert.dao.OrderRepository;
import com.alert.domain.IndexName;
import com.alert.entity.Order;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Log4j2
public class OpenOrderMonitor implements Runnable{

    private final Order order;

    private final Map<String, Double> priceMap;

    private final OrderRepository orderRepository;

    private final ZerodhaService zerodhaService;

    public OpenOrderMonitor(Order order, OrderRepository orderRepository, Map<String, Double> priceMap, ZerodhaService zerodhaService){
        this.order = order;
        this.priceMap = priceMap;
        this.orderRepository = orderRepository;
        this.zerodhaService = zerodhaService;
    }

    @Override
    public void run() {
        while(!order.isCompleted()){
            try {
                if(priceMap.get(order.getInstrumentId()) >= order.getEntryPrice() * 1.2){ // SL

                }
                Thread.sleep(200);
            } catch (InterruptedException e) {
                log.error("Error while running thread : ", e);
            }
        }

    }
}
