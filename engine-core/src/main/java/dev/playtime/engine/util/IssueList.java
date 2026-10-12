package dev.playtime.engine.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects issues so one broken file or device never aborts loading the rest of a map. */
public final class IssueList {
    private final List<Issue> issues = new ArrayList<>();

    public void add(Issue issue) { issues.add(issue); }

    public void error(String source, String message) { add(Issue.error(source, message)); }

    public void warning(String source, String message) { add(Issue.warning(source, message)); }

    public void info(String source, String message) { add(Issue.info(source, message)); }

    public void addAll(IssueList other) { issues.addAll(other.issues); }

    public List<Issue> all() { return Collections.unmodifiableList(issues); }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == Issue.Severity.ERROR);
    }

    public long count(Issue.Severity severity) {
        return issues.stream().filter(i -> i.severity() == severity).count();
    }

    public boolean isEmpty() { return issues.isEmpty(); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Issue i : issues) sb.append(i).append('\n');
        return sb.toString();
    }
}
