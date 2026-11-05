/** 个人模型价格提醒：价格首次进入更深的档位时生成，只提示去复核。 */
export interface PriceAlert {
  id: number;
  stockCode: string;
  stockName: string;
  fromZone: string;
  toZone: string;
  price: number;
  quotedAt: string | null;
  modelVersion: string;
  triggeredAt: string;
  acknowledgedAt: string | null;
}

export interface PriceAlertCheckSummary {
  checked: number;
  created: number;
  skippedStale: string[];
  failed: string[];
}
