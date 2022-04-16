package com.alert.service;

import com.alert.entity.Order;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class OpenOrderMonitor implements Runnable{

    private Order order;



    @Override
    public void run() {
        order.getId();
    }
}
