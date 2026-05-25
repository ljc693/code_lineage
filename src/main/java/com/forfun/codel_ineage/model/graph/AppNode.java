package com.forfun.codel_ineage.model.graph;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AppNode {
    private String appId;
    private String name;
    private String version;
    private String repoUrl;
    private String branch;
    private String systemId;    // V2 reserved
}
