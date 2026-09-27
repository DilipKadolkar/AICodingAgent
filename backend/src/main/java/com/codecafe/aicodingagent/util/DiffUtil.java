package com.codecafe.aicodingagent.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal LCS-based line diff producing a readable, git-diff-like (but not
 * byte-identical) unified view of changed lines. Bounded to keep the O(n*m)
 * LCS table small for very large files.
 */
public final class DiffUtil {

    private static final int MAX_LINES_FOR_FULL_DIFF = 4000;

    private DiffUtil() {
    }

    public static String unifiedDiff(String oldContent, String newContent, String path) {
        List<String> oldLines = splitLines(oldContent);
        List<String> newLines = splitLines(newContent);

        if (oldLines.size() + newLines.size() > MAX_LINES_FOR_FULL_DIFF) {
            return "--- a/" + path + "\n+++ b/" + path + "\n"
                    + "(diff omitted: file too large to render in full - "
                    + oldLines.size() + " -> " + newLines.size() + " lines)\n";
        }

        int n = oldLines.size();
        int m = newLines.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                dp[i][j] = oldLines.get(i).equals(newLines.get(j))
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("--- a/").append(path).append('\n');
        sb.append("+++ b/").append(path).append('\n');
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (oldLines.get(i).equals(newLines.get(j))) {
                sb.append(' ').append(oldLines.get(i)).append('\n');
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                sb.append('-').append(oldLines.get(i)).append('\n');
                i++;
            } else {
                sb.append('+').append(newLines.get(j)).append('\n');
                j++;
            }
        }
        while (i < n) {
            sb.append('-').append(oldLines.get(i)).append('\n');
            i++;
        }
        while (j < m) {
            sb.append('+').append(newLines.get(j)).append('\n');
            j++;
        }
        return sb.toString();
    }

    private static List<String> splitLines(String content) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(content.split("\n", -1)));
    }
}
