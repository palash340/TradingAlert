package com.alert;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;

public class STIndicator extends CachedIndicator {

    protected STIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Object calculate(int i) {
        return null;
    }
}
