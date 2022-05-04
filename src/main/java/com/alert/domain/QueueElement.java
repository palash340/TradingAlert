package com.alert.domain;

import lombok.Data;
import org.ta4j.core.BarSeries;

@Data
public class QueueElement {
    ZerodhaInstrument zerodhaInstrument;
    BarSeries barSeries;
}
