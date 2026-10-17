package com.alphamind.market.provider;

import com.alphamind.market.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.time.*;
import java.util.List;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.junit.jupiter.api.Assertions.*;

class EtfMarketProviderTest {
    @Test void shanghaiEtfUsesShanghaiSymbolsForQuoteAndHistory() {
        var rest = new RestTemplate(); var server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo(containsString("sh517400"))).andRespond(withSuccess("",org.springframework.http.MediaType.TEXT_PLAIN));
        new SinaQuoteMarketDataProvider(rest).fetchQuote("517400","黄金股ETF国泰");
        server.verify();
        var historyRest = new RestTemplate(); var historyServer = MockRestServiceServer.bindTo(historyRest).build();
        historyServer.expect(requestTo(containsString("secid=1.517400"))).andRespond(withSuccess("{\"data\":{\"klines\":[]}}",org.springframework.http.MediaType.APPLICATION_JSON));
        new EastmoneyKlineMarketDataProvider(historyRest,new ObjectMapper()).fetchDailyKlines("517400",60);
        historyServer.verify();
    }
    @Test void etfPriceAndChangeKeepThreeDecimalPrecision() {
        var quote = new MarketQuote("517400","ETF",1.67,1.67,1.675,1.68,1.66,100,167.5,LocalDateTime.now());
        var snapshot = new MarketDataSnapshot("517400","ETF",quote,List.of(),"fixture","fixture",Instant.now());
        var dto = MarketDataMapper.toDTO(new MarketDataResolution(snapshot,MarketDataResolution.Status.LIVE,0,null));
        assertEquals(1.675,dto.getCurrentPrice()); assertEquals(0.005,dto.getChange()); assertEquals(0.30,dto.getChangePercent());
    }
}
