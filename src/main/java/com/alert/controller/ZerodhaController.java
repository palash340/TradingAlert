package com.alert.controller;

import com.alert.service.ZerodhaService;
import com.alert.domain.ZerodhaTimeFrame;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/option")
public class ZerodhaController {

    @Value("${zerodha.enableAlert}")
    boolean enableAlert;

    @Autowired
    ZerodhaService zerodhaService;

    @RequestMapping("/generateAlert")
    public void generateAlert(){
        if(!enableAlert)
            return;
        try {
            zerodhaService.generateSignals(ZerodhaTimeFrame.ONE_MINUTE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
