import React from 'react';
import { NavLink } from 'react-router-dom';
import { 
  TrendingUp, LayoutDashboard, Repeat, LogOut
} from 'lucide-react';

interface SidebarProps {
  isLoggedIn: boolean;
  email: string;
  isConnected: boolean;
  handleLogout: () => void;
}

const Sidebar: React.FC<SidebarProps> = ({ isLoggedIn, email, isConnected, handleLogout }) => {
  return (
    <nav className="sidebar">
      <div className="sidebar-logo">
        <TrendingUp color="var(--accent-color)" size={28} />
        <span>김프알람</span>
      </div>
      
      <div className="sidebar-menu">
        <NavLink 
          to="/" 
          className={({ isActive }) => `menu-item ${isActive ? 'active' : ''}`}
        >
          <LayoutDashboard size={20} />
          <span>대시보드</span>
        </NavLink>
        
        <NavLink 
          to="/arbitrage" 
          className={({ isActive }) => `menu-item ${isActive ? 'active' : ''}`}
        >
          <Repeat size={20} />
          <span>차익거래</span>
        </NavLink>
      </div>
      
      <div className="sidebar-footer">
        <div className="auth-section" style={{ padding: '1rem 0.8rem', borderTop: '1px solid var(--border-color)', marginBottom: '0.5rem', marginRight: '0.4rem' }}>
          {isLoggedIn ? (
            <div className="user-profile" style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', paddingLeft: '0.2rem' }}>{email}</div>
              <div style={{ display: 'flex', gap: '0.4rem' }}>
                <NavLink 
                  to="/mypage" 
                  style={{ flex: 1, padding: '0.5rem 0.2rem', fontSize: '0.8rem', background: 'rgba(56, 189, 248, 0.1)', color: 'var(--accent-color)', border: '1px solid rgba(56, 189, 248, 0.2)', borderRadius: '0.4rem', cursor: 'pointer', fontWeight: 600, textDecoration: 'none', textAlign: 'center', boxSizing: 'border-box' }}
                >
                  마이페이지
                </NavLink>
                <button 
                  onClick={handleLogout} 
                  style={{ flex: 1, padding: '0.5rem 0.2rem', fontSize: '0.8rem', background: 'rgba(239, 68, 68, 0.1)', color: '#ef4444', border: '1px solid rgba(239, 68, 68, 0.2)', borderRadius: '0.4rem', cursor: 'pointer', fontWeight: 600, boxSizing: 'border-box' }}
                >
                  로그아웃
                </button>
              </div>
            </div>
          ) : (
            <NavLink 
              to="/auth" 
              style={{ width: '100%', padding: '0.6rem 0.4rem', fontSize: '0.85rem', background: 'var(--accent-color)', color: '#000', border: 'none', borderRadius: '0.4rem', cursor: 'pointer', fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem', textDecoration: 'none', boxSizing: 'border-box' }}
            >
              로그인 / 회원가입
            </NavLink>
          )}
        </div>
        <div className="status-group">
          <div className={`status-dot ${isConnected ? 'online' : 'offline'}`} />
          <span>{isConnected ? '실시간 연결됨' : '연결 끊김'}</span>
        </div>
      </div>
    </nav>
  );
};

export default Sidebar;
