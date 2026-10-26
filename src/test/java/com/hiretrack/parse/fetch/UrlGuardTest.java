package com.hiretrack.parse.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;

class UrlGuardTest {

    /** IP 리터럴은 그대로, 이름은 아래 표로만 해석하는 가짜 DNS (네트워크 사용 안 함) */
    private static final Map<String, String> DNS = Map.of(
            "www.saramin.co.kr", "211.233.80.53",
            "jobs.example.com", "93.184.216.34",
            "localhost", "127.0.0.1",
            "evil.example.com", "10.0.0.5",           // 공개 도메인이 내부 IP 를 가리키는 경우
            "metadata.example.com", "169.254.169.254");

    private final UrlGuard guard = new UrlGuard(UrlGuardTest::resolve);

    private static List<InetAddress> resolve(String host) throws UnknownHostException {
        try {
            return List.of(InetAddress.ofLiteral(host));
        } catch (IllegalArgumentException notLiteral) {
            String ip = DNS.get(host);
            if (ip == null) throw new UnknownHostException(host);
            return List.of(InetAddress.getByAddress(host, InetAddress.ofLiteral(ip).getAddress()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=1",
            "http://jobs.example.com/careers/1",
            "https://jobs.example.com:443/a?q=백엔드",
            "HTTPS://JOBS.EXAMPLE.COM/upper",
    })
    void 공개된_http_https_주소는_허용(String url) {
        assertThat(guard.validate(url)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // 스킴
            "file:///etc/passwd",
            "ftp://jobs.example.com/a",
            "gopher://jobs.example.com/",
            "jar:file:/app.jar!/",
            "javascript:alert(1)",
            "//jobs.example.com/no-scheme",
            // 루프백·내부망
            "http://localhost/",
            "http://localhost:8080/api/users/me",
            "http://127.0.0.1/",
            "http://127.1.2.3/",
            "http://0.0.0.0/",
            "http://10.0.0.1/",
            "http://172.16.0.1/",
            "http://172.31.255.255/",
            "http://192.168.0.1/",
            "http://169.254.169.254/latest/meta-data/",
            "http://100.64.0.1/",
            "http://[::1]/",
            "http://[::]/",
            "http://[fc00::1]/",
            "http://[fd12:3456::1]/",
            "http://[fe80::1]/",
            "http://[::ffff:127.0.0.1]/",
            "http://[::ffff:169.254.169.254]/",
            "http://224.0.0.1/",
            "http://255.255.255.255/",
            // DNS 가 내부 IP 를 가리키는 도메인
            "http://evil.example.com/",
            "http://metadata.example.com/",
            // 우회 시도·형식 오류
            "http://jobs.example.com@127.0.0.1/",
            "http://user:pw@jobs.example.com/",
            "http://jobs.example.com:22/",
            "http://jobs.example.com:6379/",
            "http://unknown-host.example/",
            "http://jobs.example.com/a b",
            "",
            "   ",
    })
    void 내부_주소와_허용되지_않은_스킴은_거부(String url) {
        assertThatThrownBy(() -> guard.validate(url))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_URL);
    }

    @Test
    void DNS_응답_중_하나라도_내부_IP면_거부() throws Exception {
        UrlGuard mixed = new UrlGuard(host -> List.of(
                InetAddress.ofLiteral("93.184.216.34"), InetAddress.ofLiteral("127.0.0.1")));

        assertThatThrownBy(() -> mixed.validate("https://round-robin.example.com/"))
                .isInstanceOf(BusinessException.class);
    }
}
