package com.pricemanagement.service.notification;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NotificationChannelProviderRegistry {

    private final Map<String, NotificationChannelProvider> providers;

    public NotificationChannelProviderRegistry(List<NotificationChannelProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(NotificationChannelProvider::channel, Function.identity()));
    }

    public Optional<NotificationChannelProvider> find(String channel) {
        return Optional.ofNullable(providers.get(channel));
    }

    public boolean hasProvider(String channel) {
        return providers.containsKey(channel);
    }

    public Set<String> channels() {
        return providers.keySet();
    }
}
