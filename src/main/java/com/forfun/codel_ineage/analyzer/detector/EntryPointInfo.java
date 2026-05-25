package com.forfun.codel_ineage.analyzer.detector;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EntryPointInfo {
    private String type;        // HTTP, RPC, MQ, CRON
    private String path;        // HTTP path or RPC interface name
    private String httpMethod;  // GET/POST for HTTP, method name for RPC
    private String framework;   // Spring, Dubbo, gRPC, Custom
    private boolean isClassLevel; // true if annotation on class, false if on method
}
