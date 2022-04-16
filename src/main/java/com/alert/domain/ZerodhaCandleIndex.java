package com.alert.domain;

import java.util.Arrays;

public enum ZerodhaCandleIndex {
    TIMESTAMP(0),
    OPEN(1),
    HIGH(2),
    LOW(3),
    CLOSE(4),
    VOLUME(5),
    OI(6);

    public int index;

    ZerodhaCandleIndex(int index){
        this.index = index;
    }

    public int getIndex(){
        return index;
    }

    public static ZerodhaCandleIndex findEnum(int index){
        return Arrays.stream(ZerodhaCandleIndex.values()).filter(zci -> zci.getIndex() == index).findFirst().get();
    }
}
