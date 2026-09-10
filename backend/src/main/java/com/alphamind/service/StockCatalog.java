package com.alphamind.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A 股静态元数据目录。
 *
 * <p>这里只保存**身份信息**（代码、名称、行业、板块），这些属性在交易日内不会变化，
 * 适合内置。价格、涨跌幅等行情字段一律不在此处保存——它们必须来自真实行情源，
 * 否则会出现"用写死的价格算出精确评分"的伪装数据问题。
 *
 * @see com.alphamind.market.service.StockQuoteService 行情字段的唯一来源
 */
@Component
public class StockCatalog {

    private static final Map<String, StockMetadata> CATALOG = new LinkedHashMap<>();

    static {
        // ===== 白酒 =====
        add("600519", "贵州茅台",  "白酒", "上海主板");
        add("000858", "五粮液",    "白酒", "深圳主板");
        add("000568", "泸州老窖",  "白酒", "深圳主板");
        add("000596", "古井贡酒",  "白酒", "深圳主板");
        add("600809", "山西汾酒",  "白酒", "上海主板");
        // ===== 银行 =====
        add("000001", "平安银行",  "银行", "深圳主板");
        add("600036", "招商银行",  "银行", "上海主板");
        add("601288", "农业银行",  "银行", "上海主板");
        add("601398", "工商银行",  "银行", "上海主板");
        add("601939", "建设银行",  "银行", "上海主板");
        add("600016", "民生银行",  "银行", "上海主板");
        add("601166", "兴业银行",  "银行", "上海主板");
        // ===== 保险/金融 =====
        add("601318", "中国平安",  "保险", "上海主板");
        add("601601", "中国太保",  "保险", "上海主板");
        add("601628", "中国人寿",  "保险", "上海主板");
        add("600030", "中信证券",  "券商", "上海主板");
        add("000776", "广发证券",  "券商", "深圳主板");
        // ===== 家电/消费 =====
        add("000333", "美的集团",  "家电", "深圳主板");
        add("000651", "格力电器",  "家电", "深圳主板");
        add("002415", "海康威视",  "电子", "深圳主板");
        add("600690", "海尔智家",  "家电", "上海主板");
        // ===== 汽车/新能源 =====
        add("002594", "比亚迪",    "汽车", "深圳主板");
        add("300750", "宁德时代",  "新能源","创业板");
        add("601238", "广汽集团",  "汽车", "上海主板");
        add("600104", "上汽集团",  "汽车", "上海主板");
        add("601127", "小康股份",  "汽车", "上海主板");
        // ===== 医药 =====
        add("600276", "恒瑞医药",  "医药", "上海主板");
        add("600196", "复星医药",  "医药", "上海主板");
        add("300015", "爱尔眼科",  "医疗", "创业板");
        add("000661", "长春高新",  "医药", "深圳主板");
        add("002007", "华兰生物",  "医药", "深圳主板");
        // ===== 半导体/科技 =====
        add("688981", "中芯国际",  "半导体","科创板");
        add("603986", "兆易创新",  "半导体","上海主板");
        add("688012", "中微公司",  "半导体","科创板");
        add("002475", "立讯精密",  "电子", "深圳主板");
        add("002027", "分众传媒",  "传媒", "深圳主板");
        // ===== 互联网/软件 =====
        add("300059", "东方财富",  "互联网","创业板");
        add("688036", "传音控股",  "电子", "科创板");
        add("002230", "科大讯飞",  "人工智能","深圳主板");
        add("688111", "金山办公",  "软件", "科创板");
        // ===== 房地产/建材 =====
        add("000002", "万科A",    "房地产","深圳主板");
        add("600048", "保利发展",  "房地产","上海主板");
        add("000786", "北新建材",  "建材", "深圳主板");
        // ===== 能源/化工 =====
        add("600028", "中国石化",  "石油", "上海主板");
        add("601857", "中国石油",  "石油", "上海主板");
        add("600547", "山东黄金",  "黄金", "上海主板");
        add("601088", "中国神华",  "煤炭", "上海主板");
        add("000895", "双汇发展",  "食品", "深圳主板");
        // ===== 钢铁/有色 =====
        add("600019", "宝山钢铁",  "钢铁", "上海主板");
        add("600362", "江西铜业",  "有色", "上海主板");
        add("002460", "赣锋锂业",  "有色", "深圳主板");
        // ===== 食品饮料 =====
        add("600887", "伊利股份",  "食品", "上海主板");
        add("002714", "牧原股份",  "农业", "深圳主板");
        add("300498", "温氏股份",  "农业", "创业板");
        // ===== 航空/交通 =====
        add("601111", "中国国航",  "航空", "上海主板");
        add("600115", "东方航空",  "航空", "上海主板");
        add("601808", "中海油服",  "石油服务","上海主板");
        // ===== 军工 =====
        add("600760", "中航沈飞",  "军工", "上海主板");
        add("000768", "中航西飞",  "军工", "深圳主板");
        // ===== 电力/公用事业 =====
        add("600900", "长江电力",  "电力", "上海主板");
        add("002380", "科远智慧",  "电力", "深圳主板");
    }

    private static void add(String code, String name, String industry, String market) {
        CATALOG.put(code, new StockMetadata(code, name, industry, market));
    }

    /** 全量股票池，顺序稳定。 */
    public Collection<StockMetadata> all() {
        return CATALOG.values();
    }

    public Optional<StockMetadata> find(String code) {
        return Optional.ofNullable(CATALOG.get(code));
    }

    /** 按代码、名称或行业做大小写不敏感的子串匹配。 */
    public List<StockMetadata> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<StockMetadata> matches = new ArrayList<>();
        for (StockMetadata stock : CATALOG.values()) {
            if (stock.code().contains(needle)
                    || stock.name().toLowerCase(Locale.ROOT).contains(needle)
                    || stock.industry().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(stock);
            }
        }
        return matches;
    }

    /** 静态身份信息，不含任何行情字段。 */
    public record StockMetadata(String code, String name, String industry, String market) {}
}
