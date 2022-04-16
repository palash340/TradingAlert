package com.alert.service.calc;

import com.alert.domain.ZerodhaCandle;
import com.alert.domain.ZerodhaCandleIndex;

public class CalculationUtil {
    public static double getData(ZerodhaCandle zerodhaCandle, ZerodhaCandleIndex candleIndex){
        switch (candleIndex){
            case HIGH:
                return zerodhaCandle.getHigh();
            case OPEN:
                return zerodhaCandle.getOpen();
            case LOW:
                return zerodhaCandle.getLow();
            case CLOSE:
                return zerodhaCandle.getClose();
            case VOLUME:
                return zerodhaCandle.getVolume();
            case OI:
                return zerodhaCandle.getOi();
        }
        return 0.0d;
    }
}
