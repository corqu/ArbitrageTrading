import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { 
  Activity, ShieldAlert, RefreshCw, Repeat, User as UserIcon, ChevronDown, Play, Square, Settings, Save
} from 'lucide-react';
import { SubscribedBot } from '../types';

interface MyPageProps {
  // ... (rest of props)

  nickname: string;
  handleUpdateProfile: (e: React.FormEvent) => Promise<void>;
  newNickname: string;
  setNewNickname: (val: string) => void;
  newPassword: string;
  setNewPassword: (val: string) => void;
  isEditingProfile: boolean;
  setIsEditingProfile: (val: boolean) => void;
  connectedExchanges: string[];
  handleDeleteExchange: (exchange: string) => Promise<void>;
  showAddExchangeModal: boolean;
  setShowAddExchangeModal: (val: boolean) => void;
  selectedExchange: 'UPBIT' | 'BITHUMB' | 'BINANCE' | 'BYBIT' | null;
  setSelectedExchange: (val: 'UPBIT' | 'BITHUMB' | 'BINANCE' | 'BYBIT' | null) => void;
  apiKey: string;
  setApiKey: (val: string) => void;
  apiSecret: string;
  setApiSecret: (val: string) => void;
  handleConnectExchange: (e: React.FormEvent) => Promise<void>;
}

