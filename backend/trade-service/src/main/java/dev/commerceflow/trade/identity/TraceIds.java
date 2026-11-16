package dev.commerceflow.trade.identity;

import java.util.UUID;
import org.slf4j.MDC;

public final class TraceIds {
    private TraceIds() {}
    public static String current() {
        String id = MDC.get("traceId");
        return id == null ? UUID.randomUUID().toString() : id;
    }
}
