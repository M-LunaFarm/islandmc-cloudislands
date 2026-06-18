package kr.lunaf.cloudislands.common.packaging;

import java.util.List;

public final class ResultRepositoryPublicationPolicy {
    public static final String REPOSITORY_ROOT = "result";
    public static final String GIT_SCOPE_POLICY = "git-operations-target-result-root-only";
    public static final String DOCUMENTATION_POLICY = "result-tree-is-code-and-build-output-only-no-markdown-documents";
    public static final String PUBLICATION_VISIBILITY_POLICY = "github-repository-must-be-public";
    public static final String PUSH_POLICY = "commit-locally-and-push-main-after-credentialed-publication";
    public static final String COMPLETION_AUDIT_POLICY = "complete-only-after-result-root-build-test-public-clone-and-doc-free-verification";
    public static final String REMOTE_REPOSITORY = "M-LunaFarm/islandmc-cloudislands";

    private static final List<String> MARKDOWN_DENY_PATTERNS = List.of(
        "*.md",
        "*.MD",
        "*.mdx",
        "*.MDX",
        "*.mdown",
        "*.MDOWN",
        "*.mkdn",
        "*.MKDN",
        "*.markdown",
        "*.MARKDOWN",
        "*.mkd",
        "*.MKD"
    );

    private static final List<String> REQUIRED_PUBLICATION_EVIDENCE = List.of(
        "git-root-is-result",
        "no-markdown-files-under-result",
        "public-github-repo-private-false",
        "main-branch-pushed",
        "fresh-public-clone-builds",
        "tests-pass-in-result-root"
    );

    private ResultRepositoryPublicationPolicy() {
    }

    public static List<String> markdownDenyPatterns() {
        return MARKDOWN_DENY_PATTERNS;
    }

    public static List<String> requiredPublicationEvidence() {
        return REQUIRED_PUBLICATION_EVIDENCE;
    }

    public static boolean markdownDenied(String filename) {
        if (filename == null) {
            return false;
        }
        String normalized = filename.trim();
        return MARKDOWN_DENY_PATTERNS.stream()
            .map(pattern -> pattern.substring(1))
            .anyMatch(normalized::endsWith);
    }

    public static boolean completionEvidenceRequired(String evidence) {
        return evidence != null && REQUIRED_PUBLICATION_EVIDENCE.contains(evidence);
    }
}
