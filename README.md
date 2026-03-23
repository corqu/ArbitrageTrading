# GimpAlarm

국내외 거래소 간 김치프리미엄을 실시간으로 추적하고, 차익거래 진입/청산 기준을 분석하며, 사용자별 자동매매 봇을 관리할 수 있는 웹 서비스입니다.

프론트엔드는 React + Vite로 구성되어 있고, 백엔드는 Spring Boot 기반으로 REST API, WebSocket, 자동매매 로직을 제공합니다. 서비스는 Nginx 리버스 프록시 뒤에서 동작하며, 데이터 저장은 MySQL, 시계열 이력 조회는 InfluxDB를 사용합니다.

## 프로젝트 개요

GimpAlarm은 단순 시세 조회가 아니라 다음 흐름을 하나의 서비스 안에서 연결하는 데 초점을 둔 프로젝트입니다.

- 국내 거래소와 해외 선물 거래소 가격을 비교해 김치프리미엄을 계산
- 실시간 WebSocket 스트림으로 종목별 프리미엄 변화를 반영
- 과거 구간을 기준으로 진입/청산 임계값 백테스트 수행
- 사용자 계정별 거래소 API 키를 연동
- 조건 기반 자동매매 봇 구독, 실행, 중지, 설정 변경 지원
- 마이페이지에서 자산 현황, 최근 주문 내역, 구독 중인 봇 상태 확인

## 주요 기능

### 1. 실시간 김치프리미엄 대시보드

- `UPBIT` / `BITHUMB` 와 `BINANCE` / `BYBIT` 조합별 실시간 프리미엄 조회
- 종목 검색, 정렬, 거래량 기준 탐색 지원
- 종목별 프리미엄 히스토리 차트 조회
- WebSocket(STOMP + SockJS) 기반 실시간 업데이트

### 2. 차익거래 분석

- 진입 김프, 청산 김프, 투자금, 레버리지, 손절/익절 값 입력
- 최근 구간 기준 백테스트 실행
- 종목별 예상 수익률, 거래 횟수, 평균 보유 시간 확인
- 조건 충족 시 자동매매 봇 구독 및 실행

### 3. 인증 및 사용자 관리

- 회원가입 / 로그인 / 로그아웃
- JWT 기반 인증
- 닉네임 중복 확인
- 쿠키 기반 액세스 토큰 / 리프레시 토큰 처리

### 4. 거래소 연동 및 자산 조회

- 사용자별 거래소 API Key / Secret Key 등록
- 연동된 거래소 목록 확인 및 해제
- 국내/해외 거래소 자산 현황 조회
- 최근 주문 내역 확인

### 5. 자동매매 봇 관리

- 봇 구독 목록 조회
- 봇 시작 / 중지
- 진입 김프, 청산 김프, 매매 금액, 레버리지 등 설정 수정
- 활성 봇의 실시간 김프 상태 확인

## 기술 스택

### Frontend

- React 19
- TypeScript
- Vite
- React Router
- Axios
- STOMP / SockJS
- Recharts
- Lucide React

### Backend

- Java 21
- Spring Boot 3.4
- Spring Web
- Spring Security
- Spring WebSocket
- Spring Data JPA
- JWT
- Gradle

### Infra / Data

- MySQL 8
- InfluxDB 2.7
- Nginx
- Docker Compose

## 디렉터리 구조

```
gimpalarm/
├─ Frontend/                 # React + Vite 프론트엔드
├─ Backend/                  # Spring Boot 백엔드
├─ mysql/                    # MySQL 볼륨 데이터
├─ docker-compose.yml        # MySQL / InfluxDB / Backend / Frontend / Nginx 실행
├─ .env                      # 로컬 환경변수
└─ README.md
```

## 아키텍처

```text
Client
  │
  ▼
Nginx
  ├─ Frontend 정적 파일 제공
  ├─ /api 프록시
  └─ /ws-stomp 프록시
           │
           ▼
Backend (Spring Boot)
  ├─ 인증 / 사용자 / 거래소 연동 API
  ├─ 김프 계산 / 히스토리 조회 API
  ├─ 백테스트 / 자동매매 API
  └─ WebSocket 브로커
           │
           ├─ MySQL     : 사용자, 자격증명, 주문, 봇 상태
           └─ InfluxDB  : 시계열 가격 / 김프 이력
```

