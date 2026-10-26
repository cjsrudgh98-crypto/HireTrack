package com.hiretrack.parse.fetch;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;

/**
 * SSRF 방어. 사용자가 넣은 URL 로 서버가 요청을 보내기 전에(그리고 리다이렉트마다) 검사한다.
 * <ul>
 *   <li>http/https 만 허용 (file://, ftp://, gopher:// 등 거부)</li>
 *   <li>포트는 기본값/80/443 만 허용 (내부 서비스 포트 스캔 방지)</li>
 *   <li>userinfo(user@host) 거부 — 파서 혼동을 노린 우회 방지</li>
 *   <li>호스트를 DNS 조회해 나온 <b>모든</b> IP 가 공인 주소여야 함 — localhost, 사설망, 링크로컬(클라우드 메타데이터 169.254.169.254) 등 거부</li>
 * </ul>
 */
public class UrlGuard {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<Integer> ALLOWED_PORTS = Set.of(-1, 80, 443);
    private static final int MAX_URL_LENGTH = 2000;

    private final HostResolver resolver;

    public UrlGuard(HostResolver resolver) {
        this.resolver = resolver;
    }

    public URI validate(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_URL_LENGTH) throw invalid();

        URI uri;
        try {
            uri = new URI(raw.trim());
        } catch (URISyntaxException e) {
            throw invalid();
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) throw invalid();
        if (uri.getRawUserInfo() != null) throw invalid();
        if (!ALLOWED_PORTS.contains(uri.getPort())) throw invalid();

        String host = uri.getHost();
        if (host == null || host.isBlank()) throw invalid();
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);

        List<InetAddress> addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw invalid();
        }
        if (addresses.isEmpty() || addresses.stream().anyMatch(UrlGuard::isBlocked)) throw invalid();
        return uri.normalize();
    }

    static boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first == 0                                     // 0.0.0.0/8
                    || (first == 100 && second >= 64 && second <= 127) // 100.64.0.0/10 CGNAT
                    || (first == 192 && second == 0 && (b[2] & 0xff) == 0) // 192.0.0.0/24
                    || (first == 198 && (second == 18 || second == 19))   // 198.18.0.0/15 벤치마크
                    || first >= 240;                               // 240.0.0.0/4 예약, 255.255.255.255
        }
        if (address instanceof Inet6Address) {
            int first = b[0] & 0xff;
            if ((first & 0xfe) == 0xfc) return true;               // fc00::/7 ULA
            if (isEmbeddedIpv4(b)) {                               // ::a.b.c.d, 64:ff9b::a.b.c.d
                try {
                    return isBlocked(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]}));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isEmbeddedIpv4(byte[] b) {
        boolean zeroPrefix = true;
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) zeroPrefix = false;
        }
        boolean compatible = zeroPrefix && b[10] == 0 && b[11] == 0;                       // ::a.b.c.d
        boolean mapped = zeroPrefix && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff;  // ::ffff:a.b.c.d
        boolean nat64 = (b[0] & 0xff) == 0x00 && (b[1] & 0xff) == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b;
        return compatible || mapped || nat64;
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_URL);
    }
}
