package com.pricemanagement.service;

import com.pricemanagement.entity.ExternalApiEndpoint;
import com.pricemanagement.repository.ExternalApiEndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExternalApiPermissionService {

    private final ExternalApiEndpointRepository endpointRepository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public Optional<ExternalApiEndpoint> match(String method, String path) {
        if (path == null || !path.startsWith("/api/external/v1/")) {
            return Optional.empty();
        }
        List<ExternalApiEndpoint> endpoints = endpointRepository.findByStatusOrderBySortOrderAsc("ACTIVE");
        return endpoints.stream()
                .filter(endpoint -> endpoint.getMethod().equalsIgnoreCase(method))
                .filter(endpoint -> pathMatcher.match(endpoint.getPathPattern(), path))
                .sorted(Comparator
                        .comparingInt((ExternalApiEndpoint endpoint) -> specificity(endpoint.getPathPattern())).reversed()
                        .thenComparing(ExternalApiEndpoint::getSortOrder))
                .findFirst();
    }

    private int specificity(String pattern) {
        int wildcards = 0;
        for (char ch : pattern.toCharArray()) {
            if (ch == '*' || ch == '?') {
                wildcards++;
            }
        }
        return pattern.length() - wildcards * 10;
    }
}
