import React from 'react';

interface AuthPageProps {
  authMode: 'login' | 'signup';
  setAuthMode: (mode: 'login' | 'signup') => void;
  email: string;
  setEmail: (val: string) => void;
  password: string;
  setPassword: (val: string) => void;
  nickname: string;
  setNickname: (val: string) => void;
  handleLogin: (e: React.FormEvent) => void;
  handleSignup: (e: React.FormEvent) => void;
  checkNicknameAvailability: () => void;
  isNicknameChecked: boolean;
  isNicknameAvailable: boolean;
  setIsNicknameChecked: (val: boolean) => void;
  setIsNicknameAvailable: (val: boolean) => void;
  loginError: string | null;
  isAuthLoading: boolean;
}

const AuthPage: React.FC<AuthPageProps> = ({
  authMode, setAuthMode, email, setEmail, password, setPassword,
  nickname, setNickname, handleLogin, handleSignup, checkNicknameAvailability,
  isNicknameChecked, isNicknameAvailable, setIsNicknameChecked, setIsNicknameAvailable,
  loginError, isAuthLoading
}) => {
  return (
    <div className="auth-content" style={{ display: 'flex', justifyContent: 'center', alignItems: 'flex-start', paddingTop: '8vh', width: '100%', marginLeft: '-110px' }}>
      <div className="card" style={{ width: '320px', padding: '1.5rem' }}>
        <div style={{ display: 'flex', gap: '0.8rem', marginBottom: '1.5rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '0.8rem' }}>
          <button onClick={() => setAuthMode('login')} style={{ flex: 1, background: 'transparent', border: 'none', color: authMode === 'login' ? 'var(--accent-color)' : 'var(--text-secondary)', fontWeight: 700, cursor: 'pointer', fontSize: '1rem', position: 'relative' }}>
            로그인
            {authMode === 'login' && <div style={{ position: 'absolute', bottom: '-0.9rem', left: 0, width: '100%', height: '2px', background: 'var(--accent-color)' }} />}
          </button>
          <button onClick={() => setAuthMode('signup')} style={{ flex: 1, background: 'transparent', border: 'none', color: authMode === 'signup' ? 'var(--accent-color)' : 'var(--text-secondary)', fontWeight: 700, cursor: 'pointer', fontSize: '1rem', position: 'relative' }}>
            회원가입
            {authMode === 'signup' && <div style={{ position: 'absolute', bottom: '-0.9rem', left: 0, width: '100%', height: '2px', background: 'var(--accent-color)' }} />}
          </button>
        </div>

        <form onSubmit={authMode === 'login' ? handleLogin : handleSignup} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div className="input-group">
            <label style={{ fontSize: '0.8rem', marginBottom: '0.3rem', display: 'block', color: 'var(--text-secondary)' }}>이메일</label>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required style={{ width: '100%', padding: '0.6rem', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: 'white', borderRadius: '0.4rem', fontSize: '0.9rem' }} placeholder="example@email.com" />
          </div>
          
          {authMode === 'signup' && (
            <div className="input-group">
              <label style={{ fontSize: '0.8rem', marginBottom: '0.3rem', display: 'block', color: 'var(--text-secondary)' }}>닉네임</label>
              <div style={{ display: 'flex', gap: '0.4rem' }}>
                <input 
                  type="text" value={nickname} 
                  onChange={(e) => { setNickname(e.target.value); setIsNicknameChecked(false); setIsNicknameAvailable(false); }} 
                  required style={{ flex: 1, padding: '0.6rem', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: 'white', borderRadius: '0.4rem', fontSize: '0.9rem' }} 
                  placeholder="닉네임" 
                />
                <button type="button" onClick={checkNicknameAvailability} style={{ padding: '0 0.8rem', background: 'rgba(255,255,255,0.05)', color: 'white', border: '1px solid var(--border-color)', borderRadius: '0.4rem', cursor: 'pointer', fontSize: '0.8rem' }}>중복확인</button>
              </div>
              {isNicknameChecked && <span style={{ fontSize: '0.7rem', marginTop: '0.2rem', color: isNicknameAvailable ? 'var(--success)' : '#ef4444' }}>{isNicknameAvailable ? '사용 가능' : '사용 중'}</span>}
            </div>
          )}

          <div className="input-group">
            <label style={{ fontSize: '0.8rem', marginBottom: '0.3rem', display: 'block', color: 'var(--text-secondary)' }}>비밀번호</label>
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required style={{ width: '100%', padding: '0.6rem', background: 'rgba(0,0,0,0.2)', border: '1px solid var(--border-color)', color: 'white', borderRadius: '0.4rem', fontSize: '0.9rem' }} placeholder="••••••••" />
          </div>

          {loginError && <div style={{ fontSize: '0.8rem', color: '#ef4444', padding: '0.4rem', background: 'rgba(239, 68, 68, 0.1)', borderRadius: '0.4rem' }}>{loginError}</div>}

          <button type="submit" disabled={isAuthLoading || (authMode === 'signup' && !isNicknameAvailable)} style={{ marginTop: '0.5rem', padding: '0.7rem', borderRadius: '0.4rem', border: 'none', background: (authMode === 'signup' && !isNicknameAvailable) ? 'var(--text-secondary)' : 'var(--accent-color)', color: '#000', fontWeight: 800, cursor: 'pointer', fontSize: '0.95rem' }}>
            {isAuthLoading ? '처리 중...' : (authMode === 'login' ? '로그인' : '회원가입 완료')}
          </button>
        </form>
      </div>
    </div>
  );
};

export default AuthPage;
