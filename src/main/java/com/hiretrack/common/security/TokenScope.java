package com.hiretrack.common.security;

/**
 * 토큰 권한 범위. 최소 권한 원칙: 확장 프로그램용 토큰은 공고 추출·등록과 내 정보 조회만 할 수 있다.
 * 확장 프로그램 저장소(chrome.storage)에 있는 토큰이 새어 나가도 자소서·통계 등은 볼 수 없다.
 */
public enum TokenScope {
    APP,
    EXTENSION;

    public String authority() {
        return "SCOPE_" + name();
    }

    static TokenScope fromClaim(String claim) {
        // 범위 claim 이 없는 이전 토큰은 웹앱 토큰으로 본다
        return claim == null ? APP : TokenScope.valueOf(claim);
    }
}
