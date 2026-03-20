export interface KimchPremium {
  symbol: string;
  domesticExchange: string;
  foreignExchange: string;
  ratio: number;
  standardRatio?: number | null;
  entryRatio?: number | null;
  exitRatio?: number | null;
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

export interface SubscribedBot {
  id?: number;
  symbol: string;
  isActive: boolean;
  entryKimp: number;
  exitKimp: number;
  amountKrw: number;
  leverage: number;
  stopLossPercent: number;
  takeProfitPercent: number;
  domesticExchange: string;
  foreignExchange: string;
}
