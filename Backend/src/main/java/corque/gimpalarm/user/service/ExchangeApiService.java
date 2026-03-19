package corque.gimpalarm.user.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.common.util.EncryptionUtil;
import corque.gimpalarm.user.domain.UserCredential;
import corque.gimpalarm.user.repository.UserCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExchangeApiService {

    private static final String BINANCE_EXCHANGE_INFO_URL = "https://fapi.binance.com/fapi/v1/exchangeInfo";

    private final UserCredentialRepository credentialRepository;
    private final EncryptionUtil encryptionUtil;
    private final PriceManager priceManager;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 지정된 거래소에 대해서만 사용자의 자산을 가져옵니다.
     * 연동되지 않은 거래소인 경우 예외를 발생시킵니다.
     */
    public Map<String, Object> getExchangeAssets(Long userId, String exchange) {
        List<UserCredential> credentials = credentialRepository.findByUserId(userId);
        
        UserCredential targetCred = credentials.stream()
                .filter(c -> c.getExchange().equalsIgnoreCase(exchange))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("연동되지 않은 거래소입니다: " + exchange));

        // 키 값의 앞뒤 공백 제거 (복사/붙여넣기 실수 방지)
        String accessKey = encryptionUtil.decrypt(targetCred.getApiKey()).trim();
        String secretKey = encryptionUtil.decrypt(targetCred.getApiSecret()).trim();

        Object assetInfo = null;
        String upperExchange = exchange.toUpperCase();
        
        switch (upperExchange) {
            case "UPBIT":
                assetInfo = getUpbitAssets(accessKey, secretKey);
                break;
            case "BINANCE":
                assetInfo = getBinanceFuturesAssets(accessKey, secretKey);
                break;
            case "BITHUMB":
                assetInfo = getBithumbAssets(accessKey, secretKey);
                break;
            case "BYBIT":
                assetInfo = getBybitAssets(accessKey, secretKey);
                break;
            default:
                throw new IllegalArgumentException("지원하지 않는 거래소입니다: " + exchange);
        }

        if (assetInfo == null) {
            throw new RuntimeException(exchange + " 자산 정보를 가져오는데 실패했습니다.");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("exchange", upperExchange);
        result.put("data", assetInfo);
        return result;
    }

    private Map<String, Object> getUpbitAssets(String accessKey, String secretKey) {
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        String jwtToken = JWT.create()
                .withClaim("access_key", accessKey)
                .withClaim("nonce", UUID.randomUUID().toString())
                .sign(algorithm);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<List> response = restTemplate.exchange(
                "https://api.upbit.com/v1/accounts", HttpMethod.GET, entity, List.class);

        double krw = 0;
        List<Map<String, String>> coins = new ArrayList<>();

        if (response.getBody() != null) {
            for (Object obj : response.getBody()) {
                Map<String, String> item = (Map<String, String>) obj;
                double balance = Double.parseDouble(item.get("balance"));
                if (balance <= 0) continue;

                if ("KRW".equals(item.get("currency"))) {
                    krw = balance;
                } else {
                    coins.add(Map.of("currency", item.get("currency"), "balance", String.valueOf(balance)));
                }
            }
        }
        return Map.of("mainBalance", krw, "mainUnit", "KRW", "balances", coins);
    }

    private Map<String, Object> getBinanceFuturesAssets(String accessKey, String secretKey) {
        long timestamp = System.currentTimeMillis();
        String queryString = "timestamp=" + timestamp;
        String signature = hmacSha256(secretKey, queryString);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", accessKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String url = "https://fapi.binance.com/fapi/v2/account?" + queryString + "&signature=" + signature;
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        if (response.getBody() != null) {
            double balance = Double.parseDouble(response.getBody().get("totalMarginBalance").toString());
            Double usdKrw = priceManager.getCurrentUsdKrw();
            if (usdKrw == null || usdKrw == 0) usdKrw = 1450.0; // 기본값

            return Map.of(
                    "mainBalance", balance,
                    "mainUnit", "USDT",
                    "krwBalance", balance * usdKrw,
                    "krwUnit", "KRW"
            );
        }
        return null;
    }

    private Map<String, Object> getBithumbAssets(String accessKey, String secretKey) {
        String apiUrl = "https://api.bithumb.com";
        String endpoint = "/v1/accounts";
        
        try {
            // 빗썸 신규 API(JWT 방식) 인증 토큰 생성
            Algorithm algorithm = Algorithm.HMAC256(secretKey);
            String jwtToken = JWT.create()
                    .withClaim("access_key", accessKey)
                    .withClaim("nonce", UUID.randomUUID().toString())
                    .withClaim("timestamp", System.currentTimeMillis())
                    .sign(algorithm);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + jwtToken);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            // 신규 API 엔드포인트 호출 (/v1/accounts)
            ResponseEntity<List> response = restTemplate.exchange(
                    apiUrl + endpoint, HttpMethod.GET, entity, List.class);

            double krw = 0;
            double coinAssetKrw = 0;
            List<Map<String, String>> coins = new ArrayList<>();

            if (response.getBody() != null) {
                for (Object obj : response.getBody()) {
                    Map<String, String> item = (Map<String, String>) obj;
                    // 빗썸 신규 API의 필드명이 업비트와 동일하게 currency, balance인 경우
                    String currency = item.get("currency");
                    String balanceStr = item.get("balance");
                    
                    if (balanceStr == null) continue;
                    double balance = Double.parseDouble(balanceStr);
                    
                    if ("KRW".equals(currency)) {
                        krw = balance;
                    } else if (balance > 0) {
                        Double currentPrice = priceManager.getPrice("BT_" + currency.toUpperCase());
                        if (currentPrice != null && currentPrice > 0) {
                            coinAssetKrw += balance * currentPrice;
                        }
                        coins.add(Map.of("currency", currency, "balance", String.valueOf(balance)));
                    }
                }
                return Map.of(
                        "mainBalance", krw,
                        "mainUnit", "KRW",
                        "balances", coins,
                        "coinAssetKrw", coinAssetKrw,
                        "totalAssetKrw", krw + coinAssetKrw,
                        "totalAssetUnit", "KRW"
                );
            }
        } catch (Exception e) {
            log.error("Bithumb (JWT) API 호출 중 예외 발생: {}", e.getMessage());
            throw new RuntimeException("Bithumb API 호출 실패: " + e.getMessage());
        }
        return null;
    }

    private Map<String, Object> getBybitAssets(String accessKey, String secretKey) {
        long timestamp = System.currentTimeMillis();
        String recvWindow = "5000";
        String queryString = "accountType=UNIFIED";
        String rawData = timestamp + accessKey + recvWindow + queryString;
        String signature = hmacSha256(secretKey, rawData);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-BAPI-API-KEY", accessKey);
        headers.set("X-BAPI-SIGN", signature);
        headers.set("X-BAPI-TIMESTAMP", String.valueOf(timestamp));
        headers.set("X-BAPI-RECV-WINDOW", recvWindow);

        String url = "https://api.bybit.com/v5/account/wallet-balance?" + queryString;
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        if (response.getBody() != null && "0".equals(response.getBody().get("retCode").toString())) {
            Map<String, Object> result = (Map<String, Object>) response.getBody().get("result");
            List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("list");
            if (!list.isEmpty()) {
                double totalEquity = Double.parseDouble(list.get(0).get("totalEquity").toString());
                Double usdKrw = priceManager.getCurrentUsdKrw();
                if (usdKrw == null || usdKrw == 0) usdKrw = 1450.0;

                return Map.of(
                        "mainBalance", totalEquity,
                        "mainUnit", "USDT",
                        "krwBalance", totalEquity * usdKrw,
                        "krwUnit", "KRW"
                );
            }
        }
        return null;
    }

    /**
     * 업비트 주문 실행
     */
    public Map<String, Object> orderUpbit(Long userId, String symbol, String side, double volume, Double price, String ordType) {
        UserCredential credential = getCredential(userId, "UPBIT");
        String accessKey = encryptionUtil.decrypt(credential.getApiKey()).trim();
        String secretKey = encryptionUtil.decrypt(credential.getApiSecret()).trim();

        String market = "KRW-" + symbol.toUpperCase();
        
        Map<String, String> params = new HashMap<>();
        params.put("market", market);
        params.put("side", side.toLowerCase()); // bid(매수), ask(매도)
        if (volume > 0) params.put("volume", String.valueOf(volume));
        if (price != null) params.put("price", String.valueOf(price));
        params.put("ord_type", ordType.toLowerCase()); // limit(지정가), price(시장가 매수), market(시장가 매도)

        ArrayList<String> queryElements = new ArrayList<>();
        for(Map.Entry<String, String> entity : params.entrySet()) {
            queryElements.add(entity.getKey() + "=" + entity.getValue());
        }
        String queryString = String.join("&", queryElements);

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(queryString.getBytes("UTF-8"));
            String queryHash = String.format("%0128x", new BigInteger(1, md.digest()));

            Algorithm algorithm = Algorithm.HMAC256(secretKey);
            String jwtToken = JWT.create()
                    .withClaim("access_key", accessKey)
                    .withClaim("nonce", UUID.randomUUID().toString())
                    .withClaim("query_hash", queryHash)
                    .withClaim("query_hash_alg", "SHA512")
                    .sign(algorithm);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + jwtToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(params, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.upbit.com/v1/orders", HttpMethod.POST, entity, Map.class);

            return response.getBody();
        } catch (Exception e) {
            log.error("Upbit Order Error: {}", e.getMessage());
            throw new RuntimeException("업비트 주문 실패: " + e.getMessage());
        }
    }

    /**
     * 바이낸스 선물 주문 실행
     */
    public Map<String, Object> orderBinanceFutures(Long userId, String symbol, String side, String positionSide, double quantity, Double price, String type) {
        UserCredential credential = getCredential(userId, "BINANCE");
        String accessKey = encryptionUtil.decrypt(credential.getApiKey()).trim();
        String secretKey = encryptionUtil.decrypt(credential.getApiSecret()).trim();

        String binanceSymbol = symbol.toUpperCase() + "USDT";
        String resolvedPositionSide = resolveBinancePositionSide(userId, positionSide);
        BinanceSymbolRules symbolRules = getBinanceSymbolRules(binanceSymbol);
        BigDecimal normalizedQuantity = normalizeQuantity(quantity, symbolRules);
        BigDecimal normalizedPrice = normalizePrice(price, symbolRules);
        long timestamp = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder();
        sb.append("symbol=").append(binanceSymbol);
        sb.append("&side=").append(side.toUpperCase()); // BUY, SELL
        sb.append("&positionSide=").append(resolvedPositionSide); // BOTH, LONG, SHORT
        sb.append("&type=").append(type.toUpperCase()); // LIMIT, MARKET
        if ("LIMIT".equalsIgnoreCase(type)) {
            sb.append("&timeInForce=GTC");
        }
        sb.append("&quantity=").append(normalizedQuantity.stripTrailingZeros().toPlainString());
        if (normalizedPrice != null) sb.append("&price=").append(normalizedPrice.stripTrailingZeros().toPlainString());
        sb.append("&timestamp=").append(timestamp);

        String queryString = sb.toString();
        String signature = hmacSha256(secretKey, queryString);
        String url = "https://fapi.binance.com/fapi/v1/order?" + queryString + "&signature=" + signature;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", accessKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Binance Order Error: {}", e.getMessage());
            throw new RuntimeException("바이낸스 주문 실패: " + e.getMessage());
        }
    }

    /**
     * 빗썸 주문 실행 (V1 신규 API 기준)
     */
    public Map<String, Object> orderBithumb(Long userId, String symbol, String side, double volume, Double price, String ordType) {
        UserCredential credential = getCredential(userId, "BITHUMB");
        String accessKey = encryptionUtil.decrypt(credential.getApiKey()).trim();
        String secretKey = encryptionUtil.decrypt(credential.getApiSecret()).trim();

        String market = "KRW-" + symbol.toUpperCase();
        
        Map<String, String> params = new HashMap<>();
        params.put("market", market);
        params.put("side", side.toLowerCase());
        if (volume > 0) params.put("volume", String.valueOf(volume));
        if (price != null) params.put("price", String.valueOf(price));
        params.put("ord_type", ordType.toLowerCase());

        ArrayList<String> queryElements = new ArrayList<>();
        for(Map.Entry<String, String> entity : params.entrySet()) {
            queryElements.add(entity.getKey() + "=" + entity.getValue());
        }
        String queryString = String.join("&", queryElements);

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(queryString.getBytes("UTF-8"));
            String queryHash = String.format("%0128x", new BigInteger(1, md.digest()));

            Algorithm algorithm = Algorithm.HMAC256(secretKey);
            String jwtToken = JWT.create()
                    .withClaim("access_key", accessKey)
                    .withClaim("nonce", UUID.randomUUID().toString())
                    .withClaim("timestamp", System.currentTimeMillis()) // 빗썸은 타임스탬프 필수
                    .withClaim("query_hash", queryHash)
                    .withClaim("query_hash_alg", "SHA512")
                    .sign(algorithm);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + jwtToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(params, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.bithumb.com/v1/orders", HttpMethod.POST, entity, Map.class);

            return response.getBody();
        } catch (Exception e) {
            log.error("Bithumb Order Error: {}", e.getMessage());
            throw new RuntimeException("빗썸 주문 실패: " + e.getMessage());
        }
    }

    /**
     * 바이낸스 선물 레버리지 설정
     */
    public Map<String, Object> setBinanceLeverage(Long userId, String symbol, int leverage) {
        UserCredential credential = getCredential(userId, "BINANCE");
        String accessKey = encryptionUtil.decrypt(credential.getApiKey()).trim();
        String secretKey = encryptionUtil.decrypt(credential.getApiSecret()).trim();

        String binanceSymbol = symbol.toUpperCase() + "USDT";
        long timestamp = System.currentTimeMillis();

        String queryString = "symbol=" + binanceSymbol + "&leverage=" + leverage + "&timestamp=" + timestamp;
        String signature = hmacSha256(secretKey, queryString);
        String url = "https://fapi.binance.com/fapi/v1/leverage?" + queryString + "&signature=" + signature;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", accessKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Binance Leverage Set Error: {}", e.getMessage());
            throw new RuntimeException("바이낸스 레버리지 설정 실패: " + e.getMessage());
        }
    }

    /**
     * 바이낸스 선물 조건부 주문 (SL/TP)
     */
    public Map<String, Object> orderBinanceFuturesConditional(Long userId, String symbol, String side, String positionSide, double quantity, double stopPrice, String type) {
        UserCredential credential = getCredential(userId, "BINANCE");
        String accessKey = encryptionUtil.decrypt(credential.getApiKey()).trim();
        String secretKey = encryptionUtil.decrypt(credential.getApiSecret()).trim();

        String binanceSymbol = symbol.toUpperCase() + "USDT";
        String resolvedPositionSide = resolveBinancePositionSide(userId, positionSide);
        BinanceSymbolRules symbolRules = getBinanceSymbolRules(binanceSymbol);
        BigDecimal normalizedStopPrice = normalizePrice(stopPrice, symbolRules);
        long timestamp = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder();
        sb.append("symbol=").append(binanceSymbol);
        sb.append("&side=").append(side.toUpperCase());
        sb.append("&positionSide=").append(resolvedPositionSide);
        sb.append("&type=").append(type.toUpperCase()); // STOP_MARKET, TAKE_PROFIT_MARKET
        sb.append("&stopPrice=").append(normalizedStopPrice.stripTrailingZeros().toPlainString());
        sb.append("&closePosition=true"); // 포지션 전량 종료
        sb.append("&timestamp=").append(timestamp);

        String queryString = sb.toString();
        String signature = hmacSha256(secretKey, queryString);
        String url = "https://fapi.binance.com/fapi/v1/order?" + queryString + "&signature=" + signature;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", accessKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Binance Conditional Order Error: {}", e.getMessage());
            throw new RuntimeException("바이낸스 조건부 주문 실패: " + e.getMessage());
        }
    }

    /**
     * 바이낸스 선물 특정 코인의 현재 포지션 수량 조회
     */
    public double getBinanceFuturesPosition(Long userId, String symbol) {
        UserCredential credential = getCredential(userId, "BINANCE");
        String accessKey = encryptionUtil.decrypt(credential.getApiKey()).trim();
        String secretKey = encryptionUtil.decrypt(credential.getApiSecret()).trim();

        String binanceSymbol = symbol.toUpperCase() + "USDT";
        String expectedPositionSide = resolveBinancePositionSide(userId, "SHORT");
        long timestamp = System.currentTimeMillis();
        String queryString = "symbol=" + binanceSymbol + "&timestamp=" + timestamp;
        String signature = hmacSha256(secretKey, queryString);
        String url = "https://fapi.binance.com/fapi/v2/positionRisk?" + queryString + "&signature=" + signature;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", accessKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);
            if (response.getBody() != null && !response.getBody().isEmpty()) {
                // 여러 포지션 모드(Hedge/One-way)가 있을 수 있으므로 SHORT 포지션 필터링
                for (Object obj : response.getBody()) {
                    Map<String, Object> pos = (Map<String, Object>) obj;
                    if (expectedPositionSide.equalsIgnoreCase(pos.get("positionSide").toString())) {
                        return Math.abs(Double.parseDouble(pos.get("positionAmt").toString()));
                    }
                }
            }
            return 0.0;
        } catch (Exception e) {
            log.error("Binance Position Fetch Error: {}", e.getMessage());
            return -1.0; // 에러 발생 시 -1 반환하여 체크 건너뛰기
        }
    }

    /**
     * 업비트 주문 상세 조회
     */
    public Map<String, Object> getOrderUpbit(Long userId, String orderId) {
        UserCredential credential = getCredential(userId, "UPBIT");
        String accessKey = encryptionUtil.decrypt(credential.getApiKey()).trim();
        String secretKey = encryptionUtil.decrypt(credential.getApiSecret()).trim();

        String queryString = "uuid=" + orderId;
        try {
            String jwtToken = createUpbitJwt(accessKey, secretKey, queryString);
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + jwtToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.upbit.com/v1/order?" + queryString, HttpMethod.GET, entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Upbit GetOrder Error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 업비트 주문 취소
     */
    public Map<String, Object> cancelOrderUpbit(Long userId, String orderId) {
        UserCredential credential = getCredential(userId, "UPBIT");
        String accessKey = encryptionUtil.decrypt(credential.getApiKey()).trim();
        String secretKey = encryptionUtil.decrypt(credential.getApiSecret()).trim();

        String queryString = "uuid=" + orderId;
        try {
            String jwtToken = createUpbitJwt(accessKey, secretKey, queryString);
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + jwtToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.upbit.com/v1/order?" + queryString, HttpMethod.DELETE, entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Upbit Cancel Error: {}", e.getMessage());
            return null;
        }
    }

    public Map<String, Object> getOrderBithumb(Long userId, String orderId) {
        UserCredential credential = getCredential(userId, "BITHUMB");
        String accessKey = encryptionUtil.decrypt(credential.getApiKey()).trim();
        String secretKey = encryptionUtil.decrypt(credential.getApiSecret()).trim();

        String queryString = "uuid=" + orderId;
        try {
            String jwtToken = createBithumbJwt(accessKey, secretKey, queryString);
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + jwtToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.bithumb.com/v1/order?" + queryString, HttpMethod.GET, entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Bithumb GetOrder Error: {}", e.getMessage());
            return null;
        }
    }

    public Map<String, Object> cancelOrderBithumb(Long userId, String orderId) {
        UserCredential credential = getCredential(userId, "BITHUMB");
        String accessKey = encryptionUtil.decrypt(credential.getApiKey()).trim();
        String secretKey = encryptionUtil.decrypt(credential.getApiSecret()).trim();

        String queryString = "uuid=" + orderId;
        try {
            String jwtToken = createBithumbJwt(accessKey, secretKey, queryString);
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + jwtToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.bithumb.com/v1/order?" + queryString, HttpMethod.DELETE, entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Bithumb Cancel Error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 바이낸스 선물 주문 상세 조회
     */
    public Map<String, Object> getOrderBinance(Long userId, String symbol, String orderId) {
        UserCredential credential = getCredential(userId, "BINANCE");
        String accessKey = encryptionUtil.decrypt(credential.getApiKey()).trim();
        String secretKey = encryptionUtil.decrypt(credential.getApiSecret()).trim();

        long timestamp = System.currentTimeMillis();
        String queryString = "symbol=" + symbol.toUpperCase() + "USDT&orderId=" + orderId + "&timestamp=" + timestamp;
        String signature = hmacSha256(secretKey, queryString);
        String url = "https://fapi.binance.com/fapi/v1/order?" + queryString + "&signature=" + signature;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", accessKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Binance GetOrder Error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 바이낸스 선물 주문 취소
     */
    public Map<String, Object> cancelOrderBinance(Long userId, String symbol, String orderId) {
        UserCredential credential = getCredential(userId, "BINANCE");
        String accessKey = encryptionUtil.decrypt(credential.getApiKey()).trim();
        String secretKey = encryptionUtil.decrypt(credential.getApiSecret()).trim();

        long timestamp = System.currentTimeMillis();
        String queryString = "symbol=" + symbol.toUpperCase() + "USDT&orderId=" + orderId + "&timestamp=" + timestamp;
        String signature = hmacSha256(secretKey, queryString);
        String url = "https://fapi.binance.com/fapi/v1/order?" + queryString + "&signature=" + signature;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", accessKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Binance Cancel Error: {}", e.getMessage());
            return null;
        }
    }

    private String createUpbitJwt(String accessKey, String secretKey, String queryString) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(queryString.getBytes("UTF-8"));
        String queryHash = String.format("%0128x", new BigInteger(1, md.digest()));

        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        return JWT.create()
                .withClaim("access_key", accessKey)
                .withClaim("nonce", UUID.randomUUID().toString())
                .withClaim("query_hash", queryHash)
                .withClaim("query_hash_alg", "SHA512")
                .sign(algorithm);
    }

    private String createBithumbJwt(String accessKey, String secretKey, String queryString) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(queryString.getBytes("UTF-8"));
        String queryHash = String.format("%0128x", new BigInteger(1, md.digest()));

        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        return JWT.create()
                .withClaim("access_key", accessKey)
                .withClaim("nonce", UUID.randomUUID().toString())
                .withClaim("timestamp", System.currentTimeMillis())
                .withClaim("query_hash", queryHash)
                .withClaim("query_hash_alg", "SHA512")
                .sign(algorithm);
    }

    private UserCredential getCredential(Long userId, String exchange) {
        return credentialRepository.findByUserId(userId).stream()
                .filter(c -> c.getExchange().equalsIgnoreCase(exchange))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("연동되지 않은 거래소입니다: " + exchange));
    }

    private String resolveBinancePositionSide(Long userId, String requestedPositionSide) {
        if (!isBinanceHedgeMode(userId)) {
            return "BOTH";
        }
        return requestedPositionSide.toUpperCase();
    }

    private boolean isBinanceHedgeMode(Long userId) {
        UserCredential credential = getCredential(userId, "BINANCE");
        String accessKey = encryptionUtil.decrypt(credential.getApiKey()).trim();
        String secretKey = encryptionUtil.decrypt(credential.getApiSecret()).trim();

        long timestamp = System.currentTimeMillis();
        String queryString = "timestamp=" + timestamp;
        String signature = hmacSha256(secretKey, queryString);
        String url = "https://fapi.binance.com/fapi/v1/positionSide/dual?" + queryString + "&signature=" + signature;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", accessKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getBody() == null || response.getBody().get("dualSidePosition") == null) {
                return false;
            }
            return Boolean.parseBoolean(String.valueOf(response.getBody().get("dualSidePosition")));
        } catch (Exception e) {
            log.warn("Binance position mode fetch failed. Fallback to ONE_WAY: {}", e.getMessage());
            return false;
        }
    }

    private BinanceSymbolRules getBinanceSymbolRules(String binanceSymbol) {
        ResponseEntity<Map> response = restTemplate.getForEntity(BINANCE_EXCHANGE_INFO_URL, Map.class);
        if (response.getBody() == null || response.getBody().get("symbols") == null) {
            throw new RuntimeException("Binance exchangeInfo response is empty");
        }

        List<Map<String, Object>> symbols = (List<Map<String, Object>>) response.getBody().get("symbols");
        Map<String, Object> symbolInfo = symbols.stream()
                .filter(item -> binanceSymbol.equalsIgnoreCase(String.valueOf(item.get("symbol"))))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported Binance futures symbol: " + binanceSymbol));

        String stepSize = null;
        String tickSize = null;
        List<Map<String, Object>> filters = (List<Map<String, Object>>) symbolInfo.get("filters");
        if (filters != null) {
            for (Map<String, Object> filter : filters) {
                String filterType = String.valueOf(filter.get("filterType"));
                if ("LOT_SIZE".equalsIgnoreCase(filterType)) {
                    stepSize = String.valueOf(filter.get("stepSize"));
                } else if ("PRICE_FILTER".equalsIgnoreCase(filterType)) {
                    tickSize = String.valueOf(filter.get("tickSize"));
                }
            }
        }

        return new BinanceSymbolRules(
                stepSize != null ? new BigDecimal(stepSize) : null,
                tickSize != null ? new BigDecimal(tickSize) : null
        );
    }

    BigDecimal normalizeQuantity(double quantity, BinanceSymbolRules symbolRules) {
        BigDecimal rawQuantity = BigDecimal.valueOf(quantity);
        if (symbolRules.stepSize() == null || symbolRules.stepSize().compareTo(BigDecimal.ZERO) <= 0) {
            return rawQuantity;
        }

        BigDecimal steps = rawQuantity.divide(symbolRules.stepSize(), 0, RoundingMode.DOWN);
        BigDecimal normalized = steps.multiply(symbolRules.stepSize());
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Normalized Binance quantity is zero: " + quantity);
        }
        return normalized;
    }

    BigDecimal normalizePrice(Double price, BinanceSymbolRules symbolRules) {
        if (price == null) {
            return null;
        }
        return normalizePrice(price.doubleValue(), symbolRules);
    }

    BigDecimal normalizePrice(double price, BinanceSymbolRules symbolRules) {
        BigDecimal rawPrice = BigDecimal.valueOf(price);
        if (symbolRules.tickSize() == null || symbolRules.tickSize().compareTo(BigDecimal.ZERO) <= 0) {
            return rawPrice;
        }

        BigDecimal ticks = rawPrice.divide(symbolRules.tickSize(), 0, RoundingMode.DOWN);
        BigDecimal normalized = ticks.multiply(symbolRules.tickSize());
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Normalized Binance price is zero: " + price);
        }
        return normalized;
    }

    record BinanceSymbolRules(BigDecimal stepSize, BigDecimal tickSize) {
    }

    private String hmacSha256(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(rawHmac);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private byte[] hmacSha512(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String base64Encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
