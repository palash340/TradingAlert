package com.alert.controller;

import com.alert.service.ZerodhaService;
import com.alert.domain.ZerodhaTimeFrame;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@RestController
@RequestMapping("/api/option")
public class ZerodhaController {

    @Value("${zerodha.enableAlert}")
    boolean enableAlert;

    @Autowired
    ZerodhaService zerodhaService;

    @RequestMapping("/generateAlert")
//    @Scheduled(cron = "0 * 9-15 * * MON-FRI", zone = "IST")
    public void generateAlert(){
        if(!enableAlert)
            return;
        try {
            zerodhaService.generateSignals(ZerodhaTimeFrame.ONE_MINUTE);
        } catch (Exception e) {
            log.error("Error while generating signal", e);
        }
    }

    @RequestMapping("/backTest")
    public void backTest(){
        try {
            zerodhaService.generateSignals(ZerodhaTimeFrame.ONE_MINUTE);
        } catch (Exception e) {
            log.error("Error while generating signal", e);
        }
    }
}
