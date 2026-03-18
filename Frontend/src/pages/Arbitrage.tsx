import React, { useState, useMemo } from 'react';
import axios from 'axios';
import { 
  RefreshCw, BarChart3, ChevronUp, Target, Calculator, Search
} from 'lucide-react';
import type { KimchPremium } from '../types';

interface ArbitrageProps {
  kimpList: KimchPremium[];
  selectedDomesticExchange: 'UPBIT' | 'BITHUMB';
  setSelectedDomesticExchange: (ex: 'UPBIT' | 'BITHUMB') => void;
  selectedForeignExchange: 'BINANCE' | 'BYBIT';
  setSelectedForeignExchange: (ex: 'BINANCE' | 'BYBIT') => void;
  isLoggedIn: boolean;
  connectedExchanges: string[];
}

const Arbitrage: React.FC<ArbitrageProps> = ({ 
  kimpList, 
  selectedDomesticExchange, setSelectedDomesticExchange,
  selectedForeignExchange, setSelectedForeignExchange,
  isLoggedIn,
  connectedExchanges
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);
  const [globalEntryKimp, setGlobalEntryKimp] = useState<string | number>(0.5);
  const [globalExitKimp, setGlobalExitKimp] = useState<string | number>(2.0);
  const [amountKrw, setAmountKrw] = useState<number>(1000000); 
  const [leverage, setLeverage] = useState<number>(3); 
  const [stopLossPercent, setStopLossPercent] = useState<number>(5.0);
  const [takeProfitPercent, setTakeProfitPercent] = useState<number>(10.0);
  const [backtestResults, setBacktestResults] = useState<{[key: string]: any}>({});
  const [loadingBacktest, setLoadingBacktest] = useState<{[key: string]: boolean}>({});
  const [botStatus, setBotStatus] = useState<{[key: string]: boolean}>({});
  const [tradingLoading, setTradingLoading] = useState<{[key: string]: boolean}>({});

  // 숫자 포맷팅 함수 (콤마 추가)
  const formatNumber = (num: number) => {
    return num.toLocaleString('ko-KR');
  };

  // 콤마 제거 후 숫자로 변환
  const parseNumber = (val: string) => {
    return parseInt(val.replace(/[^0-9]/g, '')) || 0;
  };

  const fetchBotStatus = async () => {
    try {
      const res = await axios.get('/api/trading/status');
      setBotStatus(res.data);
    } catch (e) {}
  };

  React.useEffect(() => {
    fetchBotStatus();
    const timer = setInterval(fetchBotStatus, 3000);
    return () => clearInterval(timer);
  }, []);

  const runAnalysis = async (symbol: string) => {
    setLoadingBacktest(prev => ({ ...prev, [symbol]: true }));
    try {
      const foreignEx = selectedForeignExchange === 'BINANCE' ? 'BINANCE_FUTURES' : 'BYBIT_FUTURES';
      const response = await axios.get(`/api/arbitrage/backtest`, {
        params: { 
          symbol, 
          entryKimp: globalEntryKimp, 
          exitKimp: globalExitKimp, 
          range: '-30d',
          domesticExchange: 'AVERAGE',
          foreignExchange: foreignEx
        }
      });
      setBacktestResults(prev => ({ ...prev, [symbol]: response.data }));
    } catch (error) {
      console.error('분석 실패');
    } finally {
      setLoadingBacktest(prev => ({ ...prev, [symbol]: false }));
    }
  };

  const handleToggleBot = async (symbol: string, action: 'START' | 'STOP' | 'START_AUTO') => {
    if (!isLoggedIn) {
      if (confirm('로그인이 필요한 서비스입니다. 로그인 페이지로 이동하시겠습니까?')) {
        window.location.href = '/auth';
      }
      return;
    }

    if (action !== 'STOP') {
      const domesticMatch = connectedExchanges.includes(selectedDomesticExchange);
      const foreignMatch = connectedExchanges.includes(selectedForeignExchange);

      if (!domesticMatch || !foreignMatch) {
        let missing = !domesticMatch ? selectedDomesticExchange : '';
        if (!foreignMatch) missing += (missing ? ' 및 ' : '') + selectedForeignExchange;
        
        if (confirm(`${missing} API 키가 연동되어 있지 않습니다. 마이페이지에서 연동하시겠습니까?`)) {
          window.location.href = '/mypage';
        }
        return;
      }
    }

    const key = `${symbol}:${selectedDomesticExchange}:${selectedForeignExchange}`;
    setTradingLoading(prev => ({ ...prev, [key]: true }));
    try {
      const endpoint = action === 'START_AUTO' ? '/api/user-bots' : '/api/trading/execute';
      const response = await axios.post(endpoint, {
        symbol,
        domesticExchange: selectedDomesticExchange,
        foreignExchange: selectedForeignExchange,
        amountKrw,
        leverage,
        entryKimp: parseFloat(globalEntryKimp.toString()),
        exitKimp: parseFloat(globalExitKimp.toString()),
        stopLossPercent,
        takeProfitPercent,
        action
      });
      const message = typeof response.data === 'string' ? response.data : response.data.message || '요청이 완료되었습니다.';
      alert(message);
      fetchBotStatus();
    } catch (error: any) {
      alert(error.response?.data || '매매 요청 실패');
    } finally {
      setTradingLoading(prev => ({ ...prev, [key]: false }));
    }
  };

  const isBotActive = (symbol: string) => {
    const key = `${symbol.toUpperCase()}:${selectedDomesticExchange}:${selectedForeignExchange === 'BINANCE' ? 'BINANCE_FUTURES' : 'BYBIT_FUTURES'}`;
    return !!botStatus[key];
  };

  const filteredList = useMemo(() => {
    return kimpList.filter(item => 
      item.domesticExchange === selectedDomesticExchange && 
      item.symbol.toLowerCase().includes(searchTerm.toLowerCase())
    );
  }, [kimpList, searchTerm, selectedDomesticExchange]);

  const runAnalysisForAll = async () => {
    const symbols = filteredList.map(item => item.symbol);
    for (const symbol of symbols) {
      runAnalysis(symbol);
    }
  };

  const arbitrageList = useMemo(() => {
    return [...filteredList].sort((a, b) => {
      const resA = backtestResults[a.symbol]?.totalReturn ?? (a.adjustedApr || -100);
      const resB = backtestResults[b.symbol]?.totalReturn ?? (b.adjustedApr || -100);
      return resB - resA;
    });
  }, [filteredList, backtestResults]);

  const formatHoldingTime = (days: number) => {
    if (days === 0) return '0분';
    const hours = days * 24;
    if (hours >= 24) return `${days.toFixed(1)}일`;
    if (hours >= 1) return `${hours.toFixed(1)}시간`;
    const minutes = hours * 60;
    return `${Math.round(minutes)}분`;
  };

  return (
    <div className="arbitrage-content">
      <header className="main-header" style={{ alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '2rem' }}>
          <div className="header-title">
            <h2>차익거래 전략 현황</h2>
            <p className="subtitle">전체 {filteredList.length}개 코인 예상 수익률 및 매매 시뮬레이션</p>
          </div>

          <div className="exchange-selectors" style={{ display: 'flex', alignItems: 'center', gap: '1rem', background: 'rgba(255,255,255,0.05)', padding: '0.5rem 1rem', borderRadius: '0.75rem', border: '1px solid var(--border-color)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', fontWeight: 600 }}>국내</span>
              <select 
                value={selectedDomesticExchange} 
                onChange={(e) => setSelectedDomesticExchange(e.target.value as any)}
                style={{ background: 'transparent', color: 'white', border: 'none', fontWeight: 700, cursor: 'pointer', outline: 'none' }}
              >
                <option value="UPBIT" style={{ background: '#1e293b' }}>UPBIT</option>
                <option value="BITHUMB" style={{ background: '#1e293b' }}>BITHUMB</option>
              </select>
            </div>
            
            <div style={{ width: '1px', height: '16px', background: 'var(--border-color)' }}></div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', fontWeight: 600 }}>해외</span>
              <select 
                value={selectedForeignExchange} 
                onChange={(e) => setSelectedForeignExchange(e.target.value as any)}
                style={{ background: 'transparent', color: 'white', border: 'none', fontWeight: 700, cursor: 'pointer', outline: 'none' }}
              >
                <option value="BINANCE" style={{ background: '#1e293b' }}>BINANCE</option>
                <option value="BYBIT" style={{ background: '#1e293b' }}>BYBIT</option>
              </select>
            </div>
          </div>
        </div>

        <div className="search-box" style={{ position: 'relative', width: '220px' }}>
          <Search size={16} style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-secondary)' }} />
          <input 
            type="text" 
            placeholder="코인 검색 (BTC, ETH...)" 
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{ width: '100%', padding: '0.5rem 1rem 0.5rem 2.4rem', borderRadius: '0.5rem', background: 'rgba(255,255,255,0.05)', border: '1px solid var(--border-color)', color: 'white', fontSize: '0.85rem' }}
          />
        </div>
      </header>

      <div className="card" style={{ marginBottom: '2rem', padding: '1.5rem 2.5rem', background: 'linear-gradient(135deg, #1e293b 0%, #0f172a 100%)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1.5rem' }}>
          <Target size={20} color="var(--accent-color)" />
          <h3 style={{ margin: 0 }}>전략 및 로봇 설정</h3>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '2rem', alignItems: 'flex-end' }}>
            <div className="input-group">
              <label style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginBottom: '0.4rem', display: 'block' }}>진입 김프 (%)</label>
              <input 
                type="text" 
                value={globalEntryKimp} 
                onChange={(e) => {
                  const val = e.target.value;
                  if (val === '' || val === '-' || /^-?\d*\.?\d*$/.test(val)) {
                    setGlobalEntryKimp(val);
                  }
                }}
                onBlur={() => {
                  const num = parseFloat(globalEntryKimp.toString());
                  setGlobalEntryKimp(isNaN(num) ? 0 : num);
                }}
                style={{ width: '100%', padding: '0.6rem', borderRadius: '0.4rem', border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(0,0,0,0.2)', color: 'white' }} 
              />
            </div>
            <div className="input-group">
              <label style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginBottom: '0.4rem', display: 'block' }}>탈출 김프 (%)</label>
              <input 
                type="text" 
                value={globalExitKimp} 
                onChange={(e) => {
                  const val = e.target.value;
                  if (val === '' || val === '-' || /^-?\d*\.?\d*$/.test(val)) {
                    setGlobalExitKimp(val);
                  }
                }}
                onBlur={() => {
                  const num = parseFloat(globalExitKimp.toString());
                  setGlobalExitKimp(isNaN(num) ? 0 : num);
                }}
                style={{ width: '100%', padding: '0.6rem', borderRadius: '0.4rem', border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(0,0,0,0.2)', color: 'white' }} 
              />
            </div>
            <div className="input-group">
              <label style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginBottom: '0.4rem', display: 'block' }}>투자 금액 (KRW)</label>
              <div style={{ position: 'relative' }}>
                <input 
                  type="text" 
                  value={formatNumber(amountKrw)} 
                  onChange={(e) => setAmountKrw(parseNumber(e.target.value))} 
                  style={{ width: '100%', padding: '0.6rem 2.8rem 0.6rem 0.6rem', borderRadius: '0.4rem', border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(0,0,0,0.2)', color: 'white', textAlign: 'right', boxSizing: 'border-box' }} 
                />
                <span style={{ position: 'absolute', right: '0.8rem', top: '50%', transform: 'translateY(-50%)', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>원</span>
              </div>
            </div>
            <div className="input-group">
              <label style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginBottom: '0.4rem', display: 'block' }}>레버리지 (배)</label>
              <select value={leverage} onChange={(e) => setLeverage(parseInt(e.target.value))} style={{ width: '100%', padding: '0.6rem', borderRadius: '0.4rem', border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(0,0,0,0.2)', color: 'white', cursor: 'pointer' }}>
                {[1, 3, 5, 10, 20].map(v => <option key={v} value={v} style={{ background: '#1e293b' }}>{v}배</option>)}
              </select>
            </div>
            <div className="input-group">
              <label style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginBottom: '0.4rem', display: 'block' }}>익절 (%)</label>
              <input type="number" step="0.5" value={takeProfitPercent} onChange={(e) => setTakeProfitPercent(parseFloat(e.target.value))} style={{ width: '100%', padding: '0.6rem', borderRadius: '0.4rem', border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(0,0,0,0.2)', color: 'white' }} />
            </div>
            <div className="input-group">
              <label style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginBottom: '0.4rem', display: 'block' }}>손절 (%)</label>
              <input type="number" step="0.5" value={stopLossPercent} onChange={(e) => setStopLossPercent(parseFloat(e.target.value))} style={{ width: '100%', padding: '0.6rem', borderRadius: '0.4rem', border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(0,0,0,0.2)', color: 'white' }} />
            </div>
            <button className="btn-primary" onClick={runAnalysisForAll} style={{ height: '38px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem', fontWeight: 700, padding: '0 1rem' }}>
              <Calculator size={16} /> <span style={{ whiteSpace: 'nowrap' }}>전체 분석</span>
            </button>
          </div>
        </div>
      </div>

      <div style={{ display: 'grid', gap: '1rem' }}>
        {arbitrageList.map((rec) => (
          <div key={rec.symbol} className="card" style={{ padding: '1.2rem 2rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
                <div style={{ background: 'rgba(56, 189, 248, 0.1)', color: 'var(--accent-color)', width: '48px', height: '48px', borderRadius: '0.75rem', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: '1rem', border: '1px solid rgba(56, 189, 248, 0.2)' }}>{rec.symbol}</div>
                <div style={{ display: 'flex', gap: '2.5rem' }}>
                  <div><div style={{ color: 'var(--text-secondary)', fontSize: '0.75rem', marginBottom: '0.2rem' }}>현재 김프</div><div style={{ fontWeight: 700, fontSize: '1.1rem' }} className={rec.ratio >= 0 ? 'positive' : 'negative'}>{rec.ratio.toFixed(2)}%</div></div>
                  {backtestResults[rec.symbol] ? (
                    <div>
                      <div style={{ color: 'var(--text-secondary)', fontSize: '0.75rem', marginBottom: '0.2rem' }}>30일 누적 수익</div>
                      <div style={{ color: 'var(--success)', fontWeight: 800, fontSize: '1.1rem' }}>+{backtestResults[rec.symbol].totalReturn.toFixed(2)}%</div>
                    </div>
                  ) : (
                    <div>
                      <div style={{ color: 'var(--text-secondary)', fontSize: '0.75rem', marginBottom: '0.2rem' }}>기대 수익</div>
                      <div style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginTop: '0.2rem' }}>분석 전</div>
                    </div>
                  )}
                </div>
              </div>
              <button className="btn-refresh" style={{ background: 'transparent', border: '1px solid var(--border-color)', color: 'var(--text-secondary)', padding: '0.5rem 1.2rem' }} onClick={() => { if (selectedSymbol === rec.symbol) setSelectedSymbol(null); else { setSelectedSymbol(rec.symbol); runAnalysis(rec.symbol); } }}>
                {selectedSymbol === rec.symbol ? <ChevronUp size={16} /> : <BarChart3 size={16} />} <span>{selectedSymbol === rec.symbol ? '닫기' : '상세분석'}</span>
              </button>
            </div>

            {selectedSymbol === rec.symbol && (
              <div className="backtest-container" style={{ marginTop: '1.2rem', background: 'rgba(0,0,0,0.15)', borderRadius: '0.75rem', padding: '1.2rem' }}>
                {loadingBacktest[rec.symbol] ? (
                  <div style={{ textAlign: 'center', padding: '1rem' }}><RefreshCw className="animate-spin" size={20} /> <span style={{ marginLeft: '0.5rem', color: 'var(--text-secondary)' }}>데이터 분석 중...</span></div>
                ) : backtestResults[rec.symbol] ? (
                  <div className="backtest-results" style={{ gridTemplateColumns: 'repeat(4, 1fr)', background: 'transparent', padding: 0 }}>
                    <div className="result-item"><span className="result-label">총 거래 횟수</span><span className="result-value">{backtestResults[rec.symbol].totalTrades}회</span></div>
                    <div className="result-item"><span className="result-label">평균 홀딩 기간</span><span className="result-value">{formatHoldingTime(backtestResults[rec.symbol].avgHoldingDays)}</span></div>
                    <div className="result-item"><span className="result-label">누적 수익률</span><span className="result-value" style={{ color: 'var(--success)' }}>+{backtestResults[rec.symbol].totalReturn.toFixed(2)}%</span></div>
                    <div className="result-item">
                      <span className="result-label">수익 상세</span>
                      <div style={{ fontSize: '0.7rem', color: 'var(--text-secondary)' }}>
                        김프: {backtestResults[rec.symbol].kimpReturn.toFixed(2)}%<br/>
                        펀딩: {backtestResults[rec.symbol].fundingReturn.toFixed(2)}% ({backtestResults[rec.symbol].fundingCount}회)
                      </div>
                    </div>
                  </div>
                ) : null}

                <div style={{ marginTop: '1.5rem', display: 'flex', justifyContent: 'flex-end' }}>
                  {isBotActive(rec.symbol) ? (
                    <button 
                      className="btn-stop" 
                      onClick={() => handleToggleBot(rec.symbol, 'STOP')}
                      disabled={tradingLoading[`${rec.symbol}:${selectedDomesticExchange}:${selectedForeignExchange}`]}
                      style={{ padding: '0.6rem 2rem', borderRadius: '0.5rem', background: '#ef4444', color: 'white', border: 'none', fontWeight: 700, cursor: 'pointer' }}
                    >
                      {tradingLoading[`${rec.symbol}:${selectedDomesticExchange}:${selectedForeignExchange}`] ? <RefreshCw className="animate-spin" size={16} /> : '봇 중단하기'}
                    </button>
                  ) : (
                    <button 
                      className="btn-subscribe" 
                      onClick={() => handleToggleBot(rec.symbol, 'START_AUTO')}
                      disabled={tradingLoading[`${rec.symbol}:${selectedDomesticExchange}:${selectedForeignExchange}`]}
                      style={{ padding: '0.6rem 2rem', borderRadius: '0.5rem', background: 'var(--accent-color)', color: 'black', border: 'none', fontWeight: 700, cursor: 'pointer' }}
                    >
                      {tradingLoading[`${rec.symbol}:${selectedDomesticExchange}:${selectedForeignExchange}`] ? <RefreshCw className="animate-spin" size={16} /> : '이 전략 구독하기 (봇 가동)'}
                    </button>
                  )}
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};

export default Arbitrage;
