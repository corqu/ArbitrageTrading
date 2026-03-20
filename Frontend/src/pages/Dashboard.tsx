import React, { useState, useMemo } from "react";
import axios from "axios";
import {
  RefreshCw,
  BarChart3,
  ChevronDown,
  ChevronUp,
  Search,
} from "lucide-react";
import {
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
} from "recharts";
import type { KimchPremium } from "../types";

interface DashboardProps {
  kimpList: KimchPremium[];
  fetchData: () => void;
  selectedDomesticExchange: "UPBIT" | "BITHUMB";
  setSelectedDomesticExchange: (ex: "UPBIT" | "BITHUMB") => void;
  selectedForeignExchange: "BINANCE" | "BYBIT";
  setSelectedForeignExchange: (ex: "BINANCE" | "BYBIT") => void;
  isConnected: boolean;
}

type SortKey =
  | "symbol"
  | "ratio"
  | "standardRatio"
  | "entryRatio"
  | "exitRatio"
  | "fundingRate"
  | "tradeVolume"
  | "adjustedApr";
type SortOrder = "asc" | "desc";

const Dashboard: React.FC<DashboardProps> = ({
  kimpList,
  selectedDomesticExchange,
  setSelectedDomesticExchange,
  selectedForeignExchange,
  setSelectedForeignExchange,
  isConnected,
}) => {
  const [searchTerm, setSearchTerm] = useState("");
  const [sortKey, setSortKey] = useState<SortKey>("tradeVolume");
  const [sortOrder, setSortOrder] = useState<SortOrder>("desc");
  const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);
  const [selectedRange, setSelectedRange] = useState<string>("-6h");
  const [historyData, setHistoryData] = useState<any[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  const parseChartTime = (value: unknown) => {
    if (value instanceof Date) return value;
    if (typeof value === "number") return new Date(value);
    if (typeof value === "string") {
      const parsed = new Date(value);
      if (!Number.isNaN(parsed.getTime())) return parsed;
    }
    return null;
  };

  const formatChartTimeLabel = (date: Date, range: string) => {
    return range === "-6h" || range === "-24h"
      ? date.toLocaleTimeString([], {
          hour: "2-digit",
          minute: "2-digit",
        })
      : date.toLocaleDateString([], {
          month: "numeric",
          day: "numeric",
          hour: "2-digit",
        });
  };

  const toggleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortOrder(sortOrder === "asc" ? "desc" : "asc");
    } else {
      setSortKey(key);
      setSortOrder("desc");
    }
  };

  const fetchHistory = async (symbol: string, range: string) => {
    setLoadingHistory(true);
    try {
      const foreignEx =
        selectedForeignExchange === "BINANCE"
          ? "BINANCE_FUTURES"
          : "BYBIT_FUTURES";
      const response = await axios.get(
        `/api/kimp/history?symbol=${symbol}&range=${range}&domesticExchange=AVERAGE&foreignExchange=${foreignEx}`,
      );
      const formattedData = response.data.map((item: any, index: number) => {
        const rawTime =
          item.time ?? item.timestamp ?? item.createdAt ?? item.created_at;
        const date = parseChartTime(rawTime);
        const fallbackLabel = `${index + 1}`;

        return {
          id: `${rawTime ?? "point"}-${index}`,
          time: rawTime ?? null,
          timeLabel: date ? formatChartTimeLabel(date, range) : fallbackLabel,
          standardRatio: parseFloat(Number(item.standardRatio ?? item.ratio ?? 0).toFixed(2)),
        };
      });
      setHistoryData(formattedData);
    } catch (error) {
      console.error("기록 조회 실패", error);
    } finally {
      setLoadingHistory(false);
    }
  };

  const filteredList = useMemo(() => {
    const foreignExMatch =
      selectedForeignExchange === "BINANCE"
        ? "BINANCE_FUTURES"
        : "BYBIT_FUTURES";
    return kimpList.filter(
      (item) =>
        item.domesticExchange === selectedDomesticExchange &&
        item.foreignExchange === foreignExMatch &&
        item.symbol.toLowerCase().includes(searchTerm.toLowerCase()),
    );
  }, [kimpList, searchTerm, selectedDomesticExchange, selectedForeignExchange]);

  const sortedKimpList = useMemo(() => {
    return [...filteredList].sort((a, b) => {
      let valA = a[sortKey];
      let valB = b[sortKey];

      if (valA === null || valA === undefined) return 1;
      if (valB === null || valB === undefined) return -1;

      if (typeof valA === "string" && typeof valB === "string") {
        return sortOrder === "asc"
          ? valA.localeCompare(valB)
          : valB.localeCompare(valA);
      }
      return sortOrder === "asc"
        ? (valA as number) - (valB as number)
        : (valB as number) - (valA as number);
    });
  }, [filteredList, sortKey, sortOrder]);

  const gradientOffset = useMemo(() => {
    if (historyData.length === 0) return 0;
    const ratios = historyData.map((i) => i.standardRatio);
    const max = Math.max(...ratios),
      min = Math.min(...ratios);
    if (max <= 0) return 0;
    if (min >= 0) return 1;
    return max / (max - min);
  }, [historyData]);

  const formatVolume = (volume: number | null) => {
    if (!volume) return "-";
    const MILLION = 1000000,
      BILLION = 1000000000,
      TRILLION = 1000000000000;

    if (volume >= TRILLION) {
      return `${Math.floor(volume / BILLION).toLocaleString()}십억`;
    }
    return `${Math.floor(volume / MILLION).toLocaleString()}백만`;
  };

  return (
    <div className="dashboard-content">
      <header
        className="main-header"
        style={{ alignItems: "center", flexWrap: "wrap", gap: "1rem" }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "2rem" }}>
          <div className="header-title">
            <h2>시장 대시보드</h2>
            <p className="subtitle">
              전체 {filteredList.length}개 코인 실시간 현황
            </p>
          </div>

          <div
            className="exchange-selectors"
            style={{
              display: "flex",
              alignItems: "center",
              gap: "1rem",
              background: "rgba(255,255,255,0.05)",
              padding: "0.5rem 1rem",
              borderRadius: "0.75rem",
              border: "1px solid var(--border-color)",
            }}
          >
            <div
              style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}
            >
              <span
                style={{
                  fontSize: "0.8rem",
                  color: "var(--text-secondary)",
                  fontWeight: 600,
                }}
              >
                국내
              </span>
              <select
                value={selectedDomesticExchange}
                onChange={(e) =>
                  setSelectedDomesticExchange(e.target.value as any)
                }
                style={{
                  background: "transparent",
                  color: "white",
                  border: "none",
                  fontWeight: 700,
                  cursor: "pointer",
                  outline: "none",
                }}
              >
                <option value="UPBIT" style={{ background: "#1e293b" }}>
                  UPBIT
                </option>
                <option value="BITHUMB" style={{ background: "#1e293b" }}>
                  BITHUMB
                </option>
              </select>
            </div>

            <div
              style={{
                width: "1px",
                height: "16px",
                background: "var(--border-color)",
              }}
            ></div>

            <div
              style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}
            >
              <span
                style={{
                  fontSize: "0.8rem",
                  color: "var(--text-secondary)",
                  fontWeight: 600,
                }}
              >
                해외
              </span>
              <select
                value={selectedForeignExchange}
                onChange={(e) =>
                  setSelectedForeignExchange(e.target.value as any)
                }
                style={{
                  background: "transparent",
                  color: "white",
                  border: "none",
                  fontWeight: 700,
                  cursor: "pointer",
                  outline: "none",
                }}
              >
                <option value="BINANCE" style={{ background: "#1e293b" }}>
                  BINANCE
                </option>
                <option value="BYBIT" style={{ background: "#1e293b" }}>
                  BYBIT
                </option>
              </select>
            </div>
          </div>
        </div>

        <div
          className="search-box"
          style={{ position: "relative", width: "220px" }}
        >
          <Search
            size={16}
            style={{
              position: "absolute",
              left: "12px",
              top: "50%",
              transform: "translateY(-50%)",
              color: "var(--text-secondary)",
            }}
          />
          <input
            type="text"
            placeholder="코인 검색 (BTC, ETH...)"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{
              width: "100%",
              padding: "0.5rem 1rem 0.5rem 2.4rem",
              borderRadius: "0.5rem",
              background: "rgba(255,255,255,0.05)",
              border: "1px solid var(--border-color)",
              color: "white",
              fontSize: "0.85rem",
            }}
          />
        </div>
      </header>

      <div className="summary-grid">
        <div className="summary-card">
          <span className="label">추적 중인 코인</span>
          <span className="value">{filteredList.length}개</span>
        </div>
        <div className="summary-card">
          <span className="label">평균 김치 프리미엄</span>
          <span className="value">
            {(
              filteredList.reduce((acc, curr) => acc + (curr.standardRatio ?? 0), 0) /
              (filteredList.length || 1)
            ).toFixed(2)}
            %
          </span>
        </div>
        <div className="summary-card">
          <span className="label">실시간 연동</span>
          <span
            className="value"
            style={{ color: isConnected ? "var(--success)" : "var(--danger)" }}
          >
            {isConnected ? "정상" : "연결 끊김"}
          </span>
        </div>
      </div>

      <section className="card">
        <div className="table-container">
          <table>
            <thead>
              <tr>
                <th style={{ width: "50px" }}>차트</th>
                <th
                  onClick={() => toggleSort("symbol")}
                  style={{ cursor: "pointer" }}
                >
                  코인 심볼{" "}
                  {sortKey === "symbol" && (sortOrder === "asc" ? "↑" : "↓")}
                </th>
                <th
                  onClick={() => toggleSort("standardRatio")}
                  style={{ cursor: "pointer" }}
                >
                  표준김프 (%){" "}
                  {sortKey === "standardRatio" && (sortOrder === "asc" ? "↑" : "↓")}
                </th>
                <th
                  onClick={() => toggleSort("entryRatio")}
                  style={{ cursor: "pointer" }}
                >
                  매수기준김프 (%){" "}
                  {sortKey === "entryRatio" && (sortOrder === "asc" ? "↑" : "↓")}
                </th>
                <th
                  onClick={() => toggleSort("exitRatio")}
                  style={{ cursor: "pointer" }}
                >
                  매도기준김프 (%){" "}
                  {sortKey === "exitRatio" && (sortOrder === "asc" ? "↑" : "↓")}
                </th>
                <th
                  onClick={() => toggleSort("fundingRate")}
                  style={{ cursor: "pointer" }}
                >
                  펀딩비 (%){" "}
                  {sortKey === "fundingRate" &&
                    (sortOrder === "asc" ? "↑" : "↓")}
                </th>
                <th
                  onClick={() => toggleSort("tradeVolume")}
                  style={{ cursor: "pointer" }}
                >
                  거래대금 (24h){" "}
                  {sortKey === "tradeVolume" &&
                    (sortOrder === "asc" ? "↑" : "↓")}
                </th>
              </tr>
            </thead>
            <tbody>
              {sortedKimpList.map((item, idx) => (
                <React.Fragment key={`${item.symbol}-${idx}`}>
                  <tr
                    onClick={() => {
                      if (selectedSymbol === item.symbol)
                        setSelectedSymbol(null);
                      else {
                        setSelectedSymbol(item.symbol);
                        fetchHistory(item.symbol, selectedRange);
                      }
                    }}
                    className={`clickable-row ${selectedSymbol === item.symbol ? "selected-row" : ""}`}
                  >
                    <td style={{ textAlign: "center" }}>
                      {selectedSymbol === item.symbol ? (
                        <ChevronUp size={18} color="var(--accent-color)" />
                      ) : (
                        <ChevronDown size={18} />
                      )}
                    </td>
                    <td>
                      <div className="symbol-cell">{item.symbol}</div>
                    </td>
                    <td
                      className={(item.standardRatio ?? 0) >= 0 ? "positive" : "negative"}
                      style={{ fontWeight: 700 }}
                    >
                      {item.standardRatio != null ? item.standardRatio.toFixed(2) : "-"}%
                    </td>
                    <td
                      className={(item.entryRatio ?? 0) >= 0 ? "positive" : "negative"}
                      style={{ fontWeight: 700 }}
                    >
                      {item.entryRatio != null ? item.entryRatio.toFixed(2) : "-"}%
                    </td>
                    <td
                      className={(item.exitRatio ?? 0) >= 0 ? "positive" : "negative"}
                      style={{ fontWeight: 700 }}
                    >
                      {item.exitRatio != null ? item.exitRatio.toFixed(2) : "-"}%
                    </td>
                    <td
                      style={{
                        color:
                          (item.fundingRate || 0) > 0
                            ? "#10b981"
                            : "var(--text-primary)",
                      }}
                    >
                      {item.fundingRate
                        ? (item.fundingRate * 100).toFixed(3) + "%"
                        : "-"}
                    </td>
                    <td
                      style={{
                        color: "var(--text-secondary)",
                        fontSize: "0.9rem",
                      }}
                    >
                      {formatVolume(item.tradeVolume)}
                    </td>
                  </tr>
                  {selectedSymbol === item.symbol && (
                    <tr className="expanded-row">
                      <td colSpan={6}>
                        <div className="expanded-content">
                          <div
                            style={{
                              display: "flex",
                              justifyContent: "space-between",
                              alignItems: "center",
                              marginBottom: "1.5rem",
                            }}
                          >
                            <div
                              className="chart-header"
                              style={{ marginBottom: 0 }}
                            >
                              <BarChart3 size={16} />
                              <span>{item.symbol} 김프 추이 분석</span>
                            </div>
                            <div
                              className="range-selector"
                              style={{
                                display: "flex",
                                gap: "0.4rem",
                                background: "rgba(0,0,0,0.3)",
                                padding: "0.3rem",
                                borderRadius: "0.5rem",
                              }}
                            >
                              {[
                                { l: "6시간", v: "-6h" },
                                { l: "1일", v: "-24h" },
                                { l: "1주일", v: "-7d" },
                                { l: "1개월", v: "-30d" },
                              ].map((btn) => (
                                <button
                                  key={btn.v}
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    setSelectedRange(btn.v);
                                    fetchHistory(item.symbol, btn.v);
                                  }}
                                  style={{
                                    padding: "0.3rem 0.6rem",
                                    fontSize: "0.75rem",
                                    border: "none",
                                    borderRadius: "0.3rem",
                                    background:
                                      selectedRange === btn.v
                                        ? "var(--accent-color)"
                                        : "transparent",
                                    color:
                                      selectedRange === btn.v
                                        ? "#000"
                                        : "var(--text-secondary)",
                                    cursor: "pointer",
                                    fontWeight: 600,
                                  }}
                                >
                                  {btn.l}
                                </button>
                              ))}
                            </div>
                          </div>
                          <div
                            className="chart-wrapper"
                            style={{ height: "250px", width: "100%" }}
                          >
                            {loadingHistory ? (
                              <div
                                style={{ textAlign: "center", padding: "2rem" }}
                              >
                                <RefreshCw className="animate-spin" />
                              </div>
                            ) : (
                              <ResponsiveContainer width="100%" height="100%">
                                <AreaChart data={historyData}>
                                  <defs>
                                    <linearGradient
                                      id="splitColor"
                                      x1="0"
                                      y1="0"
                                      x2="0"
                                      y2="1"
                                    >
                                      <stop
                                        offset={gradientOffset}
                                        stopColor="#10b981"
                                        stopOpacity={0.8}
                                      />
                                      <stop
                                        offset={gradientOffset}
                                        stopColor="#ef4444"
                                        stopOpacity={0.8}
                                      />
                                    </linearGradient>
                                    <linearGradient
                                      id="splitFill"
                                      x1="0"
                                      y1="0"
                                      x2="0"
                                      y2="1"
                                    >
                                      <stop
                                        offset={gradientOffset}
                                        stopColor="#10b981"
                                        stopOpacity={0.3}
                                      />
                                      <stop
                                        offset={gradientOffset}
                                        stopColor="#ef4444"
                                        stopOpacity={0.3}
                                      />
                                    </linearGradient>
                                  </defs>
                                  <CartesianGrid
                                    strokeDasharray="3 3"
                                    stroke="rgba(255,255,255,0.05)"
                                    vertical={false}
                                  />
                                  <XAxis
                                    dataKey="timeLabel"
                                    stroke="var(--text-secondary)"
                                    fontSize={10}
                                    axisLine={false}
                                    tickLine={false}
                                  />
                                  <YAxis
                                    stroke="var(--text-secondary)"
                                    fontSize={10}
                                    axisLine={false}
                                    tickLine={false}
                                    tickFormatter={(v) => `${v.toFixed(2)}%`}
                                  />
                                  <Tooltip
                                    contentStyle={{
                                      backgroundColor: "var(--card-bg)",
                                      border: "1px solid var(--border-color)",
                                      borderRadius: "8px",
                                    }}
                                    labelFormatter={(label, payload) => {
                                      const rawTime = payload?.[0]?.payload?.time;
                                      const date = parseChartTime(rawTime);
                                      return date ? date.toLocaleString() : String(label);
                                    }}
                                    formatter={(v: any) => [
                                      `${v.toFixed(2)}%`,
                                      "김프",
                                    ]}
                                  />
                                  <Area
                                    type="monotone"
                                    dataKey="ratio"
                                    stroke="url(#splitColor)"
                                    fill="url(#splitFill)"
                                    strokeWidth={2}
                                  />
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
  );
};

export default Dashboard;
