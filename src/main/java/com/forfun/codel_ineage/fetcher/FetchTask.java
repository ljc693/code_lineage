package com.forfun.codel_ineage.fetcher;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FetchTask {
    private String repoUrl;
    private String branch;
    private String commitSha;
    private String appId;
}
