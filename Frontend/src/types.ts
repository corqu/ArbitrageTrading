export interface KimchPremium {
  symbol: string;
  domesticExchange: string;
  foreignExchange: string;
  ratio: number;
  fundingRate: number | null;
  adjustedApr: number | null;
  liquidationPrice: number | null;
  tradeVolume: number | null;
  time?: string;
}

export interface BotStatus {
  [symbol: string]: boolean;
}

export interface TradingRequest {
  symbol: string;
  action: 'START' | 'STOP';
}
