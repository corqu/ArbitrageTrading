import React, { useState, useEffect, useMemo } from 'react';
import axios from 'axios';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { 
  RefreshCw, Activity, ShieldAlert, WifiOff, BarChart3, 
  TrendingUp, ChevronDown, ChevronUp, LayoutDashboard, Repeat, Zap, ArrowRight, ArrowUpDown, Calculator, Target
} from 'lucide-react';
import { CartesianGrid, Tooltip, ResponsiveContainer, AreaChart, Area, XAxis, YAxis } from 'recharts';
import type { KimchPremium } from './types';
import './App.css';

type SortKey = 'symbol' | 'ratio' | 'fundingRate' | 'tradeVolume' | 'adjustedApr';
type SortOrder = 'asc' | 'desc';

const App: React.FC = () => {
  const [activeMenu, setActiveMenu] = useState<'dashboard' | 'arbitrage'>('dashboard');
  const [kimpList, setKimpList] = useState<KimchPremium[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  // 정렬 관련 상태
  const [sortKey, setSortKey] = useState<SortKey>('tradeVolume');
  const [sortOrder, setSortOrder] = useState<SortOrder>('desc');

  // 차트 관련 상태
  const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);
  const [selectedRange, setSelectedRange] = useState<string>('-6h');
  const [historyData, setHistoryData] = useState<any[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  // 글로벌 전략 설정 상태
  const [globalEntryKimp, setGlobalEntryKimp] = useState<number>(0.5);
  const [globalExitKimp, setGlobalExitKimp] = useState<number>(2.0);
  
  // 백테스트 결과 및 로딩 상태
  const [backtestResults, setBacktestResults] = useState<{[key: string]: any}>({});
  const [loadingBacktest, setLoadingBacktest] = useState<{[key: string]: boolean}>({});

  const fetchData = async () => {
    try {
      setError(null);
      const config = { timeout: 3000 };
      const kimpRes = await axios.get<KimchPremium[]>('/api/kimp/current', config);
      setKimpList(kimpRes.data);
    } catch (err) { setError('서버 연결 실패'); }
    finally { setLoading(false); }
  };

  const fetchHistory = async (symbol: string, range: string) => {
    setLoadingHistory(true);
    try {
      const response = await axios.get(`/api/kimp/history?symbol=${symbol}&range=${range}`);
      const formattedData = response.data.map((item: any) => {
        const date = new Date(item.time);
        let timeLabel = (range === '-6h' || range === '-24h') 
          ? date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
          : date.toLocaleDateString([], { month: 'numeric', day: 'numeric', hour: '2-digit' });
        return { time: timeLabel, ratio: parseFloat(item.ratio.toFixed(2)) };
      }).reverse();
      setHistoryData(formattedData);
    } catch (error) { console.error('기록 조회 실패', error); }
    finally { setLoadingHistory(false); }
  };

  const runAnalysis = async (symbol: string) => {
    setLoadingBacktest(prev => ({ ...prev, [symbol]: true }));
    try {
      const response = await axios.get(`/api/arbitrage/backtest`, {
        params: { symbol, entryKimp: globalEntryKimp, exitKimp: globalExitKimp, range: '-30d' }
      });
      setBacktestResults(prev => ({ ...prev, [symbol]: response.data }));
    } catch (error) {
      console.error('분석 실패');
    } finally {
      setLoadingBacktest(prev => ({ ...prev, [symbol]: false }));
    }
  };

  const formatHoldingTime = (days: number) => {
    if (days === 0) return '0분';
    const hours = days * 24;
    if (hours >= 24) return `${days.toFixed(1)}일`;
    if (hours >= 1) return `${hours.toFixed(1)}시간`;
    const minutes = hours * 60;
    return `${Math.round(minutes)}분`;
  };

  const runAnalysisForAll = async () => {
    const symbols = arbitrageList.map(item => item.symbol);
    for (const symbol of symbols) {
      setLoadingBacktest(prev => ({ ...prev, [symbol]: true }));
      try {
        const response = await axios.get(`/api/arbitrage/backtest`, {
          params: { symbol, entryKimp: globalEntryKimp, exitKimp: globalExitKimp, range: '-30d' }
        });
        setBacktestResults(prev => ({ ...prev, [symbol]: response.data }));
      } catch (error) {
        console.error(`${symbol} 분석 실패`, error);
      } finally {
        setLoadingBacktest(prev => ({ ...prev, [symbol]: false }));
      }
    }
  };

  useEffect(() => {
    fetchData();
    const stompClient = new Client({
      webSocketFactory: () => new SockJS('/ws-stomp'),
      reconnectDelay: 5000,
      onConnect: () => {
        setIsConnected(true);
        setError(null);
        stompClient.subscribe('/topic/kimp', (msg) => {
          if (msg.body) {
            setKimpList(JSON.parse(msg.body));
          }
        });
      },
      onDisconnect: () => setIsConnected(false),
    });
    stompClient.activate();
    return () => { stompClient.deactivate(); };
  }, []);

  const arbitrageList = useMemo(() => {
    return [...kimpList].sort((a, b) => {
      // 백테스트 결과가 있으면 totalReturn 기준, 없으면 기존 adjustedApr 기준
      const resA = backtestResults[a.symbol]?.totalReturn ?? (a.adjustedApr || -100);
      const resB = backtestResults[b.symbol]?.totalReturn ?? (b.adjustedApr || -100);
      return resB - resA;
    });
  }, [kimpList, backtestResults]);

  const sortedKimpList = useMemo(() => {
    return [...kimpList].sort((a, b) => {
      let valA: any = a[sortKey], valB: any = b[sortKey];
      if (valA === null || valA === undefined) return 1;
      if (valB === null || valB === undefined) return -1;
      return sortOrder === 'asc' ? (valA > valB ? 1 : -1) : (valA < valB ? 1 : -1);
    });
  }, [kimpList, sortKey, sortOrder]);

  const gradientOffset = useMemo(() => {
    if (historyData.length === 0) return 0;
    const ratios = historyData.map(i => i.ratio);
    const max = Math.max(...ratios), min = Math.min(...ratios);
    if (max <= 0) return 0; if (min >= 0) return 1;
    return max / (max - min);
  }, [historyData]);

  const formatVolume = (volume: number | null) => {
    if (!volume) return '-';
    const EOK = 100000000, JO = 1000000000000;
    if (volume >= JO) {
      const joPart = Math.floor(volume / JO), eokPart = Math.floor((volume % JO) / EOK);
      return eokPart > 0 ? `${joPart}조 ${eokPart.toLocaleString()}억` : `${joPart}조`;
    }
    return `${Math.floor(volume / EOK).toLocaleString()}억`;
  };

  if (loading) return <div className="dashboard-loading"><RefreshCw className="animate-spin" size={48} /><p>데이터 로딩 중...</p></div>;

  return (
    <div className="app-container">
      <nav className="sidebar">
        <div className="sidebar-logo"><TrendingUp color="var(--accent-color)" size={28} /><span>김프알람</span></div>
        <div className="sidebar-menu">
          <button className={`menu-item ${activeMenu === 'dashboard' ? 'active' : ''}`} onClick={() => setActiveMenu('dashboard')}><LayoutDashboard size={20} /><span>대시보드</span></button>
          <button className={`menu-item ${activeMenu === 'arbitrage' ? 'active' : ''}`} onClick={() => setActiveMenu('arbitrage')}><Repeat size={20} /><span>차익거래</span></button>
        </div>
        <div className="sidebar-footer"><div className="status-group"><div className={`status-dot ${isConnected ? 'online' : 'offline'}`} /><span>{isConnected ? '실시간 연결됨' : '연결 끊김'}</span></div></div>
      </nav>

      <main className="main-content">
        <header className="main-header">
          <div className="header-title">
            <h2>{activeMenu === 'dashboard' ? '시장 대시보드' : '차익거래 전략 현황'}</h2>
            <p className="subtitle">{activeMenu === 'dashboard' ? `전체 ${kimpList.length}개 코인 실시간 현황` : '전체 코인 예상 수익률 및 매매 시뮬레이션'}</p>
          </div>
          <button className="btn-refresh" onClick={() => {
            fetchData();
            if (activeMenu === 'arbitrage' && selectedSymbol) {
              runAnalysis(selectedSymbol);
            }
          }}><RefreshCw size={16} /> <span>데이터 갱신</span></button>
        </header>

        {activeMenu === 'dashboard' ? (
          <div className="dashboard-content">
            <div className="summary-grid">
              <div className="summary-card"><span className="label">추적 중인 코인</span><span className="value">{kimpList.length}개</span></div>
              <div className="summary-card"><span className="label">평균 김치 프리미엄</span><span className="value">{(kimpList.reduce((acc, curr) => acc + (curr.ratio || 0), 0) / (kimpList.length || 1)).toFixed(2)}%</span></div>
              <div className="summary-card"><span className="label">시스템 상태</span><span className="value" style={{ color: isConnected ? 'var(--success)' : 'var(--danger)' }}>{isConnected ? '정상 작동 중' : '서버 확인 필요'}</span></div>
            </div>
            <section className="card">
              <div className="table-container">
                <table>
                  <thead>
                    <tr>
                      <th style={{ width: '50px' }}>차트</th>
                      <th onClick={() => setSortKey('symbol')} style={{ cursor: 'pointer' }}>코인 심볼 {sortKey === 'symbol' && (sortOrder === 'asc' ? '↑' : '↓')}</th>
                      <th onClick={() => setSortKey('ratio')} style={{ cursor: 'pointer' }}>김프 (%) {sortKey === 'ratio' && (sortOrder === 'asc' ? '↑' : '↓')}</th>
                      <th onClick={() => setSortKey('fundingRate')} style={{ cursor: 'pointer' }}>펀딩비 (%) {sortKey === 'fundingRate' && (sortOrder === 'asc' ? '↑' : '↓')}</th>
                      <th onClick={() => setSortKey('tradeVolume')} style={{ cursor: 'pointer' }}>거래대금 (24h) {sortKey === 'tradeVolume' && (sortOrder === 'asc' ? '↑' : '↓')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sortedKimpList.map((item, idx) => (
                      <React.Fragment key={`${item.symbol}-${idx}`}>
                        <tr onClick={() => { if(selectedSymbol === item.symbol) setSelectedSymbol(null); else { setSelectedSymbol(item.symbol); fetchHistory(item.symbol, selectedRange); } }} className={`clickable-row ${selectedSymbol === item.symbol ? 'selected-row' : ''}`}>
                          <td style={{ textAlign: 'center' }}>{selectedSymbol === item.symbol ? <ChevronUp size={18} color="var(--accent-color)" /> : <ChevronDown size={18} />}</td>
                          <td><div className="symbol-cell">{item.symbol}</div></td>
                          <td className={item.ratio >= 0 ? 'positive' : 'negative'} style={{ fontWeight: 700 }}>{item.ratio.toFixed(2)}%</td>
                          <td style={{ color: (item.fundingRate || 0) > 0 ? '#10b981' : 'var(--text-primary)' }}>{item.fundingRate ? (item.fundingRate * 100).toFixed(3) + '%' : '-'}</td>
                          <td style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>{formatVolume(item.tradeVolume)}</td>
                        </tr>
                        {selectedSymbol === item.symbol && (
                          <tr className="expanded-row">
                            <td colSpan={5}>
                              <div className="expanded-content">
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
                                  <div className="chart-header" style={{ marginBottom: 0 }}><BarChart3 size={16} /><span>{item.symbol} 김프 추이 분석</span></div>
                                  <div className="range-selector" style={{ display: 'flex', gap: '0.4rem', background: 'rgba(0,0,0,0.3)', padding: '0.3rem', borderRadius: '0.5rem' }}>
                                    {[{l:'6시간',v:'-6h'},{l:'1일',v:'-24h'},{l:'1주일',v:'-7d'},{l:'1개월',v:'-30d'}].map((btn) => (
                                      <button key={btn.v} onClick={(e) => { e.stopPropagation(); setSelectedRange(btn.v); fetchHistory(item.symbol, btn.v); }} style={{ padding: '0.3rem 0.6rem', fontSize: '0.75rem', border: 'none', borderRadius: '0.3rem', background: selectedRange === btn.v ? 'var(--accent-color)' : 'transparent', color: selectedRange === btn.v ? '#000' : 'var(--text-secondary)', cursor: 'pointer', fontWeight: 600 }}>{btn.l}</button>
                                    ))}
                                  </div>
                                </div>
                                <div className="chart-wrapper" style={{ height: '250px', minHeight: '250px', width: '100%', position: 'relative' }}>
                                  {loadingHistory ? <div className="loading-spinner"><RefreshCw className="animate-spin" /></div> : (
                                    <ResponsiveContainer width="100%" height="100%" minHeight={200}>
                                      <AreaChart data={historyData}>
                                        <defs>
                                          <linearGradient id="splitColor" x1="0" y1="0" x2="0" y2="1"><stop offset={gradientOffset} stopColor="#10b981" stopOpacity={0.8} /><stop offset={gradientOffset} stopColor="#ef4444" stopOpacity={0.8} /></linearGradient>
                                          <linearGradient id="splitFill" x1="0" y1="0" x2="0" y2="1"><stop offset={gradientOffset} stopColor="#10b981" stopOpacity={0.3} /><stop offset={gradientOffset} stopColor="#ef4444" stopOpacity={0.3} /></linearGradient>
                                        </defs>
                                        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                                        <XAxis dataKey="time" stroke="var(--text-secondary)" fontSize={10} axisLine={false} tickLine={false} />
                                        <YAxis stroke="var(--text-secondary)" fontSize={10} axisLine={false} tickLine={false} tickFormatter={(v) => `${v.toFixed(2)}%`} />
                                        <Tooltip contentStyle={{ backgroundColor: 'var(--card-bg)', border: '1px solid var(--border-color)', borderRadius: '8px' }} formatter={(v: any) => [`${v.toFixed(2)}%`, '김프']} />
                                        <Area type="monotone" dataKey="ratio" stroke="url(#splitColor)" fill="url(#splitFill)" strokeWidth={2} />
                                      </AreaChart>
                                    </ResponsiveContainer>
                                  )}
                                </div>
                              </div>
                            </td>
                          </tr>
                        )}
                      </React.Fragment>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          </div>
        ) : (
          <div className="arbitrage-content">
            <div className="card" style={{ marginBottom: '2rem', padding: '1.5rem 2rem', background: 'linear-gradient(135deg, #1e293b 0%, #0f172a 100%)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1.2rem' }}>
                <Target size={20} color="var(--accent-color)" />
                <h3 style={{ margin: 0 }}>전략 목표 설정</h3>
              </div>
              <div style={{ display: 'flex', gap: '2rem', alignItems: 'flex-end' }}>
                <div className="input-group">
                  <label>진입 목표 김프 (%)</label>
                  <input type="number" step="0.1" value={globalEntryKimp} onChange={(e) => setGlobalEntryKimp(parseFloat(e.target.value))} style={{ width: '120px', padding: '0.6rem' }} />
                </div>
                <div className="input-group">
                  <label>탈출 목표 김프 (%)</label>
                  <input type="number" step="0.1" value={globalExitKimp} onChange={(e) => setGlobalExitKimp(parseFloat(e.target.value))} style={{ width: '120px', padding: '0.6rem' }} />
                </div>
                <button 
                  className="btn-refresh" 
                  onClick={runAnalysisForAll}
                  style={{ marginBottom: '0.6rem', height: 'fit-content' }}
                >
                  <Calculator size={16} /> <span>전체 분석 갱신</span>
                </button>
                <div style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', paddingBottom: '0.6rem' }}>* 설정한 목표치 도달 시의 30일간 누적 수익을 분석하며, 결과에 따라 리스트가 정렬됩니다.</div>
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
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}
      </main>
    </div>
  );
};

export default App;
