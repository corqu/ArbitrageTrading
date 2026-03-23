import React, { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { 
  TrendingUp, LayoutDashboard, Repeat, ChevronLeft, ChevronRight, User, LogIn
} from 'lucide-react';

interface SidebarProps {
  isLoggedIn: boolean;
  email: string;
  isConnected: boolean;
  handleLogout: () => void;
}

const Sidebar: React.FC<SidebarProps> = ({ isLoggedIn, email, isConnected, handleLogout }) => {
  const [isCollapsed, setIsCollapsed] = useState(false);

  return (
    <nav className={`sidebar ${isCollapsed ? 'collapsed' : ''}`}>
      <div className="sidebar-logo">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.8rem' }}>
          <TrendingUp color="var(--accent-color)" size={28} style={{ minWidth: '28px' }} />
          {!isCollapsed && <span style={{ whiteSpace: 'nowrap', fontWeight: 900, fontSize: '1.2rem', letterSpacing: '-0.5px' }}>김프알람</span>}
        </div>
        <button 
          className="collapse-btn" 
          onClick={() => setIsCollapsed(!isCollapsed)}
          style={{ 
            background: 'rgba(255,255,255,0.05)', 
            border: 'none', 
            color: 'var(--text-secondary)', 
            cursor: 'pointer',
            padding: '6px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderRadius: '6px',
            marginLeft: isCollapsed ? '0' : 'auto',
            transition: 'all 0.2s'
          }}
        >
          {isCollapsed ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}
        </button>
      </div>
      
      <div className="sidebar-menu">
        <NavLink 
          to="/" 
          className={({ isActive }) => `menu-item ${isActive ? 'active' : ''}`}
          title="대시보드"
        >
          <LayoutDashboard size={20} style={{ minWidth: '20px' }} />
          {!isCollapsed && <span>대시보드</span>}
        </NavLink>
        
        <NavLink 
          to="/arbitrage" 
          className={({ isActive }) => `menu-item ${isActive ? 'active' : ''}`}
          title="차익거래"
        >
          <Repeat size={20} style={{ minWidth: '20px' }} />
          {!isCollapsed && <span>차익거래</span>}
        </NavLink>
      </div>
      
      <div className="sidebar-footer">
        <div className="auth-section" style={{ 
          padding: isCollapsed ? '1rem 0' : '1rem 0.8rem', 
          borderTop: '1px solid var(--border-color)', 
          marginBottom: '0.5rem', 
          marginRight: isCollapsed ? '0' : '0.4rem',
          display: 'flex',
          flexDirection: 'column',
          alignItems: isCollapsed ? 'center' : 'stretch'
        }}>
          {isLoggedIn ? (
            isCollapsed ? (
              <NavLink to="/mypage" title="마이페이지" style={{ color: 'var(--accent-color)', padding: '0.5rem', display: 'flex' }}>
                <User size={22} />
              </NavLink>
            ) : (
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
            )
          ) : (
            <NavLink 
              to="/auth" 
              title="로그인"
              style={{ 
                width: isCollapsed ? 'auto' : '100%', 
                padding: isCollapsed ? '0.6rem' : '0.6rem 0.4rem', 
                background: 'var(--accent-color)', 
                color: '#000', 
                borderRadius: '0.4rem', 
                display: 'flex', 
                alignItems: 'center', 
                justifyContent: 'center', 
                gap: '0.5rem', 
                textDecoration: 'none' 
              }}
            >
              <LogIn size={20} />
              {!isCollapsed && <span style={{ fontWeight: 700, fontSize: '0.85rem' }}>로그인 / 회원가입</span>}
            </NavLink>
          )}
        </div>
        {!isCollapsed && (
          <div className="status-group">
            <div className={`status-dot ${isConnected ? 'online' : 'offline'}`} />
            <span>{isConnected ? '실시간 연결됨' : '연결 끊김'}</span>
          </div>
        )}
      </div>
    </nav>
  );
};

export default Sidebar;
