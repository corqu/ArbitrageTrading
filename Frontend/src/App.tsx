import React, { useState, useEffect, useMemo } from 'react';
import axios from 'axios';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { 
  RefreshCw, Activity, ShieldAlert, WifiOff, BarChart3, 
  TrendingUp, ChevronDown, ChevronUp, LayoutDashboard, Repeat, Zap, ArrowRight 
} from 'lucide-react';
import { CartesianGrid, Tooltip, ResponsiveContainer, AreaChart, Area, XAxis, YAxis } from 'recharts';
import type { KimchPremium, BotStatus } from './types';
import './App.css';

const App: React.FC = () => {
  const [activeMenu, setActiveMenu] = useState<'dashboard' | 'arbitrage'>('dashboard');
  const [kimpList, setKimpList] = useState<KimchPremium[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);
  const [selectedRange, setSelectedRange] = useState<string>('-6h');
  const [historyData, setHistoryData] = useState<any[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  const fetchData = async () => {
    try {
      setError(null);
      const kimpRes = await axios.get<KimchPremium[]>('/api/kimp/current', { timeout: 3000 });
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

  useEffect(() => {
    fetchData();
    let stompClient = new Client({
      webSocketFactory: () => new SockJS('/ws-stomp'),
      reconnectDelay: 5000,
      onConnect: () => {
        setIsConnected(true);
        stompClient?.subscribe('/topic/kimp', (msg) => setKimpList(JSON.parse(msg.body)));
      },
      onDisconnect: () => setIsConnected(false),
    });
    stompClient.activate();
    return () => { stompClient.deactivate(); };
  }, []);

  const sortedStrategies = useMemo(() => {
    return [...kimpList]
      .filter(i => i.adjustedApr !== null)
      .sort((a, b) => (b.adjustedApr || 0) - (a.adjustedApr || 0));
  }, [kimpList]);

  const gradientOffset = useMemo(() => {
    if (historyData.length === 0) return 0;
    const ratios = historyData.map(i => i.ratio);
    const max = Math.max(...ratios);
    const min = Math.min(...ratios);
    if (max <= 0) return 0;
    if (min >= 0) return 1;
    return max / (max - min);
  }, [historyData]);

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
            <p className="subtitle">{activeMenu === 'dashboard' ? '실시간 김치 프리미엄 및 시장 지표' : '펀딩비와 김프를 고려한 전략별 수익률'}</p>
          </div>
          <button className="btn-refresh" onClick={fetchData}><RefreshCw size={16} /> <span>데이터 갱신</span></button>
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
                  <thead><tr><th style={{ width: '50px' }}>차트</th><th>코인 심볼</th><th>김프 (%)</th><th>펀딩비 (%)</th></tr></thead>
                  <tbody>
                    {kimpList.filter(item => item.foreignExchange !== 'BINANCE_SPOT').map((item, idx) => (
                      <React.Fragment key={`${item.symbol}-${idx}`}>
                        <tr onClick={() => { if(selectedSymbol === item.symbol) setSelectedSymbol(null); else { setSelectedSymbol(item.symbol); fetchHistory(item.symbol, selectedRange); } }} className={`clickable-row ${selectedSymbol === item.symbol ? 'selected-row' : ''}`}>
                          <td style={{ textAlign: 'center' }}>{selectedSymbol === item.symbol ? <ChevronUp size={18} color="var(--accent-color)" /> : <ChevronDown size={18} />}</td>
                          <td><div className="symbol-cell">{item.symbol}</div></td>
                          <td className={item.ratio >= 0 ? 'positive' : 'negative'} style={{ fontWeight: 700 }}>{item.ratio.toFixed(2)}%</td>
                          <td style={{ color: (item.fundingRate || 0) > 0 ? '#10b981' : 'var(--text-primary)' }}>{item.fundingRate ? (item.fundingRate * 100).toFixed(3) + '%' : '-'}</td>
                        </tr>
                        {selectedSymbol === item.symbol && (
                          <tr className="expanded-row">
                            <td colSpan={4}>
                              <div className="expanded-content">
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
                                  <div className="chart-header" style={{ marginBottom: 0 }}><BarChart3 size={16} /><span>{item.symbol} 김프 추이 분석</span></div>
                                  <div className="range-selector" style={{ display: 'flex', gap: '0.4rem', background: 'rgba(0,0,0,0.3)', padding: '0.3rem', borderRadius: '0.5rem' }}>
                                    {[{l:'6시간',v:'-6h'},{l:'1일',v:'-24h'},{l:'1주일',v:'-7d'},{l:'1개월',v:'-30d'}].map((btn) => (
                                      <button key={btn.v} onClick={(e) => { e.stopPropagation(); setSelectedRange(btn.v); fetchHistory(item.symbol, btn.v); }} style={{ padding: '0.3rem 0.6rem', fontSize: '0.75rem', border: 'none', borderRadius: '0.3rem', background: selectedRange === btn.v ? 'var(--accent-color)' : 'transparent', color: selectedRange === btn.v ? '#000' : 'var(--text-secondary)', cursor: 'pointer', fontWeight: 600 }}>{btn.l}</button>
                                    ))}
                                  </div>
                                </div>
                                <div className="chart-wrapper">
                                  {loadingHistory ? <div className="loading-spinner"><RefreshCw className="animate-spin" /></div> : (
                                    <ResponsiveContainer width="100%" height="100%">
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
            <div style={{ display: 'grid', gap: '1.5rem' }}>
              {sortedStrategies.length > 0 ? sortedStrategies.map((rec) => (
                <div key={rec.symbol} className="card" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '1.75rem 2.5rem' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '2rem' }}>
                    <div style={{ background: 'rgba(56, 189, 248, 0.1)', color: 'var(--accent-color)', width: '56px', height: '56px', borderRadius: '1rem', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: '1.1rem', border: '1px solid rgba(56, 189, 248, 0.2)' }}>
                      {rec.symbol}
                    </div>
                    <div>
                      <h3 style={{ margin: 0, fontSize: '1.25rem', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                        {rec.symbol} 헤지 차익거래 전략
                      </h3>
                      <p style={{ margin: '0.5rem 0 0', color: 'var(--text-secondary)', fontSize: '0.9rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        업비트 현물 매수 <ArrowRight size={14} /> 바이낸스 선물 숏 (5x)
                      </p>
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: '3.5rem', alignItems: 'center' }}>
                    <div style={{ textAlign: 'right' }}>
                      <div style={{ color: 'var(--text-secondary)', fontSize: '0.8rem', marginBottom: '0.4rem' }}>현재 김프</div>
                      <div style={{ fontWeight: 700, fontSize: '1.1rem' }} className={rec.ratio >= 0 ? 'positive' : 'negative'}>{rec.ratio.toFixed(2)}%</div>
                    </div>
                    <div style={{ textAlign: 'right' }}>
                      <div style={{ color: 'var(--text-secondary)', fontSize: '0.8rem', marginBottom: '0.4rem' }}>예상 연수익 (APR)</div>
                      <div style={{ color: rec.adjustedApr && rec.adjustedApr > 0 ? 'var(--success)' : 'var(--text-primary)', fontWeight: 800, fontSize: '1.5rem' }}>
                        {rec.adjustedApr?.toFixed(2)}%
                      </div>
                    </div>
                    <button className="btn-refresh" style={{ background: 'var(--card-bg)', border: '1px solid var(--border-color)', color: 'var(--text-secondary)', padding: '0.7rem 1.5rem' }}>
                      <span>전략 상세</span>
                    </button>
                  </div>
                </div>
              )) : (
                <div className="card" style={{ textAlign: 'center', padding: '5rem' }}>
                  <Zap size={48} color="var(--text-secondary)" style={{ marginBottom: '1.5rem', opacity: 0.5 }} />
                  <p style={{ color: 'var(--text-secondary)', fontSize: '1.1rem' }}>차익거래 데이터를 불러오는 중입니다...</p>
                </div>
              )}
            </div>
          </div>
        )}
      </main>
    </div>
  );
};

export default App;
