package com.alert.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString(exclude = "data")
public class HistoricalData {
    String status;

    @JsonProperty("data")
    CandlesArray data;

    @JsonIgnore
    List<ZerodhaCandle> zerodhaCandleList;

    @Data
    @ToString
    public class CandlesArray{
        List<List<String>> candles;
    }
}



