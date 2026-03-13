package com.paynest.config;

import lombok.Getter;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class PropertyReader {

    private final Environment environment;

    public PropertyReader(Environment environment){
        this.environment = environment;
    }

    public String getPropertyValue(String propertyName) {
        return environment.getProperty(propertyName);
    }
}
