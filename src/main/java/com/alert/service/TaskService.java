package com.alert.service;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TaskService {

    private ExecutorService executorService;

    @PostConstruct
    public void init(){
        executorService = Executors.newFixedThreadPool(2);
    }

    public void submitTask(Runnable runnable){
        executorService.submit(runnable);
    }
}
