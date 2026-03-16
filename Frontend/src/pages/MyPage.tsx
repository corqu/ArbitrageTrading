import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { 
  Activity, ShieldAlert, RefreshCw, Repeat
} from 'lucide-react';

interface MyPageProps {
  // ... (기존 props 생략)
  selectedExchange: 'UPBIT' | 'BINANCE' | 'BITHUMB' | null;
  setSelectedExchange: (val: 'UPBIT' | 'BINANCE' | 'BITHUMB' | null) => void;
  // ...
}

// ...

const MyPage: React.FC<MyPageProps> = ({ 
  // ...
}) => {
  // ... (기존 로직 생략)

  return (
    <div className="mypage-content" style={{ maxWidth: '1000px', margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      <div className="card" style={{ padding: '2.5rem' }}>
        {/* ... (기존 프로필 섹션 생략) */}

        <div style={{ borderTop: '1px solid var(--border-color)', paddingTop: '2rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}><ShieldAlert size={20} color="var(--accent-color)" /><h4 style={{ margin: 0 }}>연동된 거래소</h4></div>
            {!showAddExchangeModal && <button onClick={() => setShowAddExchangeModal(true)} style={{ padding: '0.4rem 0.8rem', background: 'var(--accent-color)', color: '#000', border: 'none', borderRadius: '0.4rem', fontWeight: 700, cursor: 'pointer', fontSize: '0.8rem' }}>거래소 연동하기</button>}
          </div>
          {showAddExchangeModal && (
            <div style={{ padding: '1.5rem', background: 'rgba(56, 189, 248, 0.05)', borderRadius: '0.75rem', border: '1px solid rgba(56, 189, 248, 0.2)', marginBottom: '1.5rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.2rem' }}><span style={{ fontWeight: 700 }}>새 거래소 연동</span><button onClick={() => { setShowAddExchangeModal(false); setSelectedExchange(null); }} style={{ background: 'transparent', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}>닫기</button></div>
              <div style={{ display: 'flex', gap: '0.8rem', marginBottom: '1.2rem' }}>
                {['UPBIT', 'BITHUMB', 'BINANCE'].map(ex => (
                  <button 
                    key={ex} 
                    onClick={() => setSelectedExchange(ex as any)} 
                    style={{ flex: 1, padding: '0.6rem', borderRadius: '0.4rem', border: `1px solid ${selectedExchange === ex ? 'var(--accent-color)' : 'var(--border-color)'}`, background: selectedExchange === ex ? 'rgba(56, 189, 248, 0.1)' : 'transparent', color: selectedExchange === ex ? 'var(--accent-color)' : 'var(--text-secondary)', fontWeight: 600 }}
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
                  <div style={{ width: '32px', height: '32px', background: ex === 'UPBIT' ? '#0066ff' : ex === 'BITHUMB' ? '#ff6600' : '#f3ba2f', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 900, color: 'white', fontSize: '0.7rem' }}>{ex[0]}</div>
                  <span style={{ fontWeight: 700 }}>{ex}</span>
                  <span style={{ fontSize: '0.75rem', color: 'var(--success)' }}>● 연결됨</span>
                </div>
                <button onClick={() => handleDeleteExchange(ex)} style={{ padding: '0.3rem 0.8rem', background: 'rgba(239, 68, 68, 0.1)', color: '#ef4444', border: '1px solid rgba(239, 68, 68, 0.2)', borderRadius: '0.4rem', cursor: 'pointer', fontSize: '0.75rem' }}>연동 해제</button>
              </div>
            ))}
          </div>
        </div>
      </div>
      {/* ... (이하 자산/주문 내역 섹션 동일) */}

      <div className="card assets-orders-container" style={{ padding: 0, overflow: 'hidden' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 4px 1fr', background: 'var(--card-bg)' }}>
          <div style={{ padding: '2rem' }}>
            <h4 style={{ marginBottom: '1.5rem' }}>업비트 자산</h4>
            {isLoadingAssets ? <RefreshCw className="animate-spin" /> : assetData && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                <div style={{ background: 'rgba(0,0,0,0.2)', padding: '1rem', borderRadius: '0.5rem' }}><span style={{ fontSize: '0.85rem' }}>가용 원화</span><div style={{ fontSize: '1.25rem', fontWeight: 800, color: 'var(--accent-color)' }}>{assetData.upbitKrw?.toLocaleString()} KRW</div></div>
                <div className="asset-list" style={{ maxHeight: '200px', overflowY: 'auto' }}>
                  {assetData.upbitBalances?.filter((b: any) => b.currency !== 'KRW').map((b: any) => (
                    <div key={b.currency} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.6rem', borderBottom: '1px solid var(--border-color)' }}><span>{b.currency}</span><span>{parseFloat(b.balance).toFixed(4)}</span></div>
                  ))}
                </div>
              </div>
            )}
          </div>
          <div style={{ background: 'var(--border-color)' }} />
          <div style={{ padding: '2rem' }}>
            <h4 style={{ marginBottom: '1.5rem' }}>바이낸스 선물 자산</h4>
            {assetData && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                <div style={{ background: 'rgba(0,0,0,0.2)', padding: '1rem', borderRadius: '0.5rem' }}><span style={{ fontSize: '0.85rem' }}>가용 증거금</span><div style={{ fontSize: '1.25rem', fontWeight: 800, color: '#f3ba2f' }}>{assetData.binanceUsdt?.toFixed(2)} USDT</div></div>
                <div style={{ padding: '1rem', background: 'rgba(255,255,255,0.02)' }}>적용 환율: {assetData.usdKrw?.toLocaleString()} KRW</div>
              </div>
            )}
          </div>
        </div>
        <div style={{ borderTop: '4px solid var(--border-color)', padding: '2rem' }}>
          <h4 style={{ marginBottom: '1.5rem' }}>매매 내역</h4>
          <div className="orders-paired-list" style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {tradeOrders.map((pair) => (
              <div key={pair.id} style={{ display: 'grid', gridTemplateColumns: '1fr 4px 1fr', background: 'rgba(0,0,0,0.15)', border: '1px solid var(--border-color)', borderRadius: '0.6rem' }}>
                <div style={{ padding: '0.8rem' }}>{pair.upbit && <div>{pair.upbit.symbol} {pair.upbit.side} @{pair.upbit.price?.toLocaleString()}</div>}</div>
                <div style={{ background: 'var(--border-color)' }} />
                <div style={{ padding: '0.8rem' }}>{pair.binance && <div>{pair.binance.symbol} {pair.binance.side} @{pair.binance.price?.toFixed(2)}</div>}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default MyPage;
