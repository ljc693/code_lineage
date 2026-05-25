package com.forfun.codel_ineage.analyzer.governance;

import com.forfun.codel_ineage.analyzer.fetch.FetchedCode;

public interface GovernanceService {
    GovernanceMetrics analyze(FetchedCode code);
}