const MyPage: React.FC<MyPageProps> = ({ 
  email, nickname, handleUpdateProfile, newNickname, setNewNickname,
  newPassword, setNewPassword, isEditingProfile, setIsEditingProfile,
  connectedExchanges, handleDeleteExchange, showAddExchangeModal, setShowAddExchangeModal,
  selectedExchange, setSelectedExchange, apiKey, setApiKey, apiSecret, setApiSecret,
  handleConnectExchange
}) => {
  // 국내 자산 상태
  const [domesticExchange, setDomesticExchange] = useState<'UPBIT' | 'BITHUMB'>('UPBIT');
  const [domesticAssets, setDomesticAssets] = useState<any>(null);
  const [isLoadingDomestic, setIsLoadingDomestic] = useState(false);

  // 해외 자산 상태
  const [foreignExchange, setForeignExchange] = useState<'BINANCE' | 'BYBIT'>('BINANCE');
  const [foreignAssets, setForeignAssets] = useState<any>(null);
  const [isLoadingForeign, setIsLoadingForeign] = useState(false);

  const [tradeOrders, setTradeOrders] = useState<any[]>([]);
  const [subscribedBots, setSubscribedBots] = useState<SubscribedBot[]>([]);
  const [isUpdatingBot, setIsUpdatingBot] = useState<string | null>(null);

  const fetchAssetData = async (exchange: string, setAssetData: any, setIsLoading: any) => {
    // ... (existing code)
  };

  const fetchOrders = async () => {
    try {
      const res = await axios.get('/api/user/credentials/orders');
      setTradeOrders(res.data);
    } catch (e) {}
  };

  const fetchSubscribedBots = async () => {
    try {
      const res = await axios.get('/api/user/subscriptions');
      setSubscribedBots(res.data);
    } catch (e) {}
  };

  const toggleBotActive = async (symbol: string) => {
    try {
      await axios.post(`/api/user/subscriptions/${symbol}/toggle`);
      fetchSubscribedBots();
    } catch (e) {
      alert('봇 상태 변경에 실패했습니다.');
    }
  };

  const updateBotSettings = async (symbol: string, targetRatio: number, tradeAmount: number) => {
    setIsUpdatingBot(symbol);
    try {
      await axios.put(`/api/user/subscriptions/${symbol}`, { targetRatio, tradeAmount });
      alert(`${symbol} 봇 설정이 저장되었습니다.`);
      fetchSubscribedBots();
    } catch (e) {
      alert('봇 설정 저장에 실패했습니다.');
    } finally {
      setIsUpdatingBot(null);
    }
  };

  useEffect(() => {
    fetchAssetData(domesticExchange, setDomesticAssets, setIsLoadingDomestic);
  }, [domesticExchange, connectedExchanges]);

  useEffect(() => {
    fetchAssetData(foreignExchange, setForeignAssets, setIsLoadingForeign);
  }, [foreignExchange, connectedExchanges]);

  useEffect(() => {
    fetchOrders();
    fetchSubscribedBots();
  }, [connectedExchanges]);

  const renderAssetBox = (exchange: string, data: any, isLoading: boolean, isDomestic: boolean) => {
    const isConnected = connectedExchanges.some(ex => ex.toUpperCase() === exchange.toUpperCase());

    return (
      <div style={{ background: 'rgba(255,255,255,0.03)', borderRadius: '1rem', padding: '1.5rem', border: '1px solid var(--border-color)', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <div style={{ width: '24px', height: '24px', background: exchange === 'UPBIT' ? '#0066ff' : exchange === 'BITHUMB' ? '#ff6600' : exchange === 'BINANCE' ? '#f3ba2f' : '#00b5ad', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 900, color: 'white', fontSize: '0.6rem' }}>{exchange[0]}</div>
            <select 
              value={exchange} 
              onChange={(e) => isDomestic ? setDomesticExchange(e.target.value as any) : setForeignExchange(e.target.value as any)}
              style={{ background: 'transparent', border: 'none', color: 'white', fontWeight: 700, fontSize: '0.9rem', cursor: 'pointer', outline: 'none' }}
            >
              {isDomestic ? (
                <>
                  <option value="UPBIT" style={{ background: '#1a1a1a' }}>UPBIT</option>
                  <option value="BITHUMB" style={{ background: '#1a1a1a' }}>BITHUMB</option>
                </>
              ) : (
                <>
                  <option value="BINANCE" style={{ background: '#1a1a1a' }}>BINANCE</option>
                  <option value="BYBIT" style={{ background: '#1a1a1a' }}>BYBIT</option>
                </>
              )}
            </select>
          </div>
          <span style={{ fontSize: '0.75rem', color: isConnected ? 'var(--success)' : 'var(--text-secondary)' }}>
            {isConnected ? '● 연결됨' : '○ 미연동'}
          </span>
        </div>

        {isLoading ? (
          <div style={{ textAlign: 'center', padding: '2rem' }}>
            <RefreshCw className="animate-spin" size={24} style={{ color: 'var(--accent-color)' }} />
          </div>
        ) : isConnected && data ? (
          <>
            <div style={{ background: 'rgba(0,0,0,0.2)', padding: '1rem', borderRadius: '0.6rem', display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                {isDomestic ? '보유 원화' : '보유 USDT'}
              </span>
              <div style={{ fontSize: '1.2rem', fontWeight: 800, color: !isDomestic ? '#f3ba2f' : 'var(--accent-color)' }}>
                {data.mainBalance?.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })} 
                <span style={{ fontSize: '0.8rem', marginLeft: '0.3rem', fontWeight: 600 }}>{isDomestic ? 'KRW' : 'USDT'}</span>
              </div>
              {!isDomestic && data.krwBalance && (
                <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', borderTop: '1px solid rgba(255,255,255,0.05)', paddingTop: '0.4rem', marginTop: '0.2rem' }}>
                  ≈ {data.krwBalance.toLocaleString()} KRW (원화 가치)
                </div>
              )}
            </div>

            <div className="asset-list" style={{ maxHeight: '200px', overflowY: 'auto', fontSize: '0.8rem' }}>
              {data.balances && data.balances.length > 0 ? data.balances.map((b: any) => (
                <div key={b.currency} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem 0.2rem', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                  <span style={{ color: 'var(--text-secondary)' }}>{b.currency}</span>
                  <span style={{ fontWeight: 600 }}>{parseFloat(b.balance).toLocaleString(undefined, { maximumFractionDigits: 8 })}</span>
                </div>
              )) : (
                <div style={{ textAlign: 'center', color: 'var(--text-secondary)', padding: '1rem' }}>보유 자산이 없습니다.</div>
              )}
            </div>
          </>
        ) : (
          <div style={{ textAlign: 'center', padding: '2rem', background: 'rgba(0,0,0,0.1)', borderRadius: '0.6rem', color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
            {isConnected ? '데이터를 불러올 수 없습니다.' : '거래소를 연동해 주세요.'}
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="mypage-content" style={{ maxWidth: '1000px', margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      <div className="card" style={{ padding: '2.5rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1.2rem', marginBottom: '2.5rem' }}>
          <div style={{ background: 'var(--accent-color)', color: '#000', width: '64px', height: '64px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 900, fontSize: '1.5rem' }}>{nickname[0]}</div>
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.8rem' }}>
              <h2 style={{ margin: 0 }}>{nickname}</h2>
              <span style={{ fontSize: '0.8rem', background: 'rgba(255,255,255,0.08)', padding: '0.2rem 0.6rem', borderRadius: '1rem', color: 'var(--text-secondary)' }}>PRO USER</span>
            </div>
            <p style={{ margin: '0.3rem 0 0 0', color: 'var(--text-secondary)', fontSize: '0.9rem' }}>{email}</p>
          </div>
          <button onClick={() => { setIsEditingProfile(!isEditingProfile); setNewNickname(nickname); }} style={{ padding: '0.6rem 1.2rem', background: 'transparent', border: '1px solid var(--border-color)', color: 'white', borderRadius: '0.5rem', cursor: 'pointer', fontSize: '0.85rem' }}>{isEditingProfile ? '취소' : '프로필 수정'}</button>
        </div>

        {isEditingProfile && (
          <form onSubmit={handleUpdateProfile} style={{ padding: '2rem', background: 'rgba(255,255,255,0.02)', borderRadius: '1rem', marginBottom: '2.5rem', border: '1px solid var(--border-color)' }}>
            <h4 style={{ marginTop: 0, marginBottom: '1.5rem' }}>회원 정보 수정</h4>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.2rem' }}>
              <div><label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '0.5rem' }}>닉네임</label><input type="text" value={newNickname} onChange={(e) => setNewNickname(e.target.value)} style={{ width: '100%', padding: '0.75rem', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: 'white', borderRadius: '0.5rem' }} /></div>
              <div><label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '0.5rem' }}>새 비밀번호 (변경 시에만 입력)</label><input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder="••••••••" style={{ width: '100%', padding: '0.75rem', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: 'white', borderRadius: '0.5rem' }} /></div>
              <button type="submit" style={{ padding: '0.8rem', background: 'var(--accent-color)', color: '#000', border: 'none', borderRadius: '0.5rem', fontWeight: 800, marginTop: '0.5rem' }}>정보 저장하기</button>
            </div>
          </form>
        )}

        <div style={{ borderTop: '1px solid var(--border-color)', paddingTop: '2rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}><ShieldAlert size={20} color="var(--accent-color)" /><h4 style={{ margin: 0 }}>연동된 거래소</h4></div>
            {!showAddExchangeModal && <button onClick={() => setShowAddExchangeModal(true)} style={{ padding: '0.4rem 0.8rem', background: 'var(--accent-color)', color: '#000', border: 'none', borderRadius: '0.4rem', fontWeight: 700, cursor: 'pointer', fontSize: '0.8rem' }}>거래소 연동하기</button>}
          </div>
          {showAddExchangeModal && (
            <div style={{ padding: '1.5rem', background: 'rgba(56, 189, 248, 0.05)', borderRadius: '0.75rem', border: '1px solid rgba(56, 189, 248, 0.2)', marginBottom: '1.5rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.2rem' }}><span style={{ fontWeight: 700 }}>새 거래소 연동</span><button onClick={() => { setShowAddExchangeModal(false); setSelectedExchange(null); }} style={{ background: 'transparent', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}>닫기</button></div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.8rem', marginBottom: '1.2rem' }}>
                {['UPBIT', 'BITHUMB', 'BINANCE', 'BYBIT'].map(ex => (
                  <button 
                    key={ex} 
                    onClick={() => setSelectedExchange(ex as any)} 
                    style={{ flex: '1 1 calc(50% - 0.4rem)', padding: '0.6rem', borderRadius: '0.4rem', border: `1px solid ${selectedExchange === ex ? 'var(--accent-color)' : 'var(--border-color)'}`, background: selectedExchange === ex ? 'rgba(56, 189, 248, 0.1)' : 'transparent', color: selectedExchange === ex ? 'var(--accent-color)' : 'var(--text-secondary)', fontWeight: 600 }}
                  >
                    {ex}
                  </button>
                ))}
              </div>
              {selectedExchange && (
                <form onSubmit={handleConnectExchange} style={{ display: 'flex', flexDirection: 'column', gap: '0.8rem' }}>
                  <input type="text" placeholder="Access Key (API Key)" value={apiKey} onChange={(e) => setApiKey(e.target.value)} required style={{ width: '100%', padding: '0.6rem', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: 'white', borderRadius: '0.4rem' }} />
                  <input type="password" placeholder="Secret Key" value={apiSecret} onChange={(e) => setApiSecret(e.target.value)} required style={{ width: '100%', padding: '0.6rem', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: 'white', borderRadius: '0.4rem' }} />
                  <button type="submit" style={{ padding: '0.6rem', background: 'var(--accent-color)', color: '#000', border: 'none', borderRadius: '0.4rem', fontWeight: 700 }}>연동 완료</button>
                </form>
              )}
            </div>
          )}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.8rem' }}>
            {connectedExchanges.map((ex) => (
              <div key={ex} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '1rem 1.5rem', background: 'rgba(255,255,255,0.03)', borderRadius: '0.75rem', border: '1px solid var(--border-color)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.8rem' }}>
                  <div style={{ width: '32px', height: '32px', background: ex === 'UPBIT' ? '#0066ff' : ex === 'BITHUMB' ? '#ff6600' : ex === 'BINANCE' ? '#f3ba2f' : '#00b5ad', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 900, color: 'white', fontSize: '0.7rem' }}>{ex[0]}</div>
                  <span style={{ fontWeight: 700 }}>{ex}</span>
                  <span style={{ fontSize: '0.75rem', color: 'var(--success)' }}>● 연결됨</span>
                </div>
                <button onClick={() => handleDeleteExchange(ex)} style={{ padding: '0.3rem 0.8rem', background: 'rgba(239, 68, 68, 0.1)', color: '#ef4444', border: '1px solid rgba(239, 68, 68, 0.2)', borderRadius: '0.4rem', cursor: 'pointer', fontSize: '0.75rem' }}>연동 해제</button>
              </div>
            ))}
          </div>
        </div>

        {/* 자동매매 봇 설정 섹션 추가 */}
        <div style={{ borderTop: '1px solid var(--border-color)', paddingTop: '2rem', marginTop: '2rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '1.5rem' }}>
            <Settings size={20} color="var(--accent-color)" />
            <h4 style={{ margin: 0 }}>구독 중인 자동매매 봇</h4>
          </div>
          
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {subscribedBots.length > 0 ? subscribedBots.map((bot) => (
              <div key={bot.symbol} style={{ padding: '1.5rem', background: 'rgba(255,255,255,0.03)', borderRadius: '1rem', border: '1px solid var(--border-color)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.2rem' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.8rem' }}>
                    <div style={{ padding: '0.4rem 0.8rem', background: 'rgba(56, 189, 248, 0.1)', color: 'var(--accent-color)', borderRadius: '0.5rem', fontWeight: 800 }}>{bot.symbol}</div>
                    <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>{bot.domesticExchange} ↔ {bot.foreignExchange}</span>
                  </div>
                  <button 
                    onClick={() => toggleBotActive(bot.symbol)}
                    style={{ 
                      display: 'flex', 
                      alignItems: 'center', 
                      gap: '0.5rem', 
                      padding: '0.5rem 1rem', 
                      borderRadius: '0.5rem', 
                      border: 'none', 
                      background: bot.isActive ? 'rgba(239, 68, 68, 0.1)' : 'rgba(16, 185, 129, 0.1)', 
                      color: bot.isActive ? '#ef4444' : '#10b981', 
                      fontWeight: 700, 
                      cursor: 'pointer' 
                    }}
                  >
                    {bot.isActive ? <Square size={14} fill="currentColor" /> : <Play size={14} fill="currentColor" />}
                    {bot.isActive ? '중지' : '시작'}
                  </button>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 100px', gap: '1rem', alignItems: 'flex-end' }}>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    <label style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>목표 차익 (%)</label>
                    <input 
                      type="number" 
                      defaultValue={bot.targetRatio} 
                      onBlur={(e) => bot.targetRatio = parseFloat(e.target.value)}
                      style={{ background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: 'white', padding: '0.6rem', borderRadius: '0.4rem', outline: 'none' }} 
                    />
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    <label style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>회당 매매 금액 (USDT)</label>
                    <input 
                      type="number" 
                      defaultValue={bot.tradeAmount} 
                      onBlur={(e) => bot.tradeAmount = parseFloat(e.target.value)}
                      style={{ background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: 'white', padding: '0.6rem', borderRadius: '0.4rem', outline: 'none' }} 
                    />
                  </div>
                  <button 
                    onClick={() => updateBotSettings(bot.symbol, bot.targetRatio, bot.tradeAmount)}
                    disabled={isUpdatingBot === bot.symbol}
                    style={{ 
                      height: '38px', 
                      background: 'rgba(56, 189, 248, 0.1)', 
                      color: 'var(--accent-color)', 
                      border: '1px solid rgba(56, 189, 248, 0.2)', 
                      borderRadius: '0.4rem', 
                      display: 'flex', 
                      alignItems: 'center', 
                      justifyContent: 'center', 
                      gap: '0.4rem', 
                      cursor: 'pointer',
                      fontWeight: 600
                    }}
                  >
                    <Save size={14} className={isUpdatingBot === bot.symbol ? 'animate-spin' : ''} />
                    저장
                  </button>
                </div>
              </div>
            )) : (
              <div style={{ textAlign: 'center', padding: '2rem', background: 'rgba(0,0,0,0.1)', borderRadius: '1rem', color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
                구독 중인 봇이 없습니다. 차익거래 페이지에서 봇을 구독해 주세요.
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="card assets-orders-container" style={{ padding: '2rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <Activity size={20} color="var(--accent-color)" />
            <h4 style={{ margin: 0 }}>실시간 자산 현황</h4>
          </div>
          <button 
            onClick={() => {
              fetchAssetData(domesticExchange, setDomesticAssets, setIsLoadingDomestic);
              fetchAssetData(foreignExchange, setForeignAssets, setIsLoadingForeign);
            }}
            disabled={isLoadingDomestic || isLoadingForeign}
            style={{ 
              background: 'transparent', 
              border: '1px solid var(--border-color)', 
              color: 'var(--text-secondary)', 
              padding: '0.4rem 0.8rem', 
              borderRadius: '0.4rem', 
              display: 'flex', 
              alignItems: 'center', 
              gap: '0.4rem', 
              cursor: 'pointer',
              fontSize: '0.8rem'
            }}
          >
            <RefreshCw size={14} className={(isLoadingDomestic || isLoadingForeign) ? 'animate-spin' : ''} />
            새로고침
          </button>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
          {/* 국내 거래소 영역 */}
          {renderAssetBox(domesticExchange, domesticAssets, isLoadingDomestic, true)}
          
          {/* 해외 거래소 영역 */}
          {renderAssetBox(foreignExchange, foreignAssets, isLoadingForeign, false)}
        </div>
      </div>

      <div className="card" style={{ padding: '2rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '1.5rem' }}>
          <Repeat size={20} color="var(--accent-color)" />
          <h4 style={{ margin: 0 }}>최근 매매 내역</h4>
        </div>
        <div className="orders-paired-list" style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          {tradeOrders.length > 0 ? tradeOrders.map((pair) => (
            <div key={pair.id} style={{ display: 'grid', gridTemplateColumns: '1fr 4px 1fr', background: 'rgba(0,0,0,0.15)', border: '1px solid var(--border-color)', borderRadius: '0.6rem' }}>
              <div style={{ padding: '0.8rem' }}>{pair.upbit && <div>{pair.upbit.symbol} {pair.upbit.side} @{pair.upbit.price?.toLocaleString()}</div>}</div>
              <div style={{ background: 'var(--border-color)' }} />
              <div style={{ padding: '0.8rem' }}>{pair.binance && <div>{pair.binance.symbol} {pair.binance.side} @{pair.binance.price?.toFixed(2)}</div>}</div>
            </div>
          )) : <div style={{ textAlign: 'center', color: 'var(--text-secondary)', padding: '2rem' }}>아직 매매 내역이 없습니다.</div>}
        </div>
      </div>
    </div>
  );
};

export default MyPage;
