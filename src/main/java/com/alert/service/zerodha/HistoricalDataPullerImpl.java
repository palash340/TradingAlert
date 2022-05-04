package com.alert.service.zerodha;

import com.alert.config.QueueConfig;
import com.alert.domain.*;
import com.alert.service.HistoricalDataPuller;
import com.alert.service.TaskService;
import com.alert.utils.StreamUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DoubleNum;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;

@Service
public class HistoricalDataPullerImpl implements HistoricalDataPuller {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskService taskService;

    @Autowired
    private QueueConfig queueConfig;
    @Value("${zerodha.authToken}")
    private String authToken;

    @Value("${zerodha.username}")
    private String username;

    private final String DATE_FORMAT = "YYYY-MM-dd";

    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern(DATE_FORMAT);

    private final DateTimeFormatter offsetDateFormatter = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .appendOffset("+HHMM","Z")
            .toFormatter();

    @Override
    public BarSeries getHistoricalBarSeries(ZerodhaInstrument zi, ZerodhaTimeFrame timeFrame, int days) throws IOException {
        BarSeries barSeries = null;
        HistoricalData historicalData = getHistoricalData(zi, timeFrame, days);
        if(historicalData != null) {
            List<ZerodhaCandle> zerodhaCandleList = historicalData.getZerodhaCandleList();
            if (zerodhaCandleList.size() == 0)
                return null;
            int lastIndex = zerodhaCandleList.size() - 1;
            if (!isLastCandleComplete(timeFrame, zerodhaCandleList.get(lastIndex))) {
                zerodhaCandleList.remove(lastIndex);
            }
            barSeries = new BaseBarSeriesBuilder().withName("dekho").withNumTypeOf(DoubleNum.class).build();
            for (ZerodhaCandle zerodhaCandle : historicalData.getZerodhaCandleList()) {
                barSeries.addBar(zerodhaCandle.getTimestamp().toZonedDateTime(), zerodhaCandle.getOpen(), zerodhaCandle.getHigh(), zerodhaCandle.getLow(), zerodhaCandle.getClose(), zerodhaCandle.getVolume());
            }
        }
        //queueConfig.getBackTestQueue().add(barSeries);
        return barSeries;
    }

    private boolean isLastCandleComplete(ZerodhaTimeFrame zerodhaTimeFrame, ZerodhaCandle zerodhaCandle){
        Duration between = Duration.between(zerodhaCandle.getTimestamp(), OffsetDateTime.now());
        return between.toMinutes() >= zerodhaTimeFrame.getMinutes();
    }

    @Override
    public HistoricalData getHistoricalData(ZerodhaInstrument zi, ZerodhaTimeFrame timeFrame , int days) throws IOException {
        URL url = new URL(buildURL(zi.getId(),timeFrame,days));
        CookieManager cookieManager = new CookieManager();
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("authority", "kite.zerodha.com");
        con.setRequestProperty("accept-language", "en-US,en;q=0.9");
        con.setRequestProperty("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.67 Safari/537.36");
        con.setRequestProperty("authorization", "enctoken "+ authToken);
        int responseCode = con.getResponseCode();
        if(responseCode != 200)
            return null;
        String jsonResponse = StreamUtils.convertStreamToString(con.getInputStream());
        return parseHistoricalData(objectMapper.readValue(jsonResponse, HistoricalData.class));
    }

    private String buildURL (String instrumentId, ZerodhaTimeFrame timeFrame , int days){
        StringBuilder sb = new StringBuilder();
        sb.append("https://kite.zerodha.com/oms/instruments/historical/");
        sb.append(instrumentId);
        sb.append("/");
        sb.append(timeFrame.getKey());
        sb.append("?user_id=");
        sb.append(username);
        sb.append("&oi=1&from=");
        sb.append(dtf.format(LocalDate.now().plusDays(-days)));
        sb.append("&to=");
        sb.append(dtf.format(LocalDate.now()));
        sb.append("&ciqrandom=");
        sb.append((long) Math. floor(Math. random() * 9000000000000L) + 1000000000000L);
        return sb.toString();
    }

    private HistoricalData parseHistoricalData(HistoricalData data){
        List<ZerodhaCandle> zerodhaCandles = new ArrayList<>();
        data.setZerodhaCandleList(zerodhaCandles);
        for (List<String> candleData: data.getData().getCandles()) {
            ZerodhaCandle zerodhaCandle = new ZerodhaCandle();
            for(int i = 0; i < candleData.size(); i++){
                switch (ZerodhaCandleIndex.findEnum(i)){
                    case TIMESTAMP: {
                        zerodhaCandle.setTimestamp(OffsetDateTime.parse(candleData.get(i), offsetDateFormatter));
                        break;
                    }
                    case HIGH: {
                        zerodhaCandle.setHigh(new Double(candleData.get(i)));
                        break;
                    }
                    case OPEN: {
                        zerodhaCandle.setOpen(new Double(candleData.get(i)));
                        break;
                    }
                    case LOW: {
                        zerodhaCandle.setLow(new Double(candleData.get(i)));
                        break;
                    }
                    case CLOSE: {
                        zerodhaCandle.setClose(new Double(candleData.get(i)));
                        break;
                    }
                    case VOLUME: {
                        zerodhaCandle.setVolume(Integer.valueOf(candleData.get(i)));
                        break;
                    }
                    case OI: {
                        zerodhaCandle.setOi(Integer.valueOf(candleData.get(i)));
                        break;
                    }
                    default:
                        break;
                }
            }
            zerodhaCandles.add(zerodhaCandle);
        }
        return data;
    }
}
