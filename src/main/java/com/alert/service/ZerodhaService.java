package com.alert.service;

import com.alert.dao.OrderRepository;
import com.alert.domain.*;
import com.alert.entity.Order;
import com.alert.service.calc.SuperTrendIndicator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alert.Tasks.GetHistoricalDataTask;
import com.alert.utils.StreamUtils;
import lombok.Data;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DoubleNum;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Data
@Log4j2
@Service
public class ZerodhaService {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ExecutorService executorService;

    @Value("${zerodha.authToken}")
    String authToken;

    @Value("${zerodha.username}")
    String username;

    @Autowired
    private TaskService taskService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private WebSocketClientEndpoint webSocketClientEndpoint;

    String currentExpiry;

    Set<String> indexListToBeIgnored = new HashSet<>(Arrays.asList("MIDCPNIFTY", "FINNIFTY"));

    Map<IndexName, Order> activeTrades =  new HashMap<>();

    @Getter
    private Map<String, Double> indexCurrentPriceMap = new HashMap<>();

    private List<CSVRecord> records = new ArrayList<>();

    static String DATE_FORMAT = "YYYY-MM-dd"; //2021-03-04

    DateTimeFormatter offsetDateformatter = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .appendOffset("+HHMM","Z")
            .toFormatter();

    static DateTimeFormatter dtf = DateTimeFormatter.ofPattern(DATE_FORMAT);

