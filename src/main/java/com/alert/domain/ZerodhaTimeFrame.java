package com.alert.domain;


public enum ZerodhaTimeFrame {
    ONE_MINUTE("minute", 1),
    FIVE_MINUTE("5minute", 5),
    FIFTEEN_MINUTE("15minute", 15),
    THIRTY_MINUTE("30minute", 30),
    SIXTY_MINUTE("60minute", 60);

    private String key;

    private int minutes;

    ZerodhaTimeFrame(String key, int minutes){
        this.key = key;
        this.minutes = minutes;
    }

    public String getKey(){
        return key;
    }
    public int getMinutes(){
        return minutes;
    }

}
