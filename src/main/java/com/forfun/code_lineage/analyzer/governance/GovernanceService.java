package com.forfun.code_lineage.analyzer.governance;

import com.forfun.code_lineage.analyzer.fetch.FetchedCode;

public interface GovernanceService {
    GovernanceMetrics analyze(FetchedCode code);
}
