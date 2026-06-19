package kr.lunaf.cloudislands.common.packaging;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultRepositoryPublicationPolicyTest {
    @Test
    void pinsResultOnlyPublicRepositoryBoundary() {
        assertEquals("result", ResultRepositoryPublicationPolicy.REPOSITORY_ROOT);
        assertEquals("M-LunaFarm/islandmc-cloudislands", ResultRepositoryPublicationPolicy.REMOTE_REPOSITORY);
        assertEquals("git-operations-target-result-root-only", ResultRepositoryPublicationPolicy.GIT_SCOPE_POLICY);
        assertEquals("source-tree-may-keep-operator-docs-packaged-artifacts-exclude-markdown", ResultRepositoryPublicationPolicy.DOCUMENTATION_POLICY);
        assertEquals("github-repository-must-be-public", ResultRepositoryPublicationPolicy.PUBLICATION_VISIBILITY_POLICY);
        assertEquals("commit-locally-and-push-main-after-credentialed-publication", ResultRepositoryPublicationPolicy.PUSH_POLICY);
        assertEquals("do-not-retry-known-invalid-github-token-use-fresh-credential", ResultRepositoryPublicationPolicy.PUSH_AUTH_FAILURE_POLICY);
    }

    @Test
    void deniesCommonMarkdownDocumentExtensions() {
        assertTrue(ResultRepositoryPublicationPolicy.markdownDenied("README.md"));
        assertTrue(ResultRepositoryPublicationPolicy.markdownDenied("README.Md"));
        assertTrue(ResultRepositoryPublicationPolicy.markdownDenied("guide.MD"));
        assertTrue(ResultRepositoryPublicationPolicy.markdownDenied("notes.mdx"));
        assertTrue(ResultRepositoryPublicationPolicy.markdownDenied("plan.mdown"));
        assertTrue(ResultRepositoryPublicationPolicy.markdownDenied("task.mkdn"));
        assertTrue(ResultRepositoryPublicationPolicy.markdownDenied("book.markdown"));
        assertTrue(ResultRepositoryPublicationPolicy.markdownDenied("PLAN.MarkDown"));
        assertTrue(ResultRepositoryPublicationPolicy.markdownDenied("chapter.mkd"));
        assertFalse(ResultRepositoryPublicationPolicy.markdownDenied("plugin.yml"));
        assertFalse(ResultRepositoryPublicationPolicy.markdownDenied(null));
    }

    @Test
    void requiresEvidenceBeforeGoalCompletion() {
        assertEquals(
            "complete-only-after-result-root-build-test-public-clone-and-packaged-markdown-exclusion",
            ResultRepositoryPublicationPolicy.COMPLETION_AUDIT_POLICY
        );
        assertTrue(ResultRepositoryPublicationPolicy.completionEvidenceRequired("git-root-is-result"));
        assertTrue(ResultRepositoryPublicationPolicy.completionEvidenceRequired("packaged-artifacts-exclude-markdown-docs"));
        assertTrue(ResultRepositoryPublicationPolicy.completionEvidenceRequired("public-github-repo-private-false"));
        assertTrue(ResultRepositoryPublicationPolicy.completionEvidenceRequired("main-branch-pushed"));
        assertTrue(ResultRepositoryPublicationPolicy.completionEvidenceRequired("fresh-public-clone-builds"));
        assertTrue(ResultRepositoryPublicationPolicy.completionEvidenceRequired("tests-pass-in-result-root"));
        assertFalse(ResultRepositoryPublicationPolicy.completionEvidenceRequired("chat-summary-only"));
    }

    @Test
    void checksArtifactPathsForMarkdownDocuments() {
        assertTrue(ResultRepositoryPublicationPolicy.markdownFree(List.of(
            "settings.gradle.kts",
            "cloudislands-satis/src/main/resources/plugin.yml",
            "cloudislands-common/src/main/java/Policy.java",
            ".git/COMMIT_EDITMSG"
        )));
        assertFalse(ResultRepositoryPublicationPolicy.markdownFree(List.of(
            "cloudislands-satis/src/main/resources/plugin.yml",
            "docs/runbook.md"
        )));
        assertFalse(ResultRepositoryPublicationPolicy.markdownFree(List.of(
            "docs/goal.markdown"
        )));
        assertTrue(ResultRepositoryPublicationPolicy.markdownFree(null));
    }

    @Test
    void policyCollectionsAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> ResultRepositoryPublicationPolicy.markdownDenyPatterns().add("*.rst"));
        assertThrows(UnsupportedOperationException.class, () -> ResultRepositoryPublicationPolicy.requiredPublicationEvidence().add("manual-claim"));
    }

    @Test
    void avoidsRepeatingKnownInvalidPushCredentials() {
        assertFalse(ResultRepositoryPublicationPolicy.retryPushAllowed(true));
        assertTrue(ResultRepositoryPublicationPolicy.retryPushAllowed(false));
    }
}
