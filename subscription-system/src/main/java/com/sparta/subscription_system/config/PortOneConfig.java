package com.sparta.subscription_system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PortOneConfig {
    
    @Value("${portone.api.secret}")
    private String apiSecret;
    
    @Value("${portone.api.url}")
    private String apiUrl;
    
    @Value("${portone.store.id}")
    private String storeId;
    
    @Value("${portone.channel.key}")
    private String channelKey;
    
    public String getApiSecret() {
        return apiSecret;
    }
    
    public String getApiUrl() {
        return apiUrl;
    }
    
    public String getStoreId() {
        return storeId;
    }
    
    public String getChannelKey() {
        return channelKey;
    }
}


