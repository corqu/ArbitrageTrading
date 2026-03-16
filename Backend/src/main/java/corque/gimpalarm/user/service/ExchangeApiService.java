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
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExchangeApiService {

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

        String accessKey = encryptionUtil.decrypt(targetCred.getApiKey());
        String secretKey = encryptionUtil.decrypt(targetCred.getApiSecret());

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
        String endpoint = "/info/balance";
        long nonce = System.currentTimeMillis();
        
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("endpoint", endpoint);
        parameters.add("currency", "ALL");

        String strParams = "endpoint=" + endpoint + "&currency=ALL";
        String apiSign = base64Encode(hmacSha512(secretKey, endpoint + (char)0 + strParams + (char)0 + nonce));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Api-Key", accessKey);
        headers.set("Api-Sign", apiSign);
        headers.set("Api-Nonce", String.valueOf(nonce));
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(parameters, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity("https://api.bithumb.com" + endpoint, entity, Map.class);

        if (response.getBody() != null && "0000".equals(response.getBody().get("status"))) {
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            double krw = Double.parseDouble(data.get("total_krw").toString());
            return Map.of("mainBalance", krw, "mainUnit", "KRW");
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
