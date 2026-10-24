package me.nillerusr;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import testsupport.TestContext;

/** Stage 14 regression coverage for scan certainty, cancellation and snapshot replacement. */
public final class Stage14GameDiscoveryTestSuite {
    private static int assertions; private static File tempRoot;
    public static void main(String[] args) throws Exception {
        tempRoot = File.createTempFile("source-engine-stage14", "root");
        if (!tempRoot.delete() || !tempRoot.mkdirs()) throw new IllegalStateException("Could not create Stage 14 test root.");
        try {
            TestContext context = new TestContext(new File(tempRoot, "native").getAbsolutePath());
            testCompleteEmptyIsNotFailure(context); testUnavailableRootIsDistinct(context);
            testInvalidInputIsDistinct(context); testCancelledScanCannotCommit(context);
            testDepthLimitedScanCannotCommit(context); testSnapshotRequiresEveryRootToComplete(context);
            System.out.println("Stage 14 game-discovery tests passed: " + assertions + " assertions.");
        } finally { deleteRecursively(tempRoot); }
    }
    private static void testCompleteEmptyIsNotFailure(TestContext context) throws Exception {
        File root = new File(tempRoot, "empty-library-root"); check(root.mkdirs(), "create empty root");
        GameDiscovery.ScanResult result = GameDiscovery.scanDetailed(context, root.getAbsolutePath());
        check(result.isComplete(), "empty readable root completes"); check(result.hasNoCandidates(), "zero candidates is a complete empty result");
        check(GameDiscovery.canCommitSnapshot(Arrays.asList(result), false, false), "complete empty scan can replace snapshot");
    }
    private static void testUnavailableRootIsDistinct(TestContext context) {
        File missing = new File(tempRoot, "removed-or-unmounted-root");
        GameDiscovery.ScanResult result = GameDiscovery.scanDetailed(context, missing.getAbsolutePath());
        equal(GameDiscovery.RootStatus.UNAVAILABLE, result.rootStatus, "vanished root is unavailable storage");
        check(!result.rootAccessible && !result.isComplete() && !result.hasNoCandidates(), "unavailable root is not empty success");
        check(!GameDiscovery.canCommitSnapshot(Arrays.asList(result), false, false), "unavailable root cannot replace snapshot");
    }
    private static void testInvalidInputIsDistinct(TestContext context) {
        GameDiscovery.ScanResult result = GameDiscovery.scanDetailed(context, "   ");
        equal(GameDiscovery.RootStatus.INVALID_INPUT, result.rootStatus, "blank root is invalid input");
        check(!result.isComplete(), "invalid input is incomplete");
    }
    private static void testCancelledScanCannotCommit(TestContext context) throws Exception {
        File root = new File(tempRoot, "cancelled-root"); check(root.mkdirs(), "create cancellation root");
        GameDiscovery.CancellationToken token = new GameDiscovery.CancellationToken(); token.cancel();
        GameDiscovery.ScanResult result = GameDiscovery.scanDetailed(context, root.getAbsolutePath(), token);
        check(result.cancelled, "cancel token produces explicit cancelled result");
        check(!result.isComplete() && !result.hasNoCandidates(), "cancelled scan is not empty success");
        check(!GameDiscovery.canCommitSnapshot(Arrays.asList(result), false, true), "cancelled scan cannot replace snapshot");
    }
    private static void testDepthLimitedScanCannotCommit(TestContext context) throws Exception {
        File root = new File(tempRoot, "depth-root"); check(root.mkdirs(), "create depth root"); File nested = root;
        for (int i = 0; i <= GameDiscovery.MAX_DEPTH; i++) { nested = new File(nested, "level" + i); check(nested.mkdir(), "create depth level " + i); }
        GameDiscovery.ScanResult result = GameDiscovery.scanDetailed(context, root.getAbsolutePath());
        check(result.depthLimitReached && !result.isComplete(), "depth limit is explicit partial result");
        check(!GameDiscovery.canCommitSnapshot(Arrays.asList(result), false, false), "partial scan cannot replace snapshot");
    }
    private static void testSnapshotRequiresEveryRootToComplete(TestContext context) throws Exception {
        File first = new File(tempRoot, "complete-root"), second = new File(tempRoot, "partial-root");
        check(first.mkdir(), "create complete root"); check(second.mkdir(), "create mixed partial root");
        GameDiscovery.ScanResult complete = GameDiscovery.scanDetailed(context, first.getAbsolutePath()); File nested = second;
        for (int i = 0; i <= GameDiscovery.MAX_DEPTH; i++) { nested = new File(nested, "deeper" + i); check(nested.mkdir(), "create mixed depth " + i); }
        GameDiscovery.ScanResult partial = GameDiscovery.scanDetailed(context, second.getAbsolutePath());
        List<GameDiscovery.ScanResult> mixed = new ArrayList<GameDiscovery.ScanResult>(); mixed.add(complete); mixed.add(partial);
        check(!GameDiscovery.canCommitSnapshot(mixed, false, false), "one partial root blocks mixed snapshot commit");
        check(GameDiscovery.canCommitSnapshot(Arrays.asList(complete), false, false), "all complete roots may commit snapshot");
        check(!GameDiscovery.canCommitSnapshot(Arrays.asList(complete), true, false), "worker failure blocks replacement");
        check(!GameDiscovery.canCommitSnapshot(new ArrayList<GameDiscovery.ScanResult>(), false, false), "zero scanned roots is not success");
    }
    private static void deleteRecursively(File file) { if (file == null || !file.exists()) return; File[] children=file.listFiles(); if(children!=null) for(File child:children) deleteRecursively(child); file.delete(); }
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
    private static void equal(Object expected,Object actual,String label) { assertions++; if(expected==null ? actual!=null : !expected.equals(actual)) throw new AssertionError(label+": expected <"+expected+"> but was <"+actual+">"); }
}