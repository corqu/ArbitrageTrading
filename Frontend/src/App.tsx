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
  const [activeMenu, setActiveMenu] = useState<'dashboard' | 'arbitrage' | 'auth' | 'mypage'>('dashboard');
  
  // URL 경로와 activeMenu 상태 동기화
  useEffect(() => {
    const handleLocationChange = () => {
      const path = window.location.pathname;
      if (path === '/mypage') setActiveMenu('mypage');
      else if (path === '/arbitrage') setActiveMenu('arbitrage');
      else if (path === '/auth') setActiveMenu('auth');
      else setActiveMenu('dashboard');
    };

    // 초기 로드 시 경로 확인
    handleLocationChange();

    // 뒤로가기/앞으로가기 이벤트 리스너
    window.addEventListener('popstate', handleLocationChange);
    return () => window.removeEventListener('popstate', handleLocationChange);
  }, []);

  // activeMenu 변경 시 URL 업데이트 (뒤로가기 지원)
  const changeMenu = (menu: 'dashboard' | 'arbitrage' | 'auth' | 'mypage') => {
    if (activeMenu === menu) return;
    
    const path = menu === 'dashboard' ? '/' : `/${menu}`;
    window.history.pushState(null, '', path);
    setActiveMenu(menu);
  };

  const [kimpList, setKimpList] = useState<KimchPremium[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  // 정렬 관련 상태
  const [sortKey, setSortKey] = useState<SortKey>('tradeVolume');
  const [sortOrder, setSortOrder] = useState<SortOrder>('desc');

  const toggleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      setSortKey(key);
      setSortOrder('desc');
    }
  };

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

  // 인증 관련 상태
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [authMode, setAuthMode] = useState<'login' | 'signup'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [loginError, setLoginError] = useState<string | null>(null);
  const [isAuthLoading, setIsAuthLoading] = useState(false);
  const [isEditingProfile, setIsEditingProfile] = useState(false);
  const [showAddExchangeModal, setShowAddExchangeModal] = useState(false);
  const [selectedExchange, setSelectedExchange] = useState<'UPBIT' | 'BINANCE' | null>(null);
  const [newNickname, setNewNickname] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [apiSecret, setApiSecret] = useState('');
  const [connectedExchanges, setConnectedExchanges] = useState<string[]>([]);
  const [isNicknameChecked, setIsNicknameChecked] = useState(false);
  const [isNicknameAvailable, setIsNicknameAvailable] = useState(false);
  const logoutTimerRef = React.useRef<number | null>(null);

  const startLogoutTimer = (seconds: number) => {
    if (logoutTimerRef.current) window.clearTimeout(logoutTimerRef.current);
    if (seconds <= 0) return;

    logoutTimerRef.current = window.setTimeout(() => {
      alert('로그인 세션이 만료되어 로그아웃되었습니다.');
      handleLogout();
    }, seconds * 1000);
  };

  // 앱 시작 시 로그인 상태 확인 (새로고침 대응)
  useEffect(() => {
    const checkAuth = async () => {
      try {
        const response = await axios.get('/api/auth/me');
        if (response.data.nickname) {
          setIsLoggedIn(true);
          setNickname(response.data.nickname);
          if (response.data.expiresIn) {
            startLogoutTimer(response.data.expiresIn);
          }
        }
      } catch (err) {
        console.log('세션 없음');
      } finally {
        setLoading(false);
      }
    };
    checkAuth();
    return () => { if (logoutTimerRef.current) window.clearTimeout(logoutTimerRef.current); };
  }, []);

  const checkNicknameAvailability = async () => {
    if (!nickname) return;
    try {
      const response = await axios.get(`/api/auth/check-nickname?nickname=${nickname}`);
      setIsNicknameAvailable(response.data.available);
      setIsNicknameChecked(true);
      if (!response.data.available) {
        alert('이미 사용 중인 닉네임입니다.');
      } else {
        alert('사용 가능한 닉네임입니다.');
      }
    } catch (err) {
      alert('닉네임 확인 실패');
    }
  };

  const fetchConnectedExchanges = async () => {
    if (!isLoggedIn) return;
    try {
      const response = await axios.get('/api/user/credentials/list');
      // 백엔드에서 List<String>을 반환하므로 바로 저장합니다.
      setConnectedExchanges(response.data);
    } catch (err) {
      console.error('연동 목록 조회 실패', err);
    }
  };

  const handleConnectExchange = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await axios.post('/api/user/credentials/register', {
        exchange: selectedExchange,
        accessKey: apiKey,
        secretKey: apiSecret
      });
      alert(`${selectedExchange} 연동이 완료되었습니다.`);
      setShowAddExchangeModal(false);
      setApiKey('');
      setApiSecret('');
      fetchConnectedExchanges();
    } catch (err: any) {
      alert('연동 실패: ' + (err.response?.data?.message || '알 수 없는 오류'));
    }
  };

  const handleDeleteExchange = async (exchange: string) => {
    if (!confirm(`${exchange} 연동을 해제하시겠습니까?`)) return;
    try {
      await axios.delete(`/api/user/credentials/${exchange}`);
      alert(`${exchange} 연동이 해제되었습니다.`);
      fetchConnectedExchanges();
    } catch (err) {
      alert('해제 실패');
    }
  };

  useEffect(() => {
    if (isLoggedIn) fetchConnectedExchanges();
  }, [isLoggedIn]);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsAuthLoading(true);
    setLoginError(null);
    try {
      await axios.post('/api/auth/login', { email, password });
      setIsLoggedIn(true);
      setPassword(''); 
      changeMenu('dashboard'); // 로그인 성공 시 대시보드로 이동
    } catch (err: any) {
      setLoginError(err.response?.data || '로그인 실패');
    } finally {
      setIsAuthLoading(false);
    }
  };

  const handleSignup = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsAuthLoading(true);
    setLoginError(null);
    try {
      await axios.post('/api/auth/signup', { email, password, nickname });
      alert('회원가입이 완료되었습니다. 로그인해주세요.');
      // 상태 초기화 및 로그인 모드로 전환
      setAuthMode('login');
      setPassword('');
      setNickname('');
      setLoginError(null);
    } catch (err: any) {
      setLoginError(err.response?.data || '회원가입 실패');
    } finally {
      setIsAuthLoading(false);
    }
  };

  const handleLogout = () => {
    setIsLoggedIn(false);
    setEmail('');
    changeMenu('dashboard');
  };

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
      });
      // 데이터가 최신순으로 올 경우만 뒤집어서 [과거 -> 최신] 순서로 만듭니다.
      // 보통 InfluxDB 쿼리 결과에 따라 다르지만, 사용자가 반대로 나온다고 했으므로 reverse를 제거해봅니다.
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
      let valA = a[sortKey];
      let valB = b[sortKey];
      
      if (valA === null || valA === undefined) return 1;
      if (valB === null || valB === undefined) return -1;

      if (typeof valA === 'string' && typeof valB === 'string') {
        return sortOrder === 'asc' 
          ? valA.localeCompare(valB) 
          : valB.localeCompare(valA);
      }

      return sortOrder === 'asc' 
        ? (valA as number) - (valB as number) 
        : (valB as number) - (valA as number);
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
          <button className={`menu-item ${activeMenu === 'dashboard' ? 'active' : ''}`} onClick={() => changeMenu('dashboard')}><LayoutDashboard size={20} /><span>대시보드</span></button>
          <button className={`menu-item ${activeMenu === 'arbitrage' ? 'active' : ''}`} onClick={() => changeMenu('arbitrage')}><Repeat size={20} /><span>차익거래</span></button>
        </div>
        <div className="sidebar-footer">
          <div className="auth-section" style={{ padding: '1rem', borderTop: '1px solid var(--border-color)', marginBottom: '0.5rem' }}>
            {isLoggedIn ? (
              <div className="user-profile" style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis' }}>{email}</div>
                <div style={{ display: 'flex', gap: '0.4rem' }}>
                  <button onClick={() => changeMenu('mypage')} style={{ flex: 1, padding: '0.5rem', fontSize: '0.8rem', background: 'rgba(56, 189, 248, 0.1)', color: 'var(--accent-color)', border: '1px solid rgba(56, 189, 248, 0.2)', borderRadius: '0.4rem', cursor: 'pointer', fontWeight: 600 }}>마이페이지</button>
                  <button onClick={handleLogout} style={{ flex: 1, padding: '0.5rem', fontSize: '0.8rem', background: 'rgba(239, 68, 68, 0.1)', color: '#ef4444', border: '1px solid rgba(239, 68, 68, 0.2)', borderRadius: '0.4rem', cursor: 'pointer', fontWeight: 600 }}>로그아웃</button>
                </div>
              </div>
            ) : (
              <button 
                onClick={() => changeMenu('auth')} 
                style={{ width: '100%', padding: '0.6rem', fontSize: '0.85rem', background: 'var(--accent-color)', color: '#000', border: 'none', borderRadius: '0.4rem', cursor: 'pointer', fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}
              >
                로그인 / 회원가입
              </button>
            )}
          </div>
          <div className="status-group"><div className={`status-dot ${isConnected ? 'online' : 'offline'}`} /><span>{isConnected ? '실시간 연결됨' : '연결 끊김'}</span></div>
        </div>
      </nav>

      <main className="main-content">
        <header className="main-header">
          <div className="header-title">
            <h2>
              {activeMenu === 'dashboard' ? '시장 대시보드' : 
               activeMenu === 'arbitrage' ? '차익거래 전략 현황' : '사용자 인증'}
            </h2>
            <p className="subtitle">
              {activeMenu === 'dashboard' ? `전체 ${kimpList.length}개 코인 실시간 현황` : 
               activeMenu === 'arbitrage' ? '전체 코인 예상 수익률 및 매매 시뮬레이션' : '서비스 이용을 위한 로그인 및 회원가입'}
            </p>
          </div>
          {activeMenu !== 'auth' && (
            <button className="btn-refresh" onClick={() => {
              fetchData();
              if (activeMenu === 'arbitrage' && selectedSymbol) {
                runAnalysis(selectedSymbol);
              }
            }}><RefreshCw size={16} /> <span>데이터 갱신</span></button>
          )}
        </header>

        {activeMenu === 'dashboard' ? (
          <div className="dashboard-content">
            {/* ... 기존 대시보드 내용 ... */}
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
                      <th onClick={() => toggleSort('symbol')} style={{ cursor: 'pointer' }}>코인 심볼 {sortKey === 'symbol' && (sortOrder === 'asc' ? '↑' : '↓')}</th>
                      <th onClick={() => toggleSort('ratio')} style={{ cursor: 'pointer' }}>김프 (%) {sortKey === 'ratio' && (sortOrder === 'asc' ? '↑' : '↓')}</th>
                      <th onClick={() => toggleSort('fundingRate')} style={{ cursor: 'pointer' }}>펀딩비 (%) {sortKey === 'fundingRate' && (sortOrder === 'asc' ? '↑' : '↓')}</th>
                      <th onClick={() => toggleSort('tradeVolume')} style={{ cursor: 'pointer' }}>거래대금 (24h) {sortKey === 'tradeVolume' && (sortOrder === 'asc' ? '↑' : '↓')}</th>
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
        ) : activeMenu === 'mypage' ? (
          <div className="mypage-content" style={{ maxWidth: '800px', margin: '0 auto' }}>
            <div className="card" style={{ padding: '2.5rem' }}>
              {/* 상단: 내 프로필 정보 */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '2rem' }}>
                <Activity size={24} color="var(--accent-color)" />
                <h3 style={{ margin: 0 }}>내 프로필 및 계정 설정</h3>
              </div>
              
              {!isEditingProfile ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginBottom: '2.5rem' }}>
                  <div style={{ display: 'flex', borderBottom: '1px solid var(--border-color)', paddingBottom: '0.8rem' }}>
                    <span style={{ width: '120px', color: 'var(--text-secondary)', fontSize: '0.9rem' }}>이메일(ID)</span>
                    <span style={{ fontWeight: 600 }}>{email}</span>
                  </div>
                  <div style={{ display: 'flex', borderBottom: '1px solid var(--border-color)', paddingBottom: '0.8rem', alignItems: 'center', justifyContent: 'space-between' }}>
                    <div style={{ display: 'flex' }}>
                      <span style={{ width: '120px', color: 'var(--text-secondary)', fontSize: '0.9rem' }}>닉네임</span>
                      <span style={{ fontWeight: 600 }}>{nickname || '설정되지 않음'}</span>
                    </div>
                    <button 
                      onClick={() => { setIsEditingProfile(true); setNewNickname(nickname); }}
                      style={{ padding: '0.4rem 1rem', background: 'rgba(255,255,255,0.05)', color: 'var(--text-primary)', border: '1px solid var(--border-color)', borderRadius: '0.4rem', cursor: 'pointer', fontSize: '0.8rem' }}
                    >
                      정보 수정
                    </button>
                  </div>
                </div>
              ) : (
                <form onSubmit={handleUpdateProfile} style={{ display: 'flex', flexDirection: 'column', gap: '1.2rem', marginBottom: '2.5rem', padding: '1.5rem', background: 'rgba(255,255,255,0.02)', borderRadius: '0.75rem', border: '1px solid var(--border-color)' }}>
                  <div className="input-group">
                    <label style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '0.4rem', display: 'block' }}>닉네임 변경</label>
                    <input type="text" value={newNickname} onChange={(e) => setNewNickname(e.target.value)} style={{ width: '100%', padding: '0.75rem', borderRadius: '0.5rem', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: 'white' }} />
                  </div>
                  <div className="input-group">
                    <label style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '0.4rem', display: 'block' }}>새 비밀번호</label>
                    <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder="변경할 경우에만 입력" style={{ width: '100%', padding: '0.75rem', borderRadius: '0.5rem', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: 'white' }} />
                  </div>
                  <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end', marginTop: '0.5rem' }}>
                    <button type="button" onClick={() => setIsEditingProfile(false)} style={{ padding: '0.5rem 1.2rem', background: 'transparent', color: 'var(--text-secondary)', border: 'none', cursor: 'pointer', fontSize: '0.9rem' }}>취소</button>
                    <button type="submit" style={{ padding: '0.5rem 1.5rem', background: 'var(--accent-color)', color: '#000', border: 'none', borderRadius: '0.4rem', fontWeight: 700, cursor: 'pointer', fontSize: '0.9rem' }}>저장하기</button>
                  </div>
                </form>
              )}

              {/* 하단 섹션: 연동된 거래소 리스트 (같은 카드 내부) */}
              <div style={{ borderTop: '1px solid var(--border-color)', paddingTop: '2rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                    <ShieldAlert size={20} color="var(--accent-color)" />
                    <h4 style={{ margin: 0, fontSize: '1.1rem' }}>연동된 거래소</h4>
                  </div>
                  {!showAddExchangeModal && (
                    <button 
                      onClick={() => setShowAddExchangeModal(true)}
                      style={{ padding: '0.4rem 0.8rem', background: 'var(--accent-color)', color: '#000', border: 'none', borderRadius: '0.4rem', fontWeight: 700, cursor: 'pointer', fontSize: '0.8rem' }}
                    >
                      거래소 연동하기
                    </button>
                  )}
                </div>

                {showAddExchangeModal && (
                  <div style={{ padding: '1.5rem', background: 'rgba(56, 189, 248, 0.05)', borderRadius: '0.75rem', border: '1px solid rgba(56, 189, 248, 0.2)', marginBottom: '1.5rem' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.2rem' }}>
                      <span style={{ fontWeight: 700, fontSize: '0.9rem' }}>새 거래소 연동</span>
                      <button onClick={() => { setShowAddExchangeModal(false); setSelectedExchange(null); }} style={{ background: 'transparent', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer', fontSize: '0.8rem' }}>닫기</button>
                    </div>
                    
                    <div style={{ display: 'flex', gap: '0.8rem', marginBottom: '1.2rem' }}>
                      {['UPBIT', 'BINANCE'].map(ex => (
                        <button 
                          key={ex}
                          onClick={() => setSelectedExchange(ex as any)}
                          style={{ flex: 1, padding: '0.6rem', borderRadius: '0.4rem', border: `1px solid ${selectedExchange === ex ? 'var(--accent-color)' : 'var(--border-color)'}`, background: selectedExchange === ex ? 'rgba(56, 189, 248, 0.1)' : 'transparent', color: selectedExchange === ex ? 'var(--accent-color)' : 'var(--text-secondary)', cursor: 'pointer', fontSize: '0.85rem', fontWeight: 600 }}
                        >
                          {ex}
                        </button>
                      ))}
                    </div>

                    {selectedExchange && (
                      <form onSubmit={handleConnectExchange} style={{ display: 'flex', flexDirection: 'column', gap: '0.8rem' }}>
                        <input type="text" placeholder="Access Key (API Key)" value={apiKey} onChange={(e) => setApiKey(e.target.value)} required style={{ width: '100%', padding: '0.6rem', borderRadius: '0.4rem', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: 'white', fontSize: '0.85rem' }} />
                        <input type="password" placeholder="Secret Key" value={apiSecret} onChange={(e) => setApiSecret(e.target.value)} required style={{ width: '100%', padding: '0.6rem', borderRadius: '0.4rem', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: 'white', fontSize: '0.85rem' }} />
                        <button type="submit" style={{ padding: '0.6rem', background: 'var(--accent-color)', color: '#000', border: 'none', borderRadius: '0.4rem', fontWeight: 700, cursor: 'pointer', fontSize: '0.9rem' }}>연동 완료</button>
                      </form>
                    )}
                  </div>
                )}

                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.8rem' }}>
                  {connectedExchanges.length > 0 ? (
                    connectedExchanges.map((ex) => (
                      <div key={ex} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '1rem 1.5rem', background: 'rgba(255,255,255,0.03)', borderRadius: '0.75rem', border: '1px solid var(--border-color)' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.8rem' }}>
                          <div style={{ width: '32px', height: '32px', background: ex === 'UPBIT' ? '#0066ff' : '#f3ba2f', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 900, color: 'white', fontSize: '0.7rem' }}>{ex[0]}</div>
                          <span style={{ fontWeight: 700 }}>{ex === 'UPBIT' ? '업비트 (Upbit)' : '바이낸스 (Binance)'}</span>
                          <span style={{ fontSize: '0.75rem', color: 'var(--success)', fontWeight: 600 }}>● 연결됨</span>
                        </div>
                        <button 
                          onClick={() => handleDeleteExchange(ex)}
                          style={{ padding: '0.3rem 0.8rem', background: 'rgba(239, 68, 68, 0.1)', color: '#ef4444', border: '1px solid rgba(239, 68, 68, 0.2)', borderRadius: '0.4rem', cursor: 'pointer', fontSize: '0.75rem', fontWeight: 600 }}
                        >
                          연동 해제
                        </button>
                      </div>
                    ))
                  ) : (
                    <div style={{ textAlign: 'center', padding: '2.5rem 0', color: 'var(--text-secondary)', background: 'rgba(255,255,255,0.02)', borderRadius: '0.75rem', border: '1px dashed var(--border-color)' }}>
                      <p style={{ fontSize: '0.9rem', margin: 0 }}>아직 연동된 거래소가 없습니다.</p>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        ) : activeMenu === 'arbitrage' ? (
          <div className="arbitrage-content">
            {/* ... 기존 차익거래 내용 ... */}
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
        ) : (
          <div className="auth-content" style={{ display: 'flex', justifyContent: 'center', paddingTop: '3rem' }}>
            <div className="card" style={{ width: '400px', padding: '2.5rem' }}>
              <div style={{ display: 'flex', gap: '1rem', marginBottom: '2rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '1rem' }}>
                <button onClick={() => setAuthMode('login')} style={{ flex: 1, background: 'transparent', border: 'none', color: authMode === 'login' ? 'var(--accent-color)' : 'var(--text-secondary)', fontWeight: 700, cursor: 'pointer', fontSize: '1.1rem', position: 'relative' }}>
                  로그인
                  {authMode === 'login' && <div style={{ position: 'absolute', bottom: '-1.1rem', left: 0, width: '100%', height: '2px', background: 'var(--accent-color)' }} />}
                </button>
                <button onClick={() => setAuthMode('signup')} style={{ flex: 1, background: 'transparent', border: 'none', color: authMode === 'signup' ? 'var(--accent-color)' : 'var(--text-secondary)', fontWeight: 700, cursor: 'pointer', fontSize: '1.1rem', position: 'relative' }}>
                  회원가입
                  {authMode === 'signup' && <div style={{ position: 'absolute', bottom: '-1.1rem', left: 0, width: '100%', height: '2px', background: 'var(--accent-color)' }} />}
                </button>
              </div>

              <form onSubmit={authMode === 'login' ? handleLogin : handleSignup} style={{ display: 'flex', flexDirection: 'column', gap: '1.2rem' }}>
                <div className="input-group">
                  <label style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '0.4rem', display: 'block' }}>이메일</label>
                  <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required style={{ width: '100%', padding: '0.75rem', borderRadius: '0.5rem', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: 'white' }} placeholder="example@email.com" />
                </div>
                
                {authMode === 'signup' && (
                  <div className="input-group">
                    <label style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '0.4rem', display: 'block' }}>닉네임</label>
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <input 
                        type="text" 
                        value={nickname} 
                        onChange={(e) => {
                          setNickname(e.target.value);
                          setIsNicknameChecked(false);
                          setIsNicknameAvailable(false);
                        }} 
                        required 
                        style={{ flex: 1, padding: '0.75rem', borderRadius: '0.5rem', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: 'white' }} 
                        placeholder="사용할 닉네임" 
                      />
                      <button 
                        type="button"
                        onClick={checkNicknameAvailability}
                        style={{ padding: '0 1rem', background: 'rgba(255,255,255,0.05)', color: 'white', border: '1px solid var(--border-color)', borderRadius: '0.5rem', fontSize: '0.8rem', cursor: 'pointer' }}
                      >
                        중복확인
                      </button>
                    </div>
                    {isNicknameChecked && (
                      <span style={{ fontSize: '0.75rem', marginTop: '0.3rem', display: 'block', color: isNicknameAvailable ? 'var(--success)' : '#ef4444' }}>
                        {isNicknameAvailable ? '사용 가능한 닉네임입니다.' : '이미 사용 중인 닉네임입니다.'}
                      </span>
                    )}
                  </div>
                )}

                <div className="input-group">
                  <label style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '0.4rem', display: 'block' }}>비밀번호</label>
                  <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required style={{ width: '100%', padding: '0.75rem', borderRadius: '0.5rem', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: 'white' }} placeholder="••••••••" />
                </div>

                {loginError && <div style={{ fontSize: '0.85rem', color: '#ef4444', padding: '0.5rem', background: 'rgba(239, 68, 68, 0.1)', borderRadius: '0.4rem' }}>{loginError}</div>}

                <button 
                  type="submit" 
                  disabled={isAuthLoading || (authMode === 'signup' && !isNicknameAvailable)} 
                  style={{ 
                    marginTop: '1rem', padding: '0.8rem', borderRadius: '0.5rem', border: 'none', 
                    background: (authMode === 'signup' && !isNicknameAvailable) ? 'var(--text-secondary)' : 'var(--accent-color)', 
                    color: '#000', fontWeight: 800, cursor: (authMode === 'signup' && !isNicknameAvailable) ? 'not-allowed' : 'pointer', fontSize: '1rem' 
                  }}
                >
                  {isAuthLoading ? '처리 중...' : (authMode === 'login' ? '로그인' : '회원가입 완료')}
                </button>
              </form>
            </div>
          </div>
        )
      }
      </main>
    </div>
  );
};

export default App;
