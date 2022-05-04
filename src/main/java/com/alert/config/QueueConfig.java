package com.alert.config;

import com.alert.domain.QueueElement;
import lombok.Data;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Data
@Component
public class QueueConfig {

    private BlockingQueue<QueueElement> processQueue;
    private BlockingQueue<QueueElement> alertQueue;
    private BlockingQueue<QueueElement> backTestQueue;

    @PostConstruct
    public void init(){
        this.backTestQueue = new LinkedBlockingQueue<>();
        this.alertQueue = new LinkedBlockingQueue<>();
        this.processQueue = new LinkedBlockingQueue<>();
    }
}
