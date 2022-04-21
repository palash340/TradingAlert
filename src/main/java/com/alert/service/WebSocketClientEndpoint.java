package com.alert.service;

import com.alert.domain.IndexName;
import com.zerodhatech.models.Depth;
import com.zerodhatech.models.Tick;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.websocket.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Log4j2
@Service
@ClientEndpoint
public class WebSocketClientEndpoint {

    @Value("${zerodha.authToken}")
    String authToken;

    @Value("${zerodha.username}")
    String username;

    private final String mSubscribe = "subscribe";
    private final String mUnSubscribe = "unsubscribe";
    private final String mSetMode = "mode";
    public static String modeFull = "full";
    public static String modeQuote = "quote";
    public static String modeLTP = "ltp";

    Session userSession = null;
    private MessageHandler messageHandler;

    @Autowired
    private ZerodhaService zerodhaService;

    @PostConstruct
    public void init(){
        try {
            StringBuilder url = new StringBuilder();
            url.append("wss://ws.zerodha.com/?");
            url.append("api_key=kitefront");
            url.append("&user_id=");
            url.append(username);
            url.append("&enctoken=");
            url.append(URLEncoder.encode(authToken, StandardCharsets.UTF_8.toString()));
            url.append("&uid=");
            url.append("1649839961626");
            url.append("&user-agent=kite3-web&version=2.9.11");

            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, new URI(url.toString()));

            addMessageHandler(new MessageHandler() {
                @Override
                public void handleMessage(String message) {
                    System.out.println(message);
                }
            });

            sendMessage("{\"a\":\"subscribe\",\"v\":[256265,260105]}");

        } catch (DeploymentException e) {
            log.error(e);
        } catch (UnsupportedEncodingException e) {
            log.error(e);
        } catch (IOException e) {
            log.error(e);
        } catch (URISyntaxException e) {
            log.error(e);
        }
    }

    /**
     * Callback hook for Connection open events.
     *
     * @param userSession the userSession which is opened.
     */
    @OnOpen
    public void onOpen(Session userSession) {
        log.info("opening websocket");
        this.userSession = userSession;
    }

    /**
     * Callback hook for Connection close events.
     *
     * @param userSession the userSession which is getting closed.
     * @param reason      the reason for connection close
     */
    @OnClose
    public void onClose(Session userSession, CloseReason reason) {
        log.info("closing websocket");
        this.userSession = null;
    }

    /**
     * Callback hook for Message Events. This method will be invoked when a client send a message.
     *
     * @param message The text message
     */
    @OnMessage
    public void onMessage(String message) {
        if (this.messageHandler != null) {
            this.messageHandler.handleMessage(message);
        }
    }

    @OnMessage
    public void onMessage(ByteBuffer bytes) {
        ArrayList<Tick> tickerData = parseBinary(bytes.array());
        Map<String, Double> indexCurrentPriceMap = zerodhaService.getIndexCurrentPriceMap();
        for(Tick tick : tickerData ) {
            switch ((int) tick.getInstrumentToken()){
                case 256265 :
                    indexCurrentPriceMap.put(IndexName.NIFTY.name(), tick.getLastTradedPrice());
                    break;
                case 260105 :
                    indexCurrentPriceMap.put(IndexName.BANKNIFTY.name(), tick.getLastTradedPrice());
                    break;
                default:
                    indexCurrentPriceMap.put(String.valueOf(tick.getInstrumentToken()), tick.getLastTradedPrice());
                    break;
            }
        }

    }

    private Tick getQuoteData(byte[] bin, int x, int dec1) {
        Tick tick2 = new Tick();
        tick2.setMode(modeQuote);
        tick2.setInstrumentToken((long)x);
        double lastTradedPrice = this.convertToDouble(this.getBytes(bin, 4, 8)) / (double)dec1;
        tick2.setLastTradedPrice(lastTradedPrice);
        tick2.setLastTradedQuantity(this.convertToDouble(this.getBytes(bin, 8, 12)));
        tick2.setAverageTradePrice(this.convertToDouble(this.getBytes(bin, 12, 16)) / (double)dec1);
        tick2.setVolumeTradedToday(this.convertToDouble(this.getBytes(bin, 16, 20)));
        tick2.setTotalBuyQuantity(this.convertToDouble(this.getBytes(bin, 20, 24)));
        tick2.setTotalSellQuantity(this.convertToDouble(this.getBytes(bin, 24, 28)));
        tick2.setOpenPrice(this.convertToDouble(this.getBytes(bin, 28, 32)) / (double)dec1);
        tick2.setHighPrice(this.convertToDouble(this.getBytes(bin, 32, 36)) / (double)dec1);
        tick2.setLowPrice(this.convertToDouble(this.getBytes(bin, 36, 40)) / (double)dec1);
        double closePrice = this.convertToDouble(this.getBytes(bin, 40, 44)) / (double)dec1;
        tick2.setClosePrice(closePrice);
        setChangeForTick(tick2, lastTradedPrice, closePrice);
        return tick2;
    }

    private ArrayList<Tick> parseBinary(byte[] binaryPackets) {
        ArrayList<Tick> ticks = new ArrayList();
        ArrayList<byte[]> packets = splitPackets(binaryPackets);

        for(int i = 0; i < packets.size(); ++i) {
            byte[] bin = (byte[])packets.get(i);
            byte[] t = Arrays.copyOfRange(bin, 0, 4);
            int x = ByteBuffer.wrap(t).getInt();
            int segment = x & 255;
            int dec1 = segment == 3 ? 10000000 : 100;
            Tick tick;
            if (bin.length == 8) {
                tick = getLtpQuote(bin, x, dec1);
                ticks.add(tick);
            } else if (bin.length != 28 && bin.length != 32) {
                if (bin.length == 44) {
                    tick = getQuoteData(bin, x, dec1);
                    ticks.add(tick);
                } else if (bin.length == 184) {
                    tick = getQuoteData(bin, x, dec1);
                    tick.setMode(modeFull);
                    ticks.add(getFullData(bin, dec1, tick));
                }
            } else {
                tick = getIndeciesData(bin, x);
                ticks.add(tick);
            }
        }

        return ticks;
    }

    private Tick getIndeciesData(byte[] bin, int x) {
        int dec = 100;
        Tick tick = new Tick();
        tick.setMode(modeFull);
        tick.setTradable(false);
        tick.setInstrumentToken((long)x);
        tick.setLastTradedPrice(this.convertToDouble(this.getBytes(bin, 4, 8)) / (double)dec);
        tick.setHighPrice(this.convertToDouble(this.getBytes(bin, 8, 12)) / (double)dec);
        tick.setLowPrice(this.convertToDouble(this.getBytes(bin, 12, 16)) / (double)dec);
        tick.setOpenPrice(this.convertToDouble(this.getBytes(bin, 16, 20)) / (double)dec);
        tick.setClosePrice(this.convertToDouble(this.getBytes(bin, 20, 24)) / (double)dec);
        tick.setNetPriceChangeFromClosingPrice(this.convertToDouble(this.getBytes(bin, 24, 28)) / (double)dec);
        if (bin.length > 28) {
            long tickTimeStamp = convertToLong(this.getBytes(bin, 28, 32)) * 1000L;
            if (this.isValidDate(tickTimeStamp)) {
                tick.setTickTimestamp(new Date(tickTimeStamp));
            } else {
                tick.setTickTimestamp((Date)null);
            }
        }

        return tick;
    }

    private Tick getFullData(byte[] bin, int dec, Tick tick) {
        long lastTradedtime = this.convertToLong(this.getBytes(bin, 44, 48)) * 1000L;
        if (this.isValidDate(lastTradedtime)) {
            tick.setLastTradedTime(new Date(lastTradedtime));
        } else {
            tick.setLastTradedTime((Date)null);
        }

        tick.setOi(this.convertToDouble(this.getBytes(bin, 48, 52)));
        tick.setOpenInterestDayHigh(this.convertToDouble(this.getBytes(bin, 52, 56)));
        tick.setOpenInterestDayLow(this.convertToDouble(this.getBytes(bin, 56, 60)));
        long tickTimeStamp = this.convertToLong(this.getBytes(bin, 60, 64)) * 1000L;
        if (isValidDate(tickTimeStamp)) {
            tick.setTickTimestamp(new Date(tickTimeStamp));
        } else {
            tick.setTickTimestamp((Date)null);
        }

        tick.setMarketDepth(getDepthData(bin, dec, 64, 184));
        return tick;
    }

    private Map<String, ArrayList<Depth>> getDepthData(byte[] bin, int dec, int start, int end) {
        byte[] depthBytes = getBytes(bin, start, end);
        int s = 0;
        ArrayList<Depth> buy = new ArrayList();
        ArrayList<Depth> sell = new ArrayList();

        for(int k = 0; k < 10; ++k) {
            s = k * 12;
            Depth depth = new Depth();
            depth.setQuantity((int)this.convertToDouble(this.getBytes(depthBytes, s, s + 4)));
            depth.setPrice(this.convertToDouble(this.getBytes(depthBytes, s + 4, s + 8)) / (double)dec);
            depth.setOrders((int)this.convertToDouble(this.getBytes(depthBytes, s + 8, s + 10)));
            if (k < 5) {
                buy.add(depth);
            } else {
                sell.add(depth);
            }
        }

        Map<String, ArrayList<Depth>> depthMap = new HashMap();
        depthMap.put("buy", buy);
        depthMap.put("sell", sell);
        return depthMap;
    }


    private boolean isValidDate(long date) {
        if (date <= 0L) {
            return false;
        } else {
            Calendar calendar = Calendar.getInstance();
            calendar.setLenient(false);
            calendar.setTimeInMillis(date);

            try {
                calendar.getTime();
                return true;
            } catch (Exception var5) {
                return false;
            }
        }
    }

    private ArrayList<byte[]> splitPackets(byte[] bin) {
        ArrayList<byte[]> packets = new ArrayList();
        int noOfPackets = getLengthFromByteArray(getBytes(bin, 0, 2));
        int j = 2;

        for(int i = 0; i < noOfPackets; ++i) {
            int sizeOfPacket = getLengthFromByteArray(getBytes(bin, j, j + 2));
            byte[] packet = Arrays.copyOfRange(bin, j + 2, j + 2 + sizeOfPacket);
            packets.add(packet);
            j = j + 2 + sizeOfPacket;
        }

        return packets;
    }

    private int getLengthFromByteArray(byte[] bin) {
        ByteBuffer bb = ByteBuffer.wrap(bin);
        bb.order(ByteOrder.BIG_ENDIAN);
        return bb.getShort();
    }

    private byte[] getBytes(byte[] bin, int start, int end) {
        return Arrays.copyOfRange(bin, start, end);
    }

    private long convertToLong(byte[] bin) {
        ByteBuffer bb = ByteBuffer.wrap(bin);
        bb.order(ByteOrder.BIG_ENDIAN);
        return (long)bb.getInt();
    }

    private Tick getLtpQuote(byte[] bin, int x, int dec1) {
        Tick tick1 = new Tick();
        tick1.setMode(modeLTP);
        tick1.setTradable(true);
        tick1.setInstrumentToken(x);
        tick1.setLastTradedPrice(convertToDouble(this.getBytes(bin, 4, 8)) / (double)dec1);
        return tick1;
    }

    private double convertToDouble(byte[] bin) {
        ByteBuffer bb = ByteBuffer.wrap(bin);
        bb.order(ByteOrder.BIG_ENDIAN);
        if (bin.length < 4) {
            return (double)bb.getShort();
        } else {
            return bin.length < 8 ? (double)bb.getInt() : bb.getDouble();
        }
    }

    private void setChangeForTick(Tick tick, double lastTradedPrice, double closePrice) {
        if (closePrice != 0.0D) {
            tick.setNetPriceChangeFromClosingPrice((lastTradedPrice - closePrice) * 100.0D / closePrice);
        } else {
            tick.setNetPriceChangeFromClosingPrice(0.0D);
        }

    }

    /**
     * register message handler
     *
     * @param msgHandler
     */
    public void addMessageHandler(MessageHandler msgHandler) {
        this.messageHandler = msgHandler;
    }

    /**
     * Send a message.
     *
     * @param message
     */
    public void sendMessage(String message) {
        this.userSession.getAsyncRemote().sendText(message);
    }

    public static interface MessageHandler {
        public void handleMessage(String message);
    }
}


