package com.paynest.integrator.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

@Component("samplePaymentProcessor")
public class SamplePaymentProcessor implements Processor {
    @Override
    public void process(Exchange exchange) {
        exchange.getMessage().setHeader("processedBy", "SamplePaymentProcessor");
    }
}
