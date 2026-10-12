package dev.playtime.engine;

import dev.playtime.engine.content.ContentLoader;
import dev.playtime.engine.content.ContentPack;
import dev.playtime.engine.util.IssueList;

public final class TestContent {
    private TestContent() {}

    public static ContentPack builtins() {
        IssueList issues = new IssueList();
        ContentPack pack = new ContentPack();
        ContentLoader.loadBuiltins(pack, issues);
        if (issues.hasErrors()) throw new AssertionError("builtin content errors:\n" + issues);
        return pack;
    }
}
