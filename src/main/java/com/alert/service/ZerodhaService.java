package com.alert.service;

import com.alert.dao.OrderRepository;
import com.alert.domain.IndexName;
import com.alert.domain.InstrumentType;
import com.alert.domain.ZerodhaInstrument;
import com.alert.domain.ZerodhaTimeFrame;
import com.alert.entity.Order;
import com.alert.service.calc.SuperTrendIndicator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.*;
import org.ta4j.core.analysis.criteria.pnl.GrossProfitCriterion;
import org.ta4j.core.analysis.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.concurrent.ExecutorService;

@Data
@Log4j2
@Service
public class ZerodhaService {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private HistoricalDataPuller historicalDataPuller;

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

    private List<CSVRecord> indexInstruments = new ArrayList<>();
    private List<CSVRecord> cmInstruments = new ArrayList<>();

    static String DATE_FORMAT = "YYYY-MM-dd"; //2021-03-04

    DateTimeFormatter offsetDateFormatter = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .appendOffset("+HHMM","Z")
            .toFormatter();

    static DateTimeFormatter dtf = DateTimeFormatter.ofPattern(DATE_FORMAT);

    @PostConstruct
    public void init(){
//        loadOpenOrder();
        try {
            File nfo = loadInstruments("NFO");
            setCurrentExpiry(nfo);
            loadInstruments("NSE");
        } catch (IOException e) {
            log.error("Error while loading instrument ", e);
        }
    }

    private void loadOpenOrder(){
        List<Order> openOrder = orderRepository.findOpenOrder();
        for (Order order : openOrder) {
            webSocketClientEndpoint.sendMessage("{\"a\":\"subscribe\",\"v\":["+order.getInstrumentId()+"]}");
            if(order.getInstrumentSymbol().contains("BANKNIFTY")){
                activeTrades.put(IndexName.BANKNIFTY, order);
            }else{
                activeTrades.put(IndexName.NIFTY, order);
            }
            indexCurrentPriceMap.put(order.getInstrumentId(), order.getEntryPrice());
            //taskService.submitTask(new OpenOrderMonitor(order, orderRepository, indexCurrentPriceMap, this));
        }
    }

