package com.furelise.skillmanagement.mcp.service;

import com.furelise.skillmanagement.mcp.dto.McpSuggestionResponse;
import com.furelise.skillmanagement.mcp.dto.McpSuggestionResponse.FuzzySuggestion;
import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.repository.McpMasterRepository;
import com.furelise.skillmanagement.mcp.util.JaccardSimilarity;
import com.furelise.skillmanagement.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * MCP suggestion service (Skill ↔ MCP integration — fuzzy matching for the
 * Add/Edit Skill MCP picker).
 *
 * <p>Rebuilt 2026-11 on the Skill model: fuzzy match (weighted overlap) —
 * {@code finalScore = 0.6 × Overlap(skill, mcpName+hashtags)
 * + 0.4 × Overlap(skill, mcpDescription)}; threshold 0.2; Unicode-aware
 * tokenizer (Vietnamese diacritic-insensitive) + stopword filter.
 * Only visible APPROVED MCPs are candidates. Top 3.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpSuggestionService {

    private static final int TOP_N = 3;

    /** Description is capped before tokenization (leading part is the
     *  summary; the tail is repetitive detail). */
    private static final int DESCRIPTION_CAP = 500;

    private final McpMasterRepository mcpMasterRepository;

    /**
     * Computes MCP suggestions for an Add/Edit Skill form.
     *
     * @param skillName     skill name (for fuzzy)
     * @param skillDesc     skill description (for fuzzy)
     * @param skillHashtags skill hashtags (for fuzzy)
     * @param viewer        the logged-in user (for visibility filtering)
     */
    @Transactional(readOnly = true)
    public McpSuggestionResponse suggest(String skillName, String skillDesc,
                                         List<String> skillHashtags,
                                         User viewer) {
        List<FuzzySuggestion> fuzzy = fuzzyMatch(skillName, skillDesc, skillHashtags, viewer);
        return McpSuggestionResponse.builder()
                .suggestions(fuzzy)
                .build();
    }

    private List<FuzzySuggestion> fuzzyMatch(String skillName, String skillDesc,
                                             List<String> skillHashtags, User viewer) {
        // Build the skill text blob for matching
        String skillText = joinNonNull(skillName, skillDesc,
                skillHashtags == null ? "" : String.join(" ", skillHashtags));
        if (skillText.isBlank()) {
            return List.of();
        }

        // Load visible APPROVED MCPs (a version-update-pending MCP still
        // serves its live approved version, so it is a valid candidate too).
        Specification<McpMaster> spec = Specification.allOf(
                McpMasterRepository.visibleTo(viewer),
                (root, query, cb) -> cb.equal(root.get("status"), McpMaster.STATUS_APPROVED));

        List<McpMaster> candidates = mcpMasterRepository.findAll(spec);
        if (candidates.isEmpty()) {
            return List.of();
        }

        // Score each candidate:
        //   finalScore = 0.6 × Overlap(skill, mcpName+hashtags)
        //              + 0.4 × Overlap(skill, mcpDescription)
        // Name/hashtags match is weighted higher: an MCP that only matches
        // via description can never outrank one that matches by name.
        List<FuzzySuggestion> scored = new ArrayList<>();
        for (McpMaster m : candidates) {
            String nameTags = joinNonNull(m.getMcpName(),
                    m.getHashtags() == null ? "" : String.join(" ", m.getHashtags()));
            String description = plainText(m.getDescription());
            if (description != null && description.length() > DESCRIPTION_CAP) {
                description = description.substring(0, DESCRIPTION_CAP);
            }
            double score = JaccardSimilarity.WEIGHT_NAME_TAGS
                            * JaccardSimilarity.overlap(skillText, nameTags)
                    + JaccardSimilarity.WEIGHT_DESCRIPTION
                            * JaccardSimilarity.overlap(skillText, description == null ? "" : description);
            if (score >= JaccardSimilarity.THRESHOLD) {
                scored.add(FuzzySuggestion.builder()
                        .mcpId(m.getId())
                        .mcpCode(m.getMcpCode())
                        .mcpName(m.getMcpName())
                        .serverKey(m.getServerKey())
                        .currentVersion(m.getVersion())
                        .score(score)
                        .build());
            }
        }
        // Sort by score desc, take top N
        scored.sort(Comparator.comparingDouble(FuzzySuggestion::getScore).reversed());
        return scored.size() > TOP_N ? scored.subList(0, TOP_N) : scored;
    }

    /** Strip HTML tags + collapse whitespace. */
    private static String plainText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String text = html.replaceAll("<[^>]+>", " ");
        text = text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        text = text.replaceAll("\\s+", " ").trim();
        return text.isEmpty() ? null : text;
    }

    private static String joinNonNull(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(p);
            }
        }
        return sb.toString();
    }
}