    @PostConstruct
    public void init(){
        // Load active trades
        List<Order> openOrder = orderRepository.findOpenOrder();
        for (Order order : openOrder) {
            webSocketClientEndpoint.sendMessage("{\"a\":\"subscribe\",\"v\":["+order.getInstrumentId()+"]}");
            if(order.getInstrumentSymbol().contains("BANKNIFTY")){
                activeTrades.put(IndexName.BANKNIFTY, order);
            }else{
                activeTrades.put(IndexName.NIFTY, order);
            }
            indexCurrentPriceMap.put(order.getInstrumentId(), order.getEntryPrice());
            taskService.submitTask(new OpenOrderMonitor(order, orderRepository, indexCurrentPriceMap, this));
        }
        try {
            loadInstruments();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadInstruments() throws IOException {
        String fileName = "NFO_" + dtf.format(LocalDate.now()) + ".csv";
        File file = new File(fileName);
        if(!file.exists()){
            URL url = new URL("https://api.kite.trade/instruments/NFO");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            int responseCode = con.getResponseCode();
            if(responseCode == 200)
                FileUtils.copyInputStreamToFile(con.getInputStream(), file);
        }
        if(file.exists() && StringUtils.isBlank(currentExpiry)){
            LocalDate date = null;
            LocalDate temp;
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .withHeader("instrument_token","exchange_token","tradingsymbol","name","last_price","expiry","strike","tick_size","lot_size","instrument_type","segment","exchange").parse(new InputStreamReader(new FileInputStream(file)));
            for (CSVRecord record : records) {
                this.records.add(record);
                if("expiry".equals(record.get("expiry")) || indexListToBeIgnored.contains(record.get("name"))){
                    continue;
                }
                temp = LocalDate.parse(record.get("expiry"));
                if(date == null){
                    date = temp;
                }else if(date.isAfter(temp)){
                    date = temp;
                }
            }
            currentExpiry = dtf.format(date);
        }
    }


    public void generateSignals(ZerodhaTimeFrame zerodhaTimeFrame) {
        log.info("priceMap {}", indexCurrentPriceMap);
        Map<ZerodhaInstrument, Bar> activeSignal = new HashMap<>();
        Map<ZerodhaInstrument, Future<HistoricalData>> futureTasks = new HashMap<>();

        for (CSVRecord record : records) {
            for (IndexName indexName : IndexName.values()) {
                double price = indexCurrentPriceMap.get(indexName.name());
                if(activeTrades.containsKey(indexName) && !activeTrades.get(indexName).getInstrumentSymbol().equals(record.get("tradingsymbol"))){
                        continue;
                }

                if(indexName.name().equals(record.get("name")) && currentExpiry.equals(record.get("expiry"))){
                    ZerodhaInstrument zi = new ZerodhaInstrument();
                    zi.setId(record.get("instrument_token"));
                    zi.setInstrumentType(InstrumentType.valueOf(record.get("instrument_type")));
                    zi.setName(record.get("name"));
                    zi.setStrike(Integer.parseInt(record.get("strike")));
                    zi.setTradingsymbol(record.get("tradingsymbol"));
                    zi.setExpiry(LocalDate.parse(record.get("expiry")));
                    if(validStrikePrice(price, indexName, zi.getInstrumentType()) == zi.getStrike() || activeTrades.containsKey(indexName)) {
                        futureTasks.put(zi, executorService.submit(new GetHistoricalDataTask(this, zi, zerodhaTimeFrame)));
                    }
                }
            }
        }
        while(!futureTasks.isEmpty()){
            List<ZerodhaInstrument> zerodhaInstrumentList = new ArrayList<>();
            for(Map.Entry<ZerodhaInstrument, Future<HistoricalData>> zerodhaInstrumentFutureEntry : futureTasks.entrySet()) {
                Future<HistoricalData> dataFuture = zerodhaInstrumentFutureEntry.getValue();
                if(dataFuture.isDone()){
                    ZerodhaInstrument zi = zerodhaInstrumentFutureEntry.getKey();
                    HistoricalData data = null;
                    try {
                        data = dataFuture.get();
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                    zerodhaInstrumentList.add(zi);
                    if(data != null) {
                        List<ZerodhaCandle> zerodhaCandleList = data.getZerodhaCandleList();
                        if (zerodhaCandleList.size() == 0)
                            continue;
                        int lastIndex = zerodhaCandleList.size() - 1;
                        if (!isLastCandleComplete(zerodhaTimeFrame, zerodhaCandleList.get(lastIndex))) {
                            zerodhaCandleList.remove(lastIndex);
                        }
                        BarSeries barSeries = new BaseBarSeriesBuilder().withName("dekho").withNumTypeOf(DoubleNum.class).build();
                        for (ZerodhaCandle zerodhaCandle : data.getZerodhaCandleList()) {
                            barSeries.addBar(zerodhaCandle.getTimestamp().toZonedDateTime(), zerodhaCandle.getOpen(), zerodhaCandle.getHigh(), zerodhaCandle.getLow(), zerodhaCandle.getClose(), zerodhaCandle.getVolume());
                        }

                        SuperTrendIndicator superTrendIndicator72 = new SuperTrendIndicator(barSeries, 2.0, 7);
                        SuperTrendIndicator superTrendIndicator73 = new SuperTrendIndicator(barSeries, 3.0, 7);
                        SuperTrendIndicator superTrendIndicator74 = new SuperTrendIndicator(barSeries, 4.0, 7);
                        SuperTrendIndicator superTrendIndicator75 = new SuperTrendIndicator(barSeries, 5.0, 7);

                        int i = 0;
                        IndexName indexName = zi.getTradingsymbol().contains("BANKNIFTY") ? IndexName.BANKNIFTY : IndexName.NIFTY;
                        double closingPrice = barSeries.getBar(barSeries.getEndIndex() - i).getClosePrice().doubleValue();
                        if(activeTrades.containsKey(indexName) && activeTrades.get(indexName).getInstrumentSymbol().equals(zi.getTradingsymbol())){
                            Order order = activeTrades.get(indexName);
                            if(closingPrice >= superTrendIndicator75.getValue(barSeries.getEndIndex() - i)){
                                log.info("Sl hit price closing above super trend 75 {}", indexCurrentPriceMap);
                                sendAlertToTelegram("Sl hit price closing above super trend 75 " + order.getInstrumentSymbol());
                                order.setCompleted(true);
                                order.setExitPrice(indexCurrentPriceMap.get(zi.getId()));
                                order.setExitTime(LocalDateTime.now());
                                orderRepository.save(order);
                                activeTrades.remove(indexName);
                                webSocketClientEndpoint.sendMessage("{\"a\":\"unsubscribe\",\"v\":[" + order.getInstrumentId() + "]}");
                                continue;
                            }
                            if(!order.isSoftStopLossSignal() && closingPrice > superTrendIndicator73.getValue(barSeries.getEndIndex() - i)){
                                log.info("Soft Sl hit {}", indexCurrentPriceMap);
                                sendAlertToTelegram("Soft Sl hit  " + order.getInstrumentSymbol());
                                order.setSoftStopLossSignal(true);
                                orderRepository.save(order);
                            }
                            order.getSuperTrendValues().put("sup75", superTrendIndicator75.getValue(barSeries.getEndIndex() - i));
                            order.getSuperTrendValues().put("sup74", superTrendIndicator74.getValue(barSeries.getEndIndex() - i));
                            order.getSuperTrendValues().put("sup73", superTrendIndicator73.getValue(barSeries.getEndIndex() - i));
                            order.getSuperTrendValues().put("sup72", superTrendIndicator72.getValue(barSeries.getEndIndex() - i));
                            continue;
                        }

                        boolean currentCandleSignal = closingPrice > superTrendIndicator72.getValue(barSeries.getEndIndex() - i)
                                && closingPrice < superTrendIndicator73.getValue(barSeries.getEndIndex() - i)
                                && closingPrice < superTrendIndicator74.getValue(barSeries.getEndIndex() - i)
                                && closingPrice < superTrendIndicator75.getValue(barSeries.getEndIndex() - i);

                        if (currentCandleSignal) {
                            activeSignal.put(zi, barSeries.getBar(barSeries.getEndIndex() - i));
                        }
                    }
                }
            }
            for(ZerodhaInstrument zi : zerodhaInstrumentList){
                futureTasks.remove(zi);
            }
            zerodhaInstrumentList.clear();
        }

        StringBuilder sb = new StringBuilder();
        double bankNiftyPrice = indexCurrentPriceMap.get(IndexName.BANKNIFTY.name());
        double niftyPrice = indexCurrentPriceMap.get(IndexName.NIFTY.name());

        Optional<Map.Entry<ZerodhaInstrument, Bar>> bankNiftyTrade = activeSignal.entrySet().stream().filter(zerodhaInstrumentBarEntry -> zerodhaInstrumentBarEntry.getKey().getTradingsymbol().contains("BANKNIFTY")).min(Comparator.comparingDouble(i -> Math.abs(i.getKey().getStrike() - bankNiftyPrice)));
        Optional<Map.Entry<ZerodhaInstrument, Bar>> niftyTrade = activeSignal.entrySet().stream().filter(zerodhaInstrumentBarEntry -> !zerodhaInstrumentBarEntry.getKey().getTradingsymbol().contains("BANKNIFTY")).min(Comparator.comparingDouble(i -> Math.abs(i.getKey().getStrike() - niftyPrice)));

        if(bankNiftyTrade.isPresent() && bankNiftyTrade.get().getKey().getTradingsymbol().contains("BANKNIFTY")){
            activeTrades.put(IndexName.BANKNIFTY, placeOrder(bankNiftyTrade.get(), sb));
        }
        if(niftyTrade.isPresent() && !niftyTrade.get().getKey().getTradingsymbol().contains("BANKNIFTY")){
            activeTrades.put(IndexName.NIFTY, placeOrder(niftyTrade.get(), sb));
        }

        if(sb.toString().length() > 0) {
            sendAlertToTelegram(zerodhaTimeFrame.getKey() + "--Testing--" + "%0A" + sb.toString());
        }
    }

    public void sendAlertToTelegram(String message){
        sendToTelegram(message, "-680866934");
        sendToTelegram(message, "-638700096");
    }

    public Order placeOrder(Map.Entry<ZerodhaInstrument, Bar> entry, StringBuilder sb){
        sb.append("instrument: ");
        sb.append(entry.getKey().getTradingsymbol());
        sb.append(", close:");
        sb.append(entry.getValue().getClosePrice());
        sb.append("%0A");
        sb.append(", Candle Time: ");
        sb.append(entry.getValue().getEndTime());
        sb.append("%0A");

        Order order = new Order();
        order.setEntryPrice(entry.getValue().getClosePrice().doubleValue());
        order.setEntryTime(LocalDateTime.now());
        order.setCompleted(false);
        order.setInstrumentSymbol(entry.getKey().getTradingsymbol());
        order.setInstrumentId(entry.getKey().getId());
        indexCurrentPriceMap.put(order.getInstrumentId(), order.getEntryPrice());
        webSocketClientEndpoint.sendMessage("{\"a\":\"subscribe\",\"v\":["+order.getInstrumentId()+"]}");
        order = orderRepository.save(order);

        taskService.submitTask(new OpenOrderMonitor(order, orderRepository, indexCurrentPriceMap, this));
        return order;
    }


    private int validStrikePrice(double price, IndexName indexName, InstrumentType instrumentType){
        int optionGap = IndexName.NIFTY == indexName ? 50 : 100;
        int roundOffStrike = (int) (price/ optionGap) * optionGap;
        int abs = Math.abs(roundOffStrike - ((int) price));
        if(roundOffStrike == (long) price){
            return InstrumentType.CE == instrumentType ? roundOffStrike - optionGap : roundOffStrike + optionGap;
        }else{
            if((optionGap / 2) > abs){ // 1-25; 1-49
                return InstrumentType.CE == instrumentType ? roundOffStrike - optionGap : roundOffStrike + optionGap;
            }else{ // 25-49;50-99
                return InstrumentType.CE == instrumentType ? roundOffStrike : roundOffStrike + 2*optionGap;
            }
        }
    }

    private boolean isLastCandleComplete(ZerodhaTimeFrame zerodhaTimeFrame, ZerodhaCandle zerodhaCandle){
        Duration between = Duration.between(zerodhaCandle.getTimestamp(), OffsetDateTime.now());
        return between.toMinutes() >= zerodhaTimeFrame.getMinutes();
    }

    private Map<IndexName, Double> loadLtp() throws IOException {
        URL url = new URL("https://api.kite.trade/quote/ltp?i=NSE:NIFTY+BANK&i=NSE:NIFTY+50");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("authority", "kite.zerodha.com");
        con.setRequestProperty("accept-language", "en-US,en;q=0.9");
        con.setRequestProperty("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.67 Safari/537.36");
        con.setRequestProperty("authorization", "enctoken " + authToken);
        int responseCode = con.getResponseCode();
        String jsonResponse = null;
        if(responseCode == 200)
            jsonResponse = StreamUtils.convertStreamToString(con.getInputStream());
        return parseLtpResponseJson(jsonResponse);
    }

    private Map<IndexName, Double> parseLtpResponseJson(String jsonResponse){
        Map<IndexName, Double> map = new HashMap<>();
        JSONObject rootObject = new JSONObject(jsonResponse).getJSONObject("data");
        for (IndexName indexName : IndexName.values()) {
            map.put(indexName, rootObject.getJSONObject("NSE:" + indexName.getZerodhaInstrumentName()).getDouble("last_price"));
        }
        return map;
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

    private HistoricalData parseHistoricalData(HistoricalData data){
        List<ZerodhaCandle> zerodhaCandles = new ArrayList<>();
        data.setZerodhaCandleList(zerodhaCandles);
        for (List<String> candleData: data.getData().getCandles()) {
            ZerodhaCandle zerodhaCandle = new ZerodhaCandle();
            for(int i = 0; i < candleData.size(); i++){
                switch (ZerodhaCandleIndex.findEnum(i)){
                    case TIMESTAMP: {
                        zerodhaCandle.setTimestamp(OffsetDateTime.parse(candleData.get(i), offsetDateformatter));
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

    public void sendToTelegram(String message, String chatId) {
        String urlString = "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s";

        //Add Telegram token
        String apiToken = "1619143576:AAGfUm7ZC-bjCvggzBm0l0OIHM-mPjjt7K0";

        //Add chatId
        //String chatId = "672190792";
        //String chatId = "-680866934";

        urlString = String.format(urlString, apiToken, chatId, message);
        URL url;
        URLConnection conn;
        InputStream inputStream = null;
        try{
            url = new URL(urlString);
            conn = url.openConnection();
            inputStream = new BufferedInputStream(conn.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }
}