## 화면 기준 사용자 흐름

### 대시보드

- 실시간 김치프리미엄 목록 확인
- 거래소 조합 변경
- 종목별 차트 펼쳐서 최근 프리미엄 흐름 조회

### 차익거래 페이지

- 진입/청산 기준 입력
- 백테스트 실행
- 수익성 높은 종목 확인
- 자동매매 봇 구독 또는 중지

### 마이페이지

- 회원 정보 수정
- 거래소 API 연동 / 해제
- 국내외 자산 조회
- 최근 주문 기록 확인
- 구독 중인 봇 설정 변경 및 상태 제어

## 주요 API

### Market / Dashboard

- `GET /api/kimp/current`
- `GET /api/kimp/current/pairs`
- `GET /api/kimp/history`

### Arbitrage / Trading

- `GET /api/arbitrage/backtest`
- `POST /api/trading/execute`
- `GET /api/trading/status`

### Auth

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `POST /api/auth/refresh`
- `GET /api/auth/me`
- `GET /api/auth/check-nickname`

### User Credentials / Bots

- `GET /api/user/credentials/list`
- `POST /api/user/credentials/bind`
- `DELETE /api/user/credentials/unbind/{exchange}`
- `GET /api/user/credentials/assets`
- `GET /api/user/credentials/orders`
- `GET /api/user-bots`
- `POST /api/user-bots`
- `PUT /api/user-bots/{id}`
- `DELETE /api/user-bots/{id}`

## 실시간 통신

- WebSocket endpoint: `/ws-stomp`
- Topic 예시:
  - `/topic/kimp/ub-bn`
  - `/topic/kimp/bt-bb`
  - `/topic/kimp/ub-bn/BTC`

개발 모드에서 프론트엔드를 별도로 실행하는 경우에는 Vite proxy를 통해 `/api`, `/ws-stomp` 요청을 백엔드 `localhost:8080`으로 전달합니다.

## 로컬 실행 방법

### 1. 사전 요구사항

- Node.js 20+
- npm
- Java 21
- Docker / Docker Compose

### 2. Docker Compose 실행

루트 디렉터리에서:

```bash
docker compose up -d
```

현재 `docker-compose.yml`에는 다음 서비스가 포함되어 있습니다.

- MySQL 8.0
- InfluxDB 2.7
- Backend
- Frontend
- Nginx

실행 후 기본 접속 주소는 `http://localhost`입니다.

## 환경변수

현재 프로젝트는 데이터베이스 계정, 텔레그램 알림, JWT/암호화 키 등 런타임 설정이 필요합니다.

예시:

```env
MYSQL_DATABASE=your_database
MYSQL_ROOT_PASSWORD=your_root_password
MYSQL_USER=your_user
MYSQL_PASSWORD=your_password

BotToken=your_telegram_bot_token
ChatID=your_telegram_chat_id
```

추가로 백엔드 설정 파일에서 다음 값들도 운영 환경에 맞게 분리하는 것이 좋습니다.

- `spring.datasource.url`
- `spring.influxdb.url`
- `spring.influxdb.token`
- `encryption.secret-key`
- `encryption.iv`
- `jwt.token.secret-key`

## 테스트

### Backend

```bash
cd Backend
./gradlew test
```

Windows:

```powershell
cd Backend
.\gradlew.bat test
```

배포 전에는 [Backend/deploy-checklist.txt](/C:/exam/gimpalarm/Backend/deploy-checklist.txt) 기준으로 수동 점검까지 수행하는 것을 권장합니다.

## 구현 포인트

- 거래소 조합별 김치프리미엄을 실시간으로 브로드캐스트하는 구조를 적용했습니다.
- 단순 시세 조회가 아니라 백테스트, 자산 조회, 주문 내역, 봇 관리까지 사용자 흐름을 연결했습니다.
- 인증이 필요한 API와 공개 API를 분리해 자동매매 관련 기능은 인증 사용자만 사용하도록 구성했습니다.
- 사용자별 거래소 자격증명을 저장하고, 이를 기반으로 거래소 자산 및 자동매매 기능을 제공합니다.
