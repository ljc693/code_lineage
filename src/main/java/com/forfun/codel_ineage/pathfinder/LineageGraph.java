package com.forfun.codel_ineage.pathfinder;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class LineageGraph {
    private List<G6Node> nodes;
    private List<G6Edge> edges;
    private List<G6Combo> combos;
    private Map<String, Object> meta;

    @Data
    @Builder
    public static class G6Node {
        private String id;
        private String type;
        private String label;
        private String comboId;
        private Map<String, Object> data;
        private Map<String, Object> style;
    }

    @Data
    @Builder
    public static class G6Edge {
        private String id;
        private String source;
        private String target;
        private String type;
        private String subType;
        private Map<String, Object> style;
    }

    @Data
    @Builder
    public static class G6Combo {
        private String id;
        private String type;
        private String label;
        private boolean collapsed;
    }
}
