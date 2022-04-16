package com.alert.service;

import com.alert.dao.OrderRepository;
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

    private OrderRepository orderRepository;

    public OpenOrderMonitor(Order order, OrderRepository orderRepository, Map<String, Double> priceMap){
        this.order = order;
        this.priceMap = priceMap;
        this.orderRepository = orderRepository;
    }

    @Override
    public void run() {
        while(true){
            try {
                //log.info("Order is open for {}", order.getInstrumentSymbol());
                if(priceMap.get(order.getInstrumentId()) >= order.getEntryPrice() * 1.2){ // SL
                    log.info("sl hit for {}", order.getInstrumentSymbol());
                    order.setExitPrice(priceMap.get(order.getId()));
                    order.setExitTime(LocalDateTime.now());
                    order.setCompleted(true);
                    orderRepository.save(order);
                    break;
                }
                if(priceMap.get(order.getInstrumentId()) <= order.getEntryPrice() * 0.25){ // Target
                    log.info("target hit for {}", order.getInstrumentSymbol());
                    order.setExitPrice(priceMap.get(order.getInstrumentId()));
                    order.setExitTime(LocalDateTime.now());
                    order.setCompleted(true);
                    orderRepository.save(order);
                    break;
                }
                Thread.sleep(200);
            } catch (InterruptedException e) {
                log.error("Error while running thread : ", e);
            }
        }

    }
}
