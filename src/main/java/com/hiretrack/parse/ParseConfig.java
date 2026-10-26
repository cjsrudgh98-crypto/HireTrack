package com.hiretrack.parse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hiretrack.parse.fetch.HostResolver;
import com.hiretrack.parse.fetch.JsoupPageFetcher;
import com.hiretrack.parse.fetch.PageFetcher;
import com.hiretrack.parse.fetch.UrlGuard;

@Configuration
public class ParseConfig {

    @Bean
    public UrlGuard urlGuard() {
        return new UrlGuard(HostResolver.system());
    }

    @Bean
    public PageFetcher pageFetcher(UrlGuard urlGuard, ParseProperties properties) {
        return new JsoupPageFetcher(urlGuard, (int) properties.timeout().toMillis(),
                (int) properties.maxBodySize().toBytes());
    }
}
