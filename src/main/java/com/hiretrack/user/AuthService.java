package com.hiretrack.user;

import java.time.Clock;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hiretrack.common.error.BusinessException;
import com.hiretrack.common.error.ErrorCode;
import com.hiretrack.common.security.JwtProvider;
import com.hiretrack.user.dto.AuthRequest;
import com.hiretrack.user.dto.TokenResponse;
import com.hiretrack.user.dto.UserResponse;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final Clock clock;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.clock = clock;
    }

    /** 가입 후 바로 칸반으로 이동할 수 있도록 토큰까지 발급한다. */
    @Transactional
    public TokenResponse signup(AuthRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        try {
            // 같은 이메일로 동시에 가입하면 위 검사를 둘 다 통과한다 — 유니크 제약이 하나만 받아들인다
            User user = userRepository.saveAndFlush(new User(email, passwordEncoder.encode(request.password()), clock.instant()));
            return issueToken(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
    }

    public TokenResponse login(AuthRequest request) {
        return issueToken(authenticate(request));
    }

    /** 확장 프로그램 팝업 로그인 — 공고 추출·등록만 가능한 범위 제한 토큰을 발급한다 */
    public TokenResponse loginForExtension(AuthRequest request) {
        User user = authenticate(request);
        return TokenResponse.bearer(jwtProvider.createExtensionToken(user.getId()), UserResponse.from(user));
    }

    private User authenticate(AuthRequest request) {
        return userRepository.findByEmail(normalize(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
    }

    public UserResponse me(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private TokenResponse issueToken(User user) {
        return TokenResponse.bearer(jwtProvider.createAccessToken(user.getId()), UserResponse.from(user));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
