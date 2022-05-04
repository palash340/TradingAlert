package com.alert.Tasks;

import com.alert.domain.HistoricalData;
import com.alert.domain.ZerodhaInstrument;
import com.alert.domain.ZerodhaTimeFrame;
import com.alert.service.HistoricalDataPuller;

import java.util.concurrent.Callable;

public class GetHistoricalDataTask implements Callable<HistoricalData> {

    private final HistoricalDataPuller historicalDataPuller;

    private final ZerodhaInstrument zerodhaInstrument;

    private final ZerodhaTimeFrame zerodhaTimeFrame;

    private final int days;

    public GetHistoricalDataTask(HistoricalDataPuller historicalDataPuller, ZerodhaInstrument zi, ZerodhaTimeFrame zerodhaTimeFrame, int days){
        this.historicalDataPuller = historicalDataPuller;
        this.zerodhaInstrument = zi;
        this.zerodhaTimeFrame = zerodhaTimeFrame;
        this.days = days;
    }

    @Override
    public HistoricalData call() throws Exception {
        return historicalDataPuller.getHistoricalData(zerodhaInstrument, zerodhaTimeFrame, days);
    }
}
