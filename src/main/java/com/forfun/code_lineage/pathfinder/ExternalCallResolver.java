package com.forfun.code_lineage.pathfinder;

import com.forfun.code_lineage.graph.Neo4jMethodRepository;
import com.forfun.code_lineage.model.graph.CallsEdge;
import com.forfun.code_lineage.model.graph.MethodNode;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Resolves EXTERNAL CALLS edges to target entry methods across applications.
 * Matches by HTTP URL path, Dubbo interface name, or MQ topic.
 */
@Component
public class ExternalCallResolver {

    private final Neo4jMethodRepository methodRepo;

    public ExternalCallResolver(Neo4jMethodRepository methodRepo) {
        this.methodRepo = methodRepo;
    }

    /**
     * Attempt to resolve an external call to a known entry method.
     * @param edge the EXTERNAL CALLS edge
     * @return resolved entry method, or null if not found
     */
    public ResolvedTarget resolve(CallsEdge edge) {
        String expression = edge.getCallExpression();
        if (expression == null) return null;

        // Try URL path matching (HTTP)
        String urlPath = extractUrlPath(expression);
        if (urlPath != null) {
            return resolveByHttpPath(urlPath, edge);
        }

        // Try Dubbo interface matching
        String dubboInterface = extractDubboInterface(expression);
        if (dubboInterface != null) {
            return resolveByDubboInterface(dubboInterface, edge);
        }

        return null;
    }

    private ResolvedTarget resolveByHttpPath(String path, CallsEdge edge) {
        return methodRepo.findAll().stream()
                .filter(m -> m.isEntry() && path.equals(m.getHttpPath()))
                .filter(m -> !Objects.equals(m.getAppId(), extractAppId(edge)))
                .findFirst()
                .map(m -> new ResolvedTarget(
                        m.getMethodId(), m.getAppId(), m.getClassName(), m.getSignature(), "HTTP"))
                .orElse(null);
    }

    private ResolvedTarget resolveByDubboInterface(String iface, CallsEdge edge) {
        return methodRepo.findAll().stream()
                .filter(m -> m.isEntry() && m.getClassName() != null
                        && m.getClassName().contains(iface))
                .filter(m -> !Objects.equals(m.getAppId(), extractAppId(edge)))
                .findFirst()
                .map(m -> new ResolvedTarget(
                        m.getMethodId(), m.getAppId(), m.getClassName(), m.getSignature(), "Dubbo"))
                .orElse(null);
    }

    private String extractAppId(CallsEdge edge) {
        // Source method ID format: appId:package.Class.method(params)
        String id = edge.getSourceMethodId();
        int colon = id.indexOf(':');
        return colon > 0 ? id.substring(0, colon) : null;
    }

    static String extractUrlPath(String expression) {
        // e.g., "restTemplate.postForObject(\"http://host/api/users\", ...)"
        // → "/api/users"
        int httpIdx = expression.indexOf("http://");
        if (httpIdx < 0) httpIdx = expression.indexOf("https://");
        if (httpIdx < 0) return null;

        String afterHost = expression.substring(httpIdx);
        int firstSlash = afterHost.indexOf('/', 8); // skip "http://..."
        if (firstSlash < 0) return null;

        int pathEnd = afterHost.indexOf('"', firstSlash);
        if (pathEnd < 0) pathEnd = afterHost.indexOf(',', firstSlash);
        if (pathEnd < 0) pathEnd = afterHost.indexOf(')', firstSlash);
        return pathEnd > 0 ? afterHost.substring(firstSlash, pathEnd) : afterHost.substring(firstSlash);
    }

    static String extractDubboInterface(String expression) {
        // e.g., "dubboService.someMethod(...)" or interface reference
        if (!expression.contains("dubbo") && !expression.contains("Dubbo")) return null;
        // Extract the interface/method name
        int lastDot = expression.lastIndexOf('.');
        if (lastDot > 0) {
            String methodName = expression.substring(lastDot + 1);
            int paren = methodName.indexOf('(');
            return paren > 0 ? methodName.substring(0, paren) : methodName;
        }
        return null;
    }

    public record ResolvedTarget(String targetMethodId, String targetAppId,
                                  String className, String signature, String protocol) {}
}
