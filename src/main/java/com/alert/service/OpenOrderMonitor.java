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

    private Order order;

    private final Map<String, Double> priceMap;

    private OrderRepository orderRepository;

    private ZerodhaService zerodhaService;

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
                    log.info("Hard sl hit for {}", order.getInstrumentSymbol());
                    log.info("priceMap {}", priceMap);
                    order.setExitPrice(priceMap.get(order.getInstrumentId()));
                    order.setExitTime(LocalDateTime.now());
                    order.setCompleted(true);
                    orderRepository.save(order);
                    IndexName indexName = order.getInstrumentSymbol().contains("BANKNIFTY") ? IndexName.BANKNIFTY : IndexName.NIFTY;
                    zerodhaService.getActiveTrades().remove(indexName);
                    zerodhaService.getWebSocketClientEndpoint().sendMessage("{\"a\":\"unsubscribe\",\"v\":[" + order.getInstrumentId() + "]}");
                    zerodhaService.sendAlertToTelegram("Hard 20% Sl hit  " + order.getInstrumentSymbol() + ", price: " + priceMap.get(order.getInstrumentId()));
                }
                if(priceMap.get(order.getInstrumentId()) <= order.getEntryPrice() * 0.25){ // Target
                    log.info("target hit for {}", order.getInstrumentSymbol());
                    log.info("priceMap {}", priceMap);
                    order.setExitPrice(priceMap.get(order.getInstrumentId()));
                    order.setExitTime(LocalDateTime.now());
                    order.setCompleted(true);
                    orderRepository.save(order);
                    IndexName indexName = order.getInstrumentSymbol().contains("BANKNIFTY") ? IndexName.BANKNIFTY : IndexName.NIFTY;
                    zerodhaService.getActiveTrades().remove(indexName);
                    zerodhaService.getWebSocketClientEndpoint().sendMessage("{\"a\":\"unsubscribe\",\"v\":[" + order.getInstrumentId() + "]}");
                    zerodhaService.sendAlertToTelegram("Target 75% hit  " + order.getInstrumentSymbol() + ", price: " + priceMap.get(order.getInstrumentId()));
                }
                if(order.isSoftStopLossSignal() && order.getSuperTrendValues().get("sup73") >= priceMap.get(order.getInstrumentId())){
                    log.info("sl hit for post soft sl {}", order.getInstrumentSymbol());
                    log.info("priceMap {}", priceMap);
                    order.setExitPrice(priceMap.get(order.getInstrumentId()));
                    order.setExitTime(LocalDateTime.now());
                    order.setCompleted(true);
                    orderRepository.save(order);
                    IndexName indexName = order.getInstrumentSymbol().contains("BANKNIFTY") ? IndexName.BANKNIFTY : IndexName.NIFTY;
                    zerodhaService.getActiveTrades().remove(indexName);
                    zerodhaService.getWebSocketClientEndpoint().sendMessage("{\"a\":\"unsubscribe\",\"v\":[" + order.getInstrumentId() + "]}");
                    zerodhaService.sendAlertToTelegram("Sl sup73 hit  " + order.getInstrumentSymbol() + ", price: " + priceMap.get(order.getInstrumentId()));
                }
                Thread.sleep(200);
            } catch (InterruptedException e) {
                log.error("Error while running thread : ", e);
            }
        }

    }
}
