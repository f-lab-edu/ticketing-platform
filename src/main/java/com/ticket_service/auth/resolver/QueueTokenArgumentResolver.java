package com.ticket_service.auth.resolver;

import com.ticket_service.auth.annotation.QueueToken;
import com.ticket_service.auth.dto.QueueTokenClaims;
import com.ticket_service.auth.service.QueueTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class QueueTokenArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CONCERT_ID_PATH_VARIABLE = "concertId";

    private final QueueTokenService queueTokenService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(QueueToken.class)
                && parameter.getParameterType().equals(QueueTokenClaims.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        String authHeader = webRequest.getHeader(AUTHORIZATION_HEADER);
        String token = extractToken(authHeader);
        Long concertId = extractConcertIdFromPath(webRequest);

        return queueTokenService.validateAndGetClaims(token, concertId);
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        throw new IllegalArgumentException("Invalid Authorization header");
    }

    @SuppressWarnings("unchecked")
    private Long extractConcertIdFromPath(NativeWebRequest webRequest) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new IllegalStateException("HttpServletRequest not available");
        }

        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        if (pathVariables == null || !pathVariables.containsKey(CONCERT_ID_PATH_VARIABLE)) {
            throw new IllegalStateException("concertId path variable not found");
        }

        return Long.parseLong(pathVariables.get(CONCERT_ID_PATH_VARIABLE));
    }
}
