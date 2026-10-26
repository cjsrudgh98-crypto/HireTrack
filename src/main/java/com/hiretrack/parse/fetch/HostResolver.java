package com.hiretrack.parse.fetch;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/** DNS 조회를 테스트에서 바꿔 끼울 수 있게 분리한 인터페이스. */
@FunctionalInterface
public interface HostResolver {

    List<InetAddress> resolve(String host) throws UnknownHostException;

    static HostResolver system() {
        return host -> List.of(InetAddress.getAllByName(host));
    }
}
