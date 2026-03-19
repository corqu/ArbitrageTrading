package corque.gimpalarm.user.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import corque.gimpalarm.coin.dto.PriceManager;
import corque.gimpalarm.common.exception.BadRequestException;
import corque.gimpalarm.common.exception.ExternalApiException;
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
     * 吏?뺣맂 嫄곕옒?뚯뿉 ??댁꽌留??ъ슜?먯쓽 ?먯궛??媛?몄샃?덈떎.
     * ?곕룞?섏? ?딆? 嫄곕옒?뚯씤 寃쎌슦 ?덉쇅瑜?諛쒖깮?쒗궢?덈떎.
     */
    public Map<String, Object> getExchangeAssets(Long userId, String exchange) {
        List<UserCredential> credentials = credentialRepository.findByUserId(userId);
        
        UserCredential targetCred = credentials.stream()
                .filter(c -> c.getExchange().equalsIgnoreCase(exchange))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("연동되지 않은 거래소입니다: " + exchange));

        // ??媛믪쓽 ?욌뮘 怨듬갚 ?쒓굅 (蹂듭궗/遺숈뿬?ｊ린 ?ㅼ닔 諛⑹?)
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
                throw new BadRequestException("지원하지 않는 거래소입니다: " + exchange);
        }

        if (assetInfo == null) {
            throw new ExternalApiException(exchange + " 자산 정보를 가져오는데 실패했습니다.");
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
            if (usdKrw == null || usdKrw == 0) usdKrw = 1450.0; // 湲곕낯媛?

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
            // 鍮쀬뜽 ?좉퇋 API(JWT 諛⑹떇) ?몄쬆 ?좏겙 ?앹꽦
            Algorithm algorithm = Algorithm.HMAC256(secretKey);
            String jwtToken = JWT.create()
                    .withClaim("access_key", accessKey)
                    .withClaim("nonce", UUID.randomUUID().toString())
                    .withClaim("timestamp", System.currentTimeMillis())
                    .sign(algorithm);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + jwtToken);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            // ?좉퇋 API ?붾뱶?ъ씤???몄텧 (/v1/accounts)
            ResponseEntity<List> response = restTemplate.exchange(
                    apiUrl + endpoint, HttpMethod.GET, entity, List.class);

            double krw = 0;
            double coinAssetKrw = 0;
            List<Map<String, String>> coins = new ArrayList<>();

            if (response.getBody() != null) {
                for (Object obj : response.getBody()) {
                    Map<String, String> item = (Map<String, String>) obj;
                    // 鍮쀬뜽 ?좉퇋 API???꾨뱶紐낆씠 ?낅퉬?몄? ?숈씪?섍쾶 currency, balance??寃쎌슦
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
            log.error("Bithumb (JWT) API ?몄텧 以??덉쇅 諛쒖깮: {}", e.getMessage());
            throw new ExternalApiException("Bithumb API 호출 실패: " + e.getMessage(), e);
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
     * ?낅퉬??二쇰Ц ?ㅽ뻾
     */
    public Map<String, Object> orderUpbit(Long userId, String symbol, String side, double volume, Double price, String ordType) {
        UserCredential credential = getCredential(userId, "UPBIT");
        String accessKey = encryptionUtil.decrypt(credential.getApiKey()).trim();
        String secretKey = encryptionUtil.decrypt(credential.getApiSecret()).trim();

        String market = "KRW-" + symbol.toUpperCase();
        
        Map<String, String> params = new HashMap<>();
        params.put("market", market);
        params.put("side", side.toLowerCase()); // bid(留ㅼ닔), ask(留ㅻ룄)
        if (volume > 0) params.put("volume", String.valueOf(volume));
        if (price != null) params.put("price", String.valueOf(price));
        params.put("ord_type", ordType.toLowerCase()); // limit(吏?뺢?), price(?쒖옣媛 留ㅼ닔), market(?쒖옣媛 留ㅻ룄)

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
            throw new ExternalApiException("업비트 주문 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 諛붿씠?몄뒪 ?좊Ъ 二쇰Ц ?ㅽ뻾
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
            throw new ExternalApiException("바이낸스 주문 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 鍮쀬뜽 二쇰Ц ?ㅽ뻾 (V1 ?좉퇋 API 湲곗?)
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
                    .withClaim("timestamp", System.currentTimeMillis()) // 鍮쀬뜽? ??꾩뒪?ы봽 ?꾩닔
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
            throw new ExternalApiException("빗썸 주문 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 諛붿씠?몄뒪 ?좊Ъ ?덈쾭由ъ? ?ㅼ젙
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
            throw new ExternalApiException("바이낸스 레버리지 설정 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 諛붿씠?몄뒪 ?좊Ъ 議곌굔遺 二쇰Ц (SL/TP)
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
        sb.append("&closePosition=true"); // ?ъ????꾨웾 醫낅즺
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
            throw new ExternalApiException("바이낸스 조건부 주문 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 諛붿씠?몄뒪 ?좊Ъ ?뱀젙 肄붿씤???꾩옱 ?ъ????섎웾 議고쉶
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
                // ?щ윭 ?ъ???紐⑤뱶(Hedge/One-way)媛 ?덉쓣 ???덉쑝誘濡?SHORT ?ъ????꾪꽣留?
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
            return -1.0; // ?먮윭 諛쒖깮 ??-1 諛섑솚?섏뿬 泥댄겕 嫄대꼫?곌린
        }
    }

    /**
     * ?낅퉬??二쇰Ц ?곸꽭 議고쉶
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
     * ?낅퉬??二쇰Ц 痍⑥냼
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
     * 諛붿씠?몄뒪 ?좊Ъ 二쇰Ц ?곸꽭 議고쉶
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
     * 諛붿씠?몄뒪 ?좊Ъ 二쇰Ц 痍⑥냼
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
                .orElseThrow(() -> new BadRequestException("연동되지 않은 거래소입니다: " + exchange));
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
            throw new ExternalApiException("Binance exchangeInfo response is empty");
        }

        List<Map<String, Object>> symbols = (List<Map<String, Object>>) response.getBody().get("symbols");
        Map<String, Object> symbolInfo = symbols.stream()
                .filter(item -> binanceSymbol.equalsIgnoreCase(String.valueOf(item.get("symbol"))))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unsupported Binance futures symbol: " + binanceSymbol));

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
            throw new BadRequestException("Normalized Binance quantity is zero: " + quantity);
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
            throw new BadRequestException("Normalized Binance price is zero: " + price);
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
        } catch (Exception e) { throw new ExternalApiException("서명 생성에 실패했습니다.", e); }
    }

    private byte[] hmacSha512(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new ExternalApiException("서명 생성에 실패했습니다.", e); }
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



