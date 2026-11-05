package com.alphamind.investment;

/**
 * 价格所处档位的唯一判定口径，复核引擎与价格提醒共用。
 * 档位重叠时取更深的一档；档间、首档之上、第三档之下分别给出区域名，不推断档位。
 */
public final class PriceZones {
    public static final String ABOVE_FIRST = "ABOVE_FIRST";
    public static final String BETWEEN_BANDS = "BETWEEN_BANDS";
    public static final String BELOW_THIRD = "BELOW_THIRD";
    public static final String UNKNOWN = "UNKNOWN";

    private PriceZones() {}

    public record Zone(String name, int tier) {}

    public static Zone classify(PersonalModelDefinition.Asset asset, Double price) {
        if (price == null || !Double.isFinite(price) || price <= 0) return new Zone(UNKNOWN, 0);
        int tier = 0;
        for (var band : asset.bands()) if (price >= band.low() && price <= band.high()) tier = Math.max(tier, band.tier());
        if (tier > 0) return new Zone("TIER_" + tier, tier);
        var bands = asset.bands();
        if (price > bands.get(0).high()) return new Zone(ABOVE_FIRST, 0);
        if (price < bands.get(bands.size() - 1).low()) return new Zone(BELOW_THIRD, 0);
        return new Zone(BETWEEN_BANDS, 0);
    }

    /** 提醒用的深度：首档=1 … 第三档=3，跌破第三档=4；首档之上、档间和未知为 0（不单独提醒）。 */
    public static int depth(Zone zone) {
        return BELOW_THIRD.equals(zone.name()) ? 4 : zone.tier();
    }
}
