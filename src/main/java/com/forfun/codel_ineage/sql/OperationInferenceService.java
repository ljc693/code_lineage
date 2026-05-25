package com.forfun.codel_ineage.sql;

import com.forfun.codel_ineage.model.MethodNode;
import com.forfun.codel_ineage.model.RawRelation;
import com.forfun.codel_ineage.model.SqlOperation;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Infers SQL operation type (SELECT/INSERT/UPDATE/DELETE) from the
 * actual mapper method calls captured in AST RawRelations.
 *
 * <p>Priority: INSERT &gt; DELETE &gt; UPDATE &gt; SELECT.
 * If a method calls both {@code insert} and {@code updateById}
 * (save-or-update pattern), INSERT wins. If it calls both
 * {@code delete} and {@code update}, DELETE wins.</p>
 */
@Component
public class OperationInferenceService {

    private static final Set<String> INSERT_METHODS = Set.of("insert", "save");
    private static final Set<String> UPDATE_METHODS = Set.of("update", "updateById");
    private static final Set<String> DELETE_METHODS = Set.of("delete", "deleteById",
            "deleteBatchIds", "deleteByMap");

    /**
     * Builds a lookup: mapperFqcn → set of method signatures called on that mapper.
     *
     * @param relations all raw relations from the AST scan
     * @param model     the project model with variable-to-mapper mappings
     * @return mapper FQCN → set of method signatures called on it
     */
    public Map<String, Set<String>> buildCallSignatures(List<RawRelation> relations,
                                                         ProjectModel model) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (RawRelation rel : relations) {
            String calleeClass = rel.getCallee() != null ? rel.getCallee().getClassName() : null;
            if (calleeClass == null) continue;

            // resolve variable name → mapper FQCN
            Set<String> mapperFqcns = model.varToMappers().get(calleeClass);
            if (mapperFqcns == null) {
                // try direct class name match
                for (String mfq : model.mapperToTable().keySet()) {
                    if (mfq.endsWith("." + calleeClass) || mfq.equals(calleeClass)) {
                        mapperFqcns = Set.of(mfq);
                        break;
                    }
                }
            }
            if (mapperFqcns == null) continue;

            String sig = rel.getCallee().getSignature();
            if (sig == null) continue;
            for (String fqcn : mapperFqcns) {
                result.computeIfAbsent(fqcn, k -> new LinkedHashSet<>()).add(sig);
            }
        }
        return result;
    }

    /**
     * Returns the SQL operation for a method based on the mapper methods it calls.
     * If the method calls both insert and update (e.g. save-or-update pattern),
     * INSERT takes priority over DELETE, which takes priority over UPDATE, which
     * takes priority over SELECT.
     *
     * @param method         the method being analyzed
     * @param relations      all raw relations from the AST scan
     * @param callSignatures pre-built mapper call signatures from
     *                       {@link #buildCallSignatures}
     * @return the inferred SQL operation
     */
    public SqlOperation infer(MethodNode method, List<RawRelation> relations,
                              Map<String, Set<String>> callSignatures) {
        String methodId = method.getMethodId();
        if (methodId == null) return SqlOperation.SELECT;

        SqlOperation best = SqlOperation.SELECT;
        for (RawRelation rel : relations) {
            if (!methodId.equals(rel.getCaller().getMethodId())) continue;
            String sig = rel.getCallee().getSignature();
            if (sig == null) continue;

            for (Set<String> sigs : callSignatures.values()) {
                if (!sigs.contains(sig)) continue;
                String lower = sig.toLowerCase();
                if (INSERT_METHODS.contains(lower)) return SqlOperation.INSERT; // strongest
                if (DELETE_METHODS.contains(lower)) best = SqlOperation.DELETE;
                else if (UPDATE_METHODS.contains(lower) && best != SqlOperation.DELETE)
                    best = SqlOperation.UPDATE;
            }
        }
        return best;
    }
}
