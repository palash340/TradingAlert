package com.alert.service;

import com.alert.domain.ZerodhaInstrument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@RunWith(JUnitPlatform.class)
@ExtendWith(MockitoExtension.class)
public class ZerodhaServiceTest {

    @InjectMocks
    private ZerodhaService zerodhaService;

    @Test
    public void testHistoricalData() throws IOException {
        ZerodhaInstrument zerodhaInstrument = new ZerodhaInstrument();
        zerodhaService.setAuthToken("3zF37t4pMkgnBwgCcNAV5k7Yl1+/D4xAoGYknkVEZb/5YnHrX+CVpJ3Cn/q9cgYAi0AGuKa02smn+ObTcIEgf/WPH7i+RrM9w01OFrxjuQB5S03BBUzD4A==");

        List<String> list = Arrays.asList("11546626");
        for (String id: list) {
            zerodhaInstrument.setId(id);
            //String jsonString = zerodhaService.getHistoricalData1(zerodhaInstrument, ZerodhaTimeFrame.ONE_MINUTE, 10);
            //FileUtils.writeStringToFile(new File(id + ".json"), jsonString);
        }
    }
}