    private void setCurrentExpiry(File nfo) throws IOException {
        if(!Objects.isNull(nfo) && nfo.exists() && StringUtils.isBlank(currentExpiry)){
            LocalDate date = null;
            LocalDate temp;
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .withHeader("instrument_token","exchange_token","tradingsymbol","name","last_price","expiry","strike","tick_size","lot_size","instrument_type","segment","exchange").parse(new InputStreamReader(Files.newInputStream(nfo.toPath())));
            for (CSVRecord record : records) {
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

    private File loadInstruments(String scripts) throws IOException {
        String fileName = scripts + "_" + dtf.format(LocalDate.now()) + ".csv";
        File file = new File(fileName);
        if(!file.exists()){
            URL url = new URL("https://api.kite.trade/instruments/" + scripts);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            int responseCode = con.getResponseCode();
            if(responseCode == 200)
                FileUtils.copyInputStreamToFile(con.getInputStream(), file);
        }
        if(file.exists()) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .withHeader("instrument_token", "exchange_token", "tradingsymbol", "name", "last_price", "expiry", "strike", "tick_size", "lot_size", "instrument_type", "segment", "exchange").parse(new InputStreamReader(new FileInputStream(file)));
            for (CSVRecord record : records) {
                if ("expiry".equals(record.get("expiry")) || indexListToBeIgnored.contains(record.get("name"))) {
                    continue;
                }
                List<CSVRecord> recordList = scripts.equals("NFO") ? this.indexInstruments : this.cmInstruments;
                recordList.add(record);
            }
        }
        return file;
    }


    public void generateSignals(ZerodhaTimeFrame zerodhaTimeFrame) throws IOException {
        log.info("priceMap {}", indexCurrentPriceMap);
        GrossReturnCriterion grossReturnCriterion = new GrossReturnCriterion();
       // List<ZerodhaInstrument> validInstruments = getInstrumentsFromSymbol(Arrays.asList("INDUSINDBK","COALINDIA","POWERGRID","TATASTEEL","ITC","BPCL","HDFC","HDFCBANK","SHREECEM","NTPC","UPL","ULTRACEMCO","JSWSTEEL","HINDALCO","BHARTIARTL","NESTLEIND","ADANIPORTS","BAJAJFINSV","M&M","DIVISLAB","AXISBANK","TATACONSUM","CIPLA","DRREDDY","TCS","ICICIBANK","HINDUNILVR","RELIANCE","BRITANNIA","HDFCLIFE","SUNPHARMA","GRASIM","BAJFINANCE","LT","HEROMOTOCO","KOTAKBANK","SBIN","TATAMOTORS","HCLTECH","MARUTI","ASIANPAINT","INFY","ONGC","TECHM","SBILIFE","WIPRO","BAJAJ-AUTO","TITAN","APOLLOHOSP","EICHERMOT"));
        List<ZerodhaInstrument> validInstruments = getInstrumentsFromSymbol(Arrays.asList("TCS"));
        for (ZerodhaInstrument validInstrument : validInstruments) {
            BarSeries historicalBarSeries = historicalDataPuller.getHistoricalBarSeries(validInstrument, ZerodhaTimeFrame.FIFTEEN_MINUTE, 180);
            Strategy strategy = buildStrategy(historicalBarSeries);
            BarSeriesManager seriesManager = new BarSeriesManager(historicalBarSeries);
            TradingRecord tradingRecord = seriesManager.run(strategy, Trade.TradeType.BUY);
            double profit = 0;
            for (Position position : tradingRecord.getPositions()) {
                profit = profit + position.getGrossProfit().doubleValue();
            }
            System.out.println(validInstrument.getTradingsymbol() + " : " + profit);
            System.out.println(validInstrument.getTradingsymbol() + " : " + grossReturnCriterion.calculate(historicalBarSeries, tradingRecord));
            log.info("tradingRecord {}", tradingRecord);
        }
    }

    private List<ZerodhaInstrument> getInstrumentsFromSymbol(List<String> symbols){
        List<ZerodhaInstrument> list = new ArrayList<>();
        for (CSVRecord cmInstrument : cmInstruments) {
            if(symbols.contains(cmInstrument.get("tradingsymbol"))) {
                ZerodhaInstrument zi = new ZerodhaInstrument();
                zi.setId(cmInstrument.get("instrument_token"));
                zi.setInstrumentType(InstrumentType.EQ);
                zi.setName(cmInstrument.get("name"));
                zi.setStrike(0);
                zi.setTradingsymbol(cmInstrument.get("tradingsymbol"));
                list.add(zi);
            }
        }
        return list;
    }
    private List<ZerodhaInstrument> getValidInstruments(){
        List<ZerodhaInstrument> list = new ArrayList<>();
        for (CSVRecord record : cmInstruments) {
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
                        list.add(zi);
                    }
                }
            }
        }
        return list;
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

       // taskService.submitTask(new OpenOrderMonitor(order, orderRepository, indexCurrentPriceMap, this));
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
    public Strategy buildStrategy(BarSeries barSeries){
        if(barSeries == null)
            return null;

        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(barSeries);
        /*EMAIndicator ema55 = new EMAIndicator(closePriceIndicator,55);
        EMAIndicator ema = new EMAIndicator(closePriceIndicator,55);
        return new BaseStrategy(new OverIndicatorRule(ema55, closePriceIndicator), new UnderIndicatorRule(ema, closePriceIndicator));*/

        SMAIndicator sma;
        ATRIndicator atr;
        EMAIndicator ema13 = new EMAIndicator(closePriceIndicator,13);
        EMAIndicator ema21 = new EMAIndicator(closePriceIndicator,100);
        RSIIndicator rsi14 = new RSIIndicator(closePriceIndicator, 14);
        //RSIIndicator rsi50 = new RSIIndicator(closePriceIndicator, 50);
        SuperTrendIndicator sup73 = new SuperTrendIndicator(barSeries, 7.0, 3);
        return new BaseStrategy(new UnderIndicatorRule(rsi14, 50), new CrossedDownIndicatorRule(ema13, ema21));
    }

    public void sendToTelegram(String message, String chatId) {
        String urlString = "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s";

        //Add Telegram token
        String apiToken = "1619143576:AAGfUm7ZC-bjCvggzBm0l0OIHM-mPjjt7K0";

        urlString = String.format(urlString, apiToken, chatId, message);
        URL url;
        URLConnection conn;
        InputStream inputStream = null;
        try{
            url = new URL(urlString);
            conn = url.openConnection();
            inputStream = new BufferedInputStream(conn.getInputStream());
        } catch (IOException e) {
            log.error("Error while sending message to telegram ", e);
        }finally {
            if(inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.warn("Error while closing stream", e);
                }
            }

        }
    }
}
