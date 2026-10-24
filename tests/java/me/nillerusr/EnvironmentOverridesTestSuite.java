package me.nillerusr;

import java.util.ArrayList;
import java.util.List;

public final class EnvironmentOverridesTestSuite {
    private static int assertions;
    private EnvironmentOverridesTestSuite() { }

    public static void main(String[] args) {
        emptyInputIsNoOp();
        validEntriesPreserveValues();
        invalidInputNeverMutatesEnvironment();
        duplicateNamesAreRejected();
        reservedNamesAreRejected();
        boundsAndSyntaxAreEnforced();
        setterFailureStopsFollowingAssignments();
        System.out.println("Environment override tests passed: " + assertions + " assertions.");
    }

    private static void emptyInputIsNoOp() {
        check(EnvironmentOverrides.parse(null).isSuccess(), "null input is empty success");
        eq(0, EnvironmentOverrides.parse(null).size(), "null input has no entries");
        check(EnvironmentOverrides.parse(" ; \n ; ").isSuccess(), "blank separators are ignored");
        final List<String> calls = new ArrayList<String>();
        EnvironmentOverrides.Result result = EnvironmentOverrides.apply("", new EnvironmentOverrides.Setter() {
            @Override public boolean set(String name, String value) { calls.add(name); return true; }
        });
        check(result.isSuccess() && calls.isEmpty(), "empty configuration never calls setter");
    }

    private static void validEntriesPreserveValues() {
        EnvironmentOverrides.Result result = EnvironmentOverrides.parse(
                " LIBGL_USEVBO = 0 \nCUSTOM_LABEL=Two words;TOKEN=a=b");
        check(result.isSuccess(), "valid configuration parses");
        eq(3, result.size(), "all entries retained");
        eq("LIBGL_USEVBO", result.getEntry(0).getName(), "name whitespace trimmed");
        eq("0", result.getEntry(0).getValue(), "value whitespace trimmed");
        eq("Two words", result.getEntry(1).getValue(), "spaces preserved in values");
        eq("a=b", result.getEntry(2).getValue(), "only first equals separates name and value");
    }

    private static void invalidInputNeverMutatesEnvironment() {
        final List<String> calls = new ArrayList<String>();
        EnvironmentOverrides.Result result = EnvironmentOverrides.apply(
                "CUSTOM_FIRST=1\nINVALID-NAME=2", new EnvironmentOverrides.Setter() {
                    @Override public boolean set(String name, String value) { calls.add(name); return true; }
                });
        eq(EnvironmentOverrides.ERROR_INVALID, result.getError(), "later invalid entry rejects full list");
        check(calls.isEmpty(), "valid prefix is not applied before later validation failure");

        final List<String> reservedCalls = new ArrayList<String>();
        result = EnvironmentOverrides.apply("CUSTOM_FIRST=1;LD_PRELOAD=/tmp/not-allowed",
                new EnvironmentOverrides.Setter() {
                    @Override public boolean set(String name, String value) { reservedCalls.add(name); return true; }
                });
        eq(EnvironmentOverrides.ERROR_INVALID, result.getError(), "later reserved entry rejects full list");
        check(reservedCalls.isEmpty(), "reserved entry prevents every mutation");
    }

    private static void duplicateNamesAreRejected() {
        final List<String> calls = new ArrayList<String>();
        EnvironmentOverrides.Result result = EnvironmentOverrides.apply("LIBGL_USEVBO=0;LIBGL_USEVBO=1",
                new EnvironmentOverrides.Setter() {
                    @Override public boolean set(String name, String value) { calls.add(name); return true; }
                });
        eq(EnvironmentOverrides.ERROR_INVALID, result.getError(), "duplicate override rejected");
        check(calls.isEmpty(), "duplicate never partially applies earlier value");
    }

    private static void reservedNamesAreRejected() {
        String[] names = {
                "VALVE_GAME_PATH", "APP_DATA_PATH", "APP_LIB_PATH", "EXTRAS_VPK_PATH",
                "APP_MOD_LIB", "LANG", "LD_LIBRARY_PATH", "LD_PRELOAD", "PATH",
                "ANDROID_ROOT", "ANDROID_DATA", "ANDROID_CUSTOM_SETTING"
        };
        for (String name : names) {
            eq(EnvironmentOverrides.ERROR_INVALID, EnvironmentOverrides.parse(name + "=value").getError(),
                    "reserved variable rejected: " + name);
        }
        check(EnvironmentOverrides.parse("LIBGL_USEVBO=0").isSuccess(), "supported GL override allowed");
    }

    private static void boundsAndSyntaxAreEnforced() {
        eq(EnvironmentOverrides.ERROR_INVALID, EnvironmentOverrides.parse("A=" + repeat('x', 8191)).getError(),
                "whole input bound");
        eq(EnvironmentOverrides.ERROR_INVALID, EnvironmentOverrides.parse("A=" + repeat('x', 2047)).getError(),
                "entry bound");
        eq(EnvironmentOverrides.ERROR_INVALID,
                EnvironmentOverrides.parse("CUSTOM=value" + ((char) 0) + "tail").getError(), "NUL rejected");
        StringBuilder tooMany = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            if (i > 0) tooMany.append(';');
            tooMany.append("CUSTOM_").append(i).append('=').append(i);
        }
        eq(EnvironmentOverrides.ERROR_INVALID, EnvironmentOverrides.parse(tooMany.toString()).getError(),
                "more than 64 entries rejected");
        eq(EnvironmentOverrides.ERROR_INVALID, EnvironmentOverrides.parse("=missing-name").getError(),
                "missing name rejected");
        eq(EnvironmentOverrides.ERROR_INVALID, EnvironmentOverrides.parse("BAD-NAME=value").getError(),
                "invalid name syntax rejected");
    }

    private static void setterFailureStopsFollowingAssignments() {
        final List<String> calls = new ArrayList<String>();
        EnvironmentOverrides.Result result = EnvironmentOverrides.apply("FIRST=1;SECOND=2;THIRD=3",
                new EnvironmentOverrides.Setter() {
                    @Override public boolean set(String name, String value) {
                        calls.add(name);
                        return !"SECOND".equals(name);
                    }
                });
        eq(EnvironmentOverrides.ERROR_APPLY_FAILED, result.getError(), "native setter error returned");
        eq("SECOND", result.getFailedName(), "failed variable identified");
        eq(2, calls.size(), "later variables not applied after setter failure");
    }
    private static String repeat(char ch, int count) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < count; i++) value.append(ch);
        return value.toString();
    }
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static void eq(Object expected, Object actual, String message) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }
}
