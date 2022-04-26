package com.alert.controller;

import com.alert.service.ZerodhaService;
import com.alert.domain.ZerodhaTimeFrame;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/option")
public class ZerodhaController {

    @Value("${zerodha.enableAlert}")
    boolean enableAlert;

    @Autowired
    ZerodhaService zerodhaService;

    @RequestMapping("/generateAlert")
    @Scheduled(cron = "0 * 9-15 * * MON-FRI", zone = "IST")
    public void generateAlert(){
        if(!enableAlert)
            return;
        LocalDateTime now = LocalDateTime.now();
        if(now.getHour() == 9 && now.getMinute() <= 15)
            return;
        try {
            zerodhaService.generateSignals(ZerodhaTimeFrame.ONE_MINUTE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
