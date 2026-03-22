import React, { useState, useEffect } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";
import { RefreshCw } from "lucide-react";

import Sidebar from "./components/Sidebar";
import axios, { ensureCsrfToken } from "./lib/api";
import Dashboard from "./pages/Dashboard";
import Arbitrage from "./pages/Arbitrage";
import MyPage from "./pages/MyPage";
import AuthPage from "./pages/AuthPage";
import type { KimchPremium } from "./types";
import "./App.css";

const App: React.FC = () => {
  const [kimpList, setKimpList] = useState<KimchPremium[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [selectedDomesticExchange, setSelectedDomesticExchange] = useState<
    "UPBIT" | "BITHUMB"
  >("UPBIT");
  const [selectedForeignExchange, setSelectedForeignExchange] = useState<
    "BINANCE" | "BYBIT"
  >("BINANCE");
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [authMode, setAuthMode] = useState<"login" | "signup">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [nickname, setNickname] = useState("");
  const [loginError, setLoginError] = useState<string | null>(null);
  const [isAuthLoading, setIsAuthLoading] = useState(false);
  const [isEditingProfile, setIsEditingProfile] = useState(false);
  const [newNickname, setNewNickname] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [connectedExchanges, setConnectedExchanges] = useState<string[]>([]);
  const [showAddExchangeModal, setShowAddExchangeModal] = useState(false);
  const [selectedExchange, setSelectedExchange] = useState<
    "UPBIT" | "BITHUMB" | "BINANCE" | "BYBIT" | null
  >(null);
  const [apiKey, setApiKey] = useState("");
  const [apiSecret, setApiSecret] = useState("");
  const [isNicknameChecked, setIsNicknameChecked] = useState(false);
  const [isNicknameAvailable, setIsNicknameAvailable] = useState(false);
  const logoutTimerRef = React.useRef<number | null>(null);

  const startLogoutTimer = (seconds: number) => {
    if (logoutTimerRef.current) window.clearTimeout(logoutTimerRef.current);
    if (seconds <= 0) return;
    logoutTimerRef.current = window.setTimeout(() => {
      alert("로그인 세션이 만료되어 로그아웃되었습니다.");
      void handleLogout();
    }, seconds * 1000);
  };

  const handleLogout = async () => {
    try {
      await axios.post("/api/auth/logout");
    } catch (err) {
      console.error("Logout failed", err);
    } finally {
      setIsLoggedIn(false);
      setEmail("");
      setNickname("");
      setConnectedExchanges([]);
      if (logoutTimerRef.current) {
        window.clearTimeout(logoutTimerRef.current);
        logoutTimerRef.current = null;
      }
    }
  };

  const checkAuth = async () => {
    try {
      const response = await axios.get("/api/auth/me");
      if (response.data.nickname) {
        setIsLoggedIn(true);
        setNickname(response.data.nickname);
        setEmail(response.data.email || "");
        if (response.data.expiresIn) startLogoutTimer(response.data.expiresIn);
        fetchConnectedExchanges();
      }
    } catch (err) {
      console.log("No active session");
    } finally {
      setLoading(false);
    }
  };

  const fetchConnectedExchanges = async () => {
    try {
      const response = await axios.get("/api/user/credentials/list");
      setConnectedExchanges(response.data);
    } catch (err) {}
  };

  const fetchData = async () => {
    try {
      const kimpRes = await axios.get<KimchPremium[]>("/api/kimp/current");
      setKimpList(kimpRes.data);
    } catch (err) {
      console.error("Failed to fetch kimp data", err);
    }
  };

  useEffect(() => {
    const initializeApp = async () => {
      try {
        await ensureCsrfToken();
      } catch (err) {
        console.error("Failed to initialize CSRF token", err);
      } finally {
        await checkAuth();
        await fetchData();
      }
    };

    void initializeApp();

    const stompClient = new Client({
      webSocketFactory: () => new SockJS("/ws-stomp"),
      reconnectDelay: 5000,
      onConnect: () => {
        setIsConnected(true);
        const dom = selectedDomesticExchange === "UPBIT" ? "ub" : "bt";
        const forEx = selectedForeignExchange === "BINANCE" ? "bn" : "bb";
        const topic = `/topic/kimp/${dom}-${forEx}`;

        stompClient.subscribe(topic, (msg) => {
          if (msg.body) {
            try {
              const data = JSON.parse(msg.body);
              setKimpList((prev) => {
                const foreignExchange =
                  selectedForeignExchange === "BINANCE" ? "BINANCE_FUTURES" : "BYBIT_FUTURES";
                const updates = (Array.isArray(data) ? data : [data]).map((item: Partial<KimchPremium>) => ({
                  symbol: item.symbol ?? "",
                  domesticExchange: selectedDomesticExchange,
                  foreignExchange,
                  ratio: item.standardRatio ?? item.ratio ?? 0,
                  standardRatio: item.standardRatio ?? null,
                  entryRatio: item.entryRatio ?? item.standardRatio ?? null,
                  exitRatio: item.exitRatio ?? item.standardRatio ?? null,
                  fundingRate: item.fundingRate ?? null,
                  adjustedApr: item.adjustedApr ?? null,
                  liquidationPrice: item.liquidationPrice ?? null,
                  tradeVolume: item.tradeVolume ?? null,
                }));
                const next = [...prev];

                updates.forEach((newItem: KimchPremium) => {
                  const idx = next.findIndex(
                    (item) =>
                      item.symbol === newItem.symbol &&
                      item.domesticExchange === newItem.domesticExchange &&
                      item.foreignExchange === newItem.foreignExchange,
                  );
                  if (idx !== -1) {
                    next[idx] = { ...next[idx], ...newItem };
                  } else {
                    next.push(newItem);
                  }
                });
                return [...next];
              });
              setLoading(false);
            } catch (e) {
              console.error("WS parsing error:", e);
            }
          }
        });
      },
      onDisconnect: () => setIsConnected(false),
    });
    stompClient.activate();
    return () => {
      stompClient.deactivate();
      if (logoutTimerRef.current) window.clearTimeout(logoutTimerRef.current);
    };
  }, [selectedDomesticExchange, selectedForeignExchange]);

  const handleUpdateProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await axios.put("/api/auth/profile", {
        nickname: newNickname,
        password: newPassword,
      });
      alert("프로필이 수정되었습니다.");
      setNickname(newNickname);
      setIsEditingProfile(false);
    } catch (err) {
      alert("수정 실패");
    }
  };

  const handleConnectExchange = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await axios.post("/api/user/credentials/bind", {
        exchange: selectedExchange,
        accessKey: apiKey,
        secretKey: apiSecret,
      });
      alert(`${selectedExchange} 연동 성공`);
      setShowAddExchangeModal(false);
      fetchConnectedExchanges();
    } catch (err) {
      alert("연동 실패");
    }
  };

  const handleDeleteExchange = async (exchange: string) => {
    if (!confirm("연동 해제하시겠습니까?")) return;
    try {
      await axios.delete(`/api/user/credentials/unbind/${exchange}`);
      fetchConnectedExchanges();
    } catch (err) {
      alert("해제 실패");
    }
  };

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsAuthLoading(true);
    try {
      await axios.post("/api/auth/login", { email, password });
      await checkAuth();
    } catch (err: any) {
      setLoginError(err.response?.data?.message || err.response?.data || "로그인 실패");
    } finally {
      setIsAuthLoading(false);
    }
  };

  const handleSignup = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsAuthLoading(true);
    try {
      await axios.post("/api/auth/signup", { email, password, nickname });
      alert("회원가입 완료");
      setAuthMode("login");
    } catch (err: any) {
      setLoginError(err.response?.data?.message || err.response?.data || "회원가입 실패");
    } finally {
      setIsAuthLoading(false);
    }
  };

  const checkNicknameAvailability = async () => {
    try {
      const res = await axios.get(
        `/api/auth/check-nickname?nickname=${nickname}`,
      );
      setIsNicknameAvailable(res.data.available);
      setIsNicknameChecked(true);
    } catch (err) {}
  };

  if (loading)
    return (
      <div className="dashboard-loading">
        <RefreshCw className="animate-spin" size={48} />
        <p>서버 연결 중...</p>
      </div>
    );

  return (
    <BrowserRouter>
      <div className="app-container">
        <Sidebar
          isLoggedIn={isLoggedIn}
          email={email}
          isConnected={isConnected}
          handleLogout={handleLogout}
        />

        <main className="main-content">
          <Routes>
            <Route path="/" element={
              <Dashboard
                kimpList={kimpList}
                fetchData={fetchData}
                selectedDomesticExchange={selectedDomesticExchange}
                setSelectedDomesticExchange={setSelectedDomesticExchange}
                selectedForeignExchange={selectedForeignExchange}
                setSelectedForeignExchange={setSelectedForeignExchange}
                isConnected={isConnected}
              />
            } />
            <Route
              path="/arbitrage"
              element={
                <Arbitrage
                  kimpList={kimpList}
                  selectedDomesticExchange={selectedDomesticExchange}
                  setSelectedDomesticExchange={setSelectedDomesticExchange}
                  selectedForeignExchange={selectedForeignExchange}
                  setSelectedForeignExchange={setSelectedForeignExchange}
                  isLoggedIn={isLoggedIn}
                  connectedExchanges={connectedExchanges}
                />
              }
            />
            <Route
              path="/auth"
              element={
                isLoggedIn ? (
                  <Navigate to="/" />
                ) : (
                  <AuthPage
                    authMode={authMode}
                    setAuthMode={setAuthMode}
                    email={email}
                    setEmail={setEmail}
                    password={password}
                    setPassword={setPassword}
                    nickname={nickname}
                    setNickname={setNickname}
                    handleLogin={handleLogin}
                    handleSignup={handleSignup}
                    checkNicknameAvailability={checkNicknameAvailability}
                    isNicknameChecked={isNicknameChecked}
                    isNicknameAvailable={isNicknameAvailable}
                    setIsNicknameChecked={setIsNicknameChecked}
                    setIsNicknameAvailable={setIsNicknameAvailable}
                    loginError={loginError}
                    isAuthLoading={isAuthLoading}
                  />
                )
              }
            />
            <Route
              path="/mypage"
              element={
                isLoggedIn ? (
                  <MyPage
                    kimpList={kimpList}
                    email={email}
                    nickname={nickname}
                    handleUpdateProfile={handleUpdateProfile}
                    newNickname={newNickname}
                    setNewNickname={setNewNickname}
                    newPassword={newPassword}
                    setNewPassword={setNewPassword}
                    isEditingProfile={isEditingProfile}
                    setIsEditingProfile={setIsEditingProfile}
                    connectedExchanges={connectedExchanges}
                    handleDeleteExchange={handleDeleteExchange}
                    showAddExchangeModal={showAddExchangeModal}
                    setShowAddExchangeModal={setShowAddExchangeModal}
                    selectedExchange={selectedExchange}
                    setSelectedExchange={setSelectedExchange}
                    apiKey={apiKey}
                    setApiKey={setApiKey}
                    apiSecret={apiSecret}
                    setApiSecret={setApiSecret}
                    handleConnectExchange={handleConnectExchange}
                  />
                ) : (
                  <Navigate to="/auth" />
                )
              }
            />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
};

export default App;
