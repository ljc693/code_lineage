package com.forfun.codel_ineage.fetcher;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class FetchedCode {
    private String baseDir;
    private List<String> changedFiles;
    private String commitLog;
    private String appId;
    private String branch;
}
