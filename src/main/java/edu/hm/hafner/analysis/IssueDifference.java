package edu.hm.hafner.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Computes old, new, and fixed issues based on the reports of two consecutive static analysis runs for the same
 * software artifact.
 *
 * @author Ullrich Hafner
 */
public class IssueDifference {
    private final Report newIssues;
    private final Report fixedIssues;
    private final Report outstandingIssues;
    private final Map<Integer, List<Issue>> referencesByHash;
    private final Map<String, List<Issue>> referencesByFingerprint;

    /**
     * Creates a new instance of {@link IssueDifference}.
     *
     * @param currentIssues
     *         the issues of the current report
     * @param referenceId
     *         ID identifying the reference report
     * @param referenceIssues
     *         the issues of a previous report (reference)
     */
    public IssueDifference(final Report currentIssues, final String referenceId, final Report referenceIssues) {
        newIssues = currentIssues.copy();
        fixedIssues = referenceIssues.copy();
        outstandingIssues = new Report();

        referencesByHash = new HashMap<Integer, List<Issue>>();
        referencesByFingerprint = new HashMap<String, List<Issue>>();

        for (Issue issue : fixedIssues) {
            addIssueToMap(referencesByHash, issue.hashCode(), issue);
            addIssueToMap(referencesByFingerprint, issue.getFingerprint(), issue);
        }

        List<UUID> removed = matchIssuesByEquals(currentIssues);
        Report secondPass = currentIssues.copy();
        removed.forEach(secondPass::remove);
        matchIssuesByFingerprint(secondPass);

        newIssues.forEach(issue -> issue.setReference(referenceId));
    }

    private List<UUID> matchIssuesByEquals(final Report currentIssues) {
        List<UUID> removedIds = new ArrayList<UUID>();
        for (Issue current : currentIssues) {
            List<Issue> equalIssues = findReferenceByEquals(current);

            if (!equalIssues.isEmpty()) {
                removedIds.add(remove(current, selectIssueWithSameFingerprint(current, equalIssues)));
            }
        }
        return removedIds;
    }

    private void matchIssuesByFingerprint(final Report currentIssues) {
        for (Issue current : currentIssues) {
            findReferenceByFingerprint(current).ifPresent(issue -> remove(current, issue));
        }
    }

    private <K> void addIssueToMap(final Map<K, List<Issue>> map, final K key, final Issue issue) {
        List<Issue> issues = map.get(key);
        if (issues == null) {
            issues = new ArrayList<Issue>();
            map.put(key, issues);
        }
        issues.add(issue);
    }

    private <K> void removeIssueFromMap(final Map<K, List<Issue>> map, final K key, final Issue issue) {
        List<Issue> issues = map.get(key);
        issues.remove(issue);
        if (issues.isEmpty()) {
            map.remove(key);
        }
    }

    private UUID remove(final Issue current, final Issue oldIssue) {
        UUID id = current.getId();
        Issue issueWithLatestProperties = newIssues.remove(id);
        issueWithLatestProperties.setReference(oldIssue.getReference());
        outstandingIssues.add(issueWithLatestProperties);
        fixedIssues.remove(oldIssue.getId());
        removeIssueFromMap(referencesByFingerprint, oldIssue.getFingerprint(), oldIssue);
        removeIssueFromMap(referencesByHash, oldIssue.hashCode(), oldIssue);
        return id;
    }

    private Issue selectIssueWithSameFingerprint(final Issue current, final List<Issue> equalIssues) {
        return equalIssues.stream()
                .filter(issue -> issue.getFingerprint().equals(current.getFingerprint()))
                .findFirst()
                .orElse(equalIssues.get(0));
    }

    private Optional<Issue> findReferenceByFingerprint(final Issue current) {
        List<Issue> references = referencesByFingerprint.get(current.getFingerprint());
        if (references != null) {
            return Optional.of(references.get(0));
        }
        return Optional.empty();
    }

    private List<Issue> findReferenceByEquals(final Issue current) {
        List<Issue> references = referencesByHash.get(current.hashCode());
        List<Issue> equalIssues = new ArrayList<>();
        if (references != null) {
            for (Issue reference : references) {
                if (current.equals(reference)) {
                    equalIssues.add(reference);
                }
            }
        }
        return equalIssues;
    }

    /**
     * Returns the outstanding issues. I.e. all issues, that are part of the previous report and that are still part of
     * the current report.
     *
     * @return the outstanding issues
     */
    public Report getOutstandingIssues() {
        return outstandingIssues;
    }

    /**
     * Returns the new issues. I.e. all issues, that are part of the current report but that have not been shown up in
     * the previous report.
     *
     * @return the new issues
     */
    public Report getNewIssues() {
        return newIssues;
    }

    /**
     * Returns the fixed issues. I.e. all issues, that are part of the previous report but that are not present in the
     * current report anymore.
     *
     * @return the fixed issues
     */
    public Report getFixedIssues() {
        return fixedIssues;
    }
}
