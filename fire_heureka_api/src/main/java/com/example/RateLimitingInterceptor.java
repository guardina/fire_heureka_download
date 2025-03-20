package com.example;

import okhttp3.Interceptor;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;

public class RateLimitingInterceptor implements Interceptor {
    private final RateLimiter rateLimiter = RateLimiter.of(
        "okhttp-rate-limiter",
        RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .limitForPeriod(10)
            .timeoutDuration(Duration.ofMillis(2000))
            .build()
    );

    @Override
    public Response intercept(Chain chain) throws IOException {
        Supplier<Response> responseSupplier = RateLimiter.decorateSupplier(rateLimiter, () -> {
            try {
                return chain.proceed(chain.request());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return responseSupplier.get();
    }
}
