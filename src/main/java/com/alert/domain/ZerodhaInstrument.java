package com.alert.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;

@Data
@EqualsAndHashCode
@ToString
public class ZerodhaInstrument {
    String id;
    String tradingsymbol;
    String name;
    LocalDate expiry;
    long strike;
    InstrumentType instrumentType;
}
