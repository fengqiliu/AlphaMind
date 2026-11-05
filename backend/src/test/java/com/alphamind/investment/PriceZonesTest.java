package com.alphamind.investment;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PriceZonesTest {
    public static final PersonalModelDefinition.Asset ASSET = new PersonalModelDefinition.Asset("600036", "招商银行", "银行", "银行",
            List.of(new PersonalModelDefinition.Band(1, 37, 39), new PersonalModelDefinition.Band(2, 34, 36),
                    new PersonalModelDefinition.Band(3, 31, 33)), "", "", "", Map.of(), false);

    @Test
    void classifiesEveryRegion() {
        assertEquals("ABOVE_FIRST", PriceZones.classify(ASSET, 40.0).name());
        assertEquals(new PriceZones.Zone("TIER_1", 1), PriceZones.classify(ASSET, 39.0));
        assertEquals("BETWEEN_BANDS", PriceZones.classify(ASSET, 36.5).name());
        assertEquals(new PriceZones.Zone("TIER_3", 3), PriceZones.classify(ASSET, 31.0));
        assertEquals("BELOW_THIRD", PriceZones.classify(ASSET, 30.99).name());
        assertEquals("UNKNOWN", PriceZones.classify(ASSET, null).name());
        assertEquals("UNKNOWN", PriceZones.classify(ASSET, Double.NaN).name());
    }

    @Test
    void depthOrdersTiersAndBelowThird() {
        assertEquals(0, PriceZones.depth(PriceZones.classify(ASSET, 36.5)));
        assertEquals(2, PriceZones.depth(PriceZones.classify(ASSET, 35.0)));
        assertEquals(4, PriceZones.depth(PriceZones.classify(ASSET, 20.0)));
    }
}
