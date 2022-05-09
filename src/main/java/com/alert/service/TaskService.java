package com.alert.service;

import com.zerodhatech.models.Tick;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.num.DoubleNum;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class TaskService {

    private ExecutorService executorService;

    @PostConstruct
    public void init(){
        executorService = Executors.newFixedThreadPool(2);
    }

    public Future submitTask(Callable callable){
        return executorService.submit(callable);
    }

    public static void main(String[] args) throws InterruptedException {
        BarSeries barSeries = new BaseBarSeries("timeseries", DoubleNum::valueOf);
        barSeries.setMaximumBarCount(200);
       // barSeries.addTrade(100, 100);
        /*barSeries.addTrade(100, 100);
        Thread.sleep(1000);
        */
        Tick tick = new Tick();
        tick.setLastTradedPrice(100);
        tick.setLastTradedQuantity(100);
        tick.setLastTradedTime(new Date());
        ZonedDateTime endTime = ZonedDateTime.now().plusMinutes(1);
        endTime.minusSeconds(endTime.getSecond());
        Bar lastBar =  new BaseBar(Duration.ofMinutes(1), endTime, DoubleNum::valueOf);
        barSeries.addBar(lastBar);

        for(int i = 0; i < 10; i++) {
            if(!lastBar.inPeriod(ZonedDateTime.ofInstant(tick.getLastTradedTime().toInstant(), ZoneId.systemDefault()))){
                barSeries.addBar(new BaseBar(Duration.ofMinutes(1), endTime.plusMinutes(i+1), DoubleNum::valueOf));
            }
            barSeries.addTrade(tick.getLastTradedPrice(), tick.getLastTradedQuantity());
        }
        System.out.println("size : " + barSeries.getBarCount());
        System.out.println(barSeries.getLastBar().toString());
    }
}

