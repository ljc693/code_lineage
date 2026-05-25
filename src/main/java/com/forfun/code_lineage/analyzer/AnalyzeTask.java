package com.forfun.code_lineage.analyzer;

import com.forfun.code_lineage.analyzer.fetch.FetchedCode;
import lombok.Builder;
import lombok.Data;

import java.util.function.Consumer;

@Data
@Builder
public class AnalyzeTask {
    private FetchedCode fetchedCode;
    private String appId;
    /** Optional callback for per-file progress reporting to CI/CD pipelines */
    @Builder.Default
    private Consumer<FileProgress> progressCallback = null;
}
