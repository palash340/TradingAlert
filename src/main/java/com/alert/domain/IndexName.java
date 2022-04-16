package com.alert.domain;

public enum IndexName {
    NIFTY("NIFTY 50"),
    BANKNIFTY("NIFTY BANK");

    private String zerodhaInstrumentName;

    IndexName(String zerodhaInstrumentName){
       this.zerodhaInstrumentName = zerodhaInstrumentName;
    }

    public String getZerodhaInstrumentName(){
        return zerodhaInstrumentName;
    }
}
