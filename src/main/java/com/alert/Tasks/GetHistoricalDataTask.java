package com.alert.Tasks;

import com.alert.domain.HistoricalData;
import com.alert.domain.ZerodhaInstrument;
import com.alert.domain.ZerodhaTimeFrame;
import com.alert.service.ZerodhaService;

import java.util.concurrent.Callable;

public class GetHistoricalDataTask implements Callable<HistoricalData> {

    ZerodhaService zerodhaService;

    ZerodhaInstrument zerodhaInstrument;

    ZerodhaTimeFrame zerodhaTimeFrame;

    public GetHistoricalDataTask(ZerodhaService zerodhaService, ZerodhaInstrument zi, ZerodhaTimeFrame zerodhaTimeFrame){
        this.zerodhaService = zerodhaService;
        this.zerodhaInstrument = zi;
        this.zerodhaTimeFrame = zerodhaTimeFrame;
    }

    @Override
    public HistoricalData call() throws Exception {
        return zerodhaService.getHistoricalData(zerodhaInstrument, zerodhaTimeFrame, 6);
    }
}
