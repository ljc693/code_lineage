package com.forfun.codel_ineage.service;

import com.forfun.codel_ineage.analyzer.AnalyzeResult;
import com.forfun.codel_ineage.analyzer.AnalyzeTask;
import com.forfun.codel_ineage.analyzer.CodeAnalyzer;
import com.forfun.codel_ineage.analyzer.FileProgress;
import com.forfun.codel_ineage.event.*;
import com.forfun.codel_ineage.fetcher.*;
import com.forfun.codel_ineage.graph.GraphAdapter;
import com.forfun.codel_ineage.model.*;
import com.forfun.codel_ineage.sql.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScanOrchestrator {

    private final EventBus eventBus;
    private final CodeFetcher codeFetcher;
    private final CodeAnalyzer codeAnalyzer;
    private final SqlParser sqlParser;
    private final GraphAdapter graphAdapter;
    private final ScanProgressTracker progressTracker;
    private final ScanHistoryRepository historyRepo;
    private final Map<String, ScanContext> scanContexts = new ConcurrentHashMap<>();

    public ScanOrchestrator(EventBus eventBus, CodeFetcher codeFetcher,
                            CodeAnalyzer codeAnalyzer, SqlParser sqlParser,
                            GraphAdapter graphAdapter,
                            ScanProgressTracker progressTracker,
                            ScanHistoryRepository historyRepo) {
        this.eventBus = eventBus;
        this.codeFetcher = codeFetcher;
        this.codeAnalyzer = codeAnalyzer;
        this.sqlParser = sqlParser;
        this.graphAdapter = graphAdapter;
        this.progressTracker = progressTracker;
        this.historyRepo = historyRepo;
    }

    public void startScan(String traceId, String appId, String repoUrl,
                          String branch, String scanType, String commitSha) {
        scanContexts.put(traceId, new ScanContext(appId, repoUrl, branch, scanType, commitSha));
        progressTracker.start(traceId, appId, 0);
        historyRepo.insertStarted(traceId, appId, scanType, commitSha, branch, repoUrl);
        eventBus.publish(new ScanRequestedEvent(traceId, appId, scanType, commitSha));
    }

    @EventListener
    public void onScanRequested(ScanRequestedEvent event) {
        try {
            progressTracker.updateStage(event.getTraceId(),
                    ScanProgressTracker.Stage.FETCHING, 0);

            ScanContext ctx = scanContexts.get(event.getTraceId());
            FetchTask task = FetchTask.builder()
                    .appId(event.getAppId())
                    .repoUrl(ctx != null ? ctx.repoUrl : event.getAppId())
                    .branch("refs/heads/main")
                    .commitSha(event.getCommitSha())
                    .build();

            FetchedCode code = codeFetcher.fetch(task);
            eventBus.publish(new CodeFetchedEvent(event.getTraceId(), code));
        } catch (Exception e) {
            eventBus.publish(new ProcessFailedEvent(event.getTraceId(), "CodeFetcher", e.getMessage()));
        }
    }

    @EventListener
    public void onCodeFetched(CodeFetchedEvent event) {
        try {
            progressTracker.updateStage(event.getTraceId(),
                    ScanProgressTracker.Stage.ANALYZING, 0);

            ScanContext ctx = scanContexts.get(event.getTraceId());
            if (ctx != null) {
                ctx.fetchedCode = event.getFetchedCode();
            }

            AnalyzeTask task = AnalyzeTask.builder()
                    .fetchedCode(event.getFetchedCode())
                    .appId(event.getFetchedCode().getAppId())
                    .progressCallback(fp -> progressTracker.updateFileProgress(
                            event.getTraceId(), fp.fileName(), fp.currentFile(), fp.totalFiles()))
                    .build();

            AnalyzeResult result = codeAnalyzer.analyze(task);
            eventBus.publish(new CodeAnalyzedEvent(event.getTraceId(), result));
        } catch (Exception e) {
            eventBus.publish(new ProcessFailedEvent(event.getTraceId(), "CodeAnalyzer", e.getMessage()));
        }
    }

    @EventListener
    public void onCodeAnalyzed(CodeAnalyzedEvent event) {
        try {
            progressTracker.updateStage(event.getTraceId(),
                    ScanProgressTracker.Stage.SQL_PARSING, 0);

            ScanContext ctx = scanContexts.get(event.getTraceId());
            if (ctx != null) {
                ctx.analyzeResult = event.getResult();
            }

            String baseDir = ctx != null && ctx.fetchedCode != null
                    ? ctx.fetchedCode.getBaseDir() : "";

            AnalyzeResult result = event.getResult();
            progressTracker.updateStats(event.getTraceId(),
                    result.getMethods().size(), result.getRelations().size(), 0);

            ParseTask task = ParseTask.builder()
                    .baseDir(baseDir)
                    .methods(result.getMethods())
                    .appId(result.getAppId())
                    .rawRelations(result.getRelations())
                    .build();

            List<SqlRelations> sqlRelations = sqlParser.parse(task);
            eventBus.publish(new SqlParsedEvent(event.getTraceId(), sqlRelations));
        } catch (Exception e) {
            eventBus.publish(new ProcessFailedEvent(event.getTraceId(), "SqlParser", e.getMessage()));
        }
    }

    @EventListener
    public void onSqlParsed(SqlParsedEvent event) {
        try {
            progressTracker.updateStage(event.getTraceId(),
                    ScanProgressTracker.Stage.WRITING_GRAPH, 0);

            ScanContext ctx = scanContexts.remove(event.getTraceId());
            AnalyzeResult analyzeResult = ctx != null ? ctx.analyzeResult : null;
            if (analyzeResult == null) {
                eventBus.publish(new ProcessFailedEvent(event.getTraceId(), "GraphStore", "No analysis result"));
                return;
            }

            SubGraph subGraph = SubGraph.builder()
                    .methods(analyzeResult.getMethods())
                    .callsEdges(buildCallsEdges(analyzeResult.getRelations()))
                    .accessesEdges(buildAccessesEdges(event.getSqlRelations()))
                    .build();

            graphAdapter.write(subGraph);

            int entryPoints = (int) analyzeResult.getMethods().stream()
                    .filter(MethodNode::isEntry).count();
            int tables = (int) event.getSqlRelations().stream()
                    .map(SqlRelations::getTableName).distinct().count();
            int methods = analyzeResult.getMethods().size();
            int relations = analyzeResult.getRelations().size();
            int sqlRelations = event.getSqlRelations().size();

            int totalFiles = progressTracker.get(event.getTraceId()).totalFiles;
            progressTracker.complete(event.getTraceId(), methods, relations, sqlRelations, entryPoints, tables);
            historyRepo.updateCompleted(event.getTraceId(), "COMPLETED", totalFiles,
                    methods, relations, sqlRelations, entryPoints, tables, null);

            Map<String, Object> stats = Map.of(
                    "status", "completed",
                    "methods", methods,
                    "relations", relations,
                    "sqlRelations", sqlRelations,
                    "entryPoints", entryPoints,
                    "tables", tables
            );
            eventBus.publish(new ScanCompletedEvent(event.getTraceId(),
                    analyzeResult.getAppId(), stats));
        } catch (Exception e) {
            eventBus.publish(new ProcessFailedEvent(event.getTraceId(), "GraphStore", e.getMessage()));
        }
    }

    @EventListener
    public void onProcessFailed(ProcessFailedEvent event) {
        progressTracker.fail(event.getTraceId(), event.getProcessorType() + ": " + event.getError());
        historyRepo.updateCompleted(event.getTraceId(), "FAILED", 0, 0, 0, 0, 0, 0, event.getError());
        scanContexts.remove(event.getTraceId());
    }

    private List<CallsEdge> buildCallsEdges(List<RawRelation> relations) {
        int counter = 0;
        List<CallsEdge> edges = new ArrayList<>();
        for (RawRelation r : relations) {
            edges.add(CallsEdge.builder()
                    .id("c-" + (counter++))
                    .sourceMethodId(r.getCaller().getMethodId())
                    .targetMethodId(r.getCallee().getMethodId())
                    .callType(r.getCallType())
                    .lineNumber(r.getLineNumber())
                    .callExpression(r.getCallExpression())
                    .build());
        }
        return edges;
    }

    private List<AccessesEdge> buildAccessesEdges(List<SqlRelations> sqlRelations) {
        int counter = 0;
        List<AccessesEdge> edges = new ArrayList<>();
        for (SqlRelations sr : sqlRelations) {
            edges.add(AccessesEdge.builder()
                    .id("a-" + (counter++))
                    .sourceMethodId(sr.getSourceMethodId())
                    .targetTableId(sr.getTableName())
                    .operation(sr.getOperation())
                    .rawSql(sr.getRawSql())
                    .build());
        }
        return edges;
    }

    private static class ScanContext {
        final String appId;
        final String repoUrl;
        final String branch;
        final String scanType;
        final String commitSha;
        FetchedCode fetchedCode;
        AnalyzeResult analyzeResult;

        ScanContext(String appId, String repoUrl, String branch, String scanType, String commitSha) {
            this.appId = appId;
            this.repoUrl = repoUrl;
            this.branch = branch;
            this.scanType = scanType;
            this.commitSha = commitSha;
        }
    }
}
