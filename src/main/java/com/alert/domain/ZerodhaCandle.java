package com.alert.domain;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ZerodhaCandle {

    OffsetDateTime timestamp;

    Double open;

    Double high;

    Double low;

    Double close;

    Integer volume;

    Integer oi;
}
