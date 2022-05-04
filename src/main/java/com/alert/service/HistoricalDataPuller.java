package com.alert.service;

import com.alert.domain.HistoricalData;
import com.alert.domain.ZerodhaInstrument;
import com.alert.domain.ZerodhaTimeFrame;
import org.ta4j.core.BarSeries;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ProtocolException;


public interface HistoricalDataPuller {

    BarSeries getHistoricalBarSeries(ZerodhaInstrument zi, ZerodhaTimeFrame timeFrame, int days) throws IOException;

    HistoricalData getHistoricalData(ZerodhaInstrument zerodhaInstrument, ZerodhaTimeFrame zerodhaTimeFrame, int i) throws IOException;
}
