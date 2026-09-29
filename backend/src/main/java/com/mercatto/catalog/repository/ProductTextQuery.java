package com.mercatto.catalog.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The free-text part of a product search, parsed into the terms the database matches (#220).
 *
 * <ul>
 *   <li>The query is trimmed, cut to {@value #MAX_LENGTH} characters and split on whitespace;
 *       at most {@value #MAX_TERMS} tokens are kept.</li>
 *   <li>A token containing {@code %} or {@code _} becomes a <b>literal term</b>: a
 *       {@code LIKE '%…%'} pattern (with {@code \}, {@code %} and {@code _} escaped by {@code \})
 *       over the unaccented, lowercased name + brand + category + description, so those characters
 *       are plain text, never wildcards.</li>
 *   <li>Every other token is split on anything that isn't a letter or a digit ("usb-c" → "usb",
 *       "c") and lowercased into <b>full-text terms</b>. They're joined with {@code &} (every term
 *       must match, in any field, in any order) and each one of 3+ characters gets the prefix
 *       marker {@code :*}. Since terms hold only letters and digits, user input can never inject
 *       tsquery syntax ({@code & | ! ( ) : *}).</li>
 *   <li>A query with neither (e.g. {@code "!!!"}) is searched as one literal term.</li>
 * </ul>
 *
 * Immutable. Module-internal: only {@code catalog} uses it.
 */
public final class ProductTextQuery {

    static final int MAX_LENGTH = 200;
    static final int MAX_TERMS = 10;
    private static final int MIN_PREFIX_LENGTH = 3;
    private static final int MIN_CORRECTABLE_LENGTH = 4;
    private static final int TWO_EDITS_LENGTH = 8;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NOT_WORD_CHAR = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern LETTERS_ONLY = Pattern.compile("\\p{L}+");

    private final List<String> ftsTerms;
    private final List<String> likePatterns;

    private ProductTextQuery(List<String> ftsTerms, List<String> likePatterns) {
        this.ftsTerms = List.copyOf(ftsTerms);
        this.likePatterns = List.copyOf(likePatterns);
    }

    /** Parses a raw search query; {@code null} for a {@code null} or blank one. */
    public static ProductTextQuery parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String query = raw.strip();
        if (query.length() > MAX_LENGTH) {
            query = query.substring(0, MAX_LENGTH).strip();
        }
        List<String> fts = new ArrayList<>();
        List<String> likes = new ArrayList<>();
        String[] tokens = WHITESPACE.split(query);
        for (int i = 0; i < tokens.length && i < MAX_TERMS; i++) {
            String token = tokens[i].toLowerCase(Locale.ROOT);
            if (token.indexOf('%') >= 0 || token.indexOf('_') >= 0) {
                likes.add(containsPattern(token));
                continue;
            }
            for (String part : NOT_WORD_CHAR.split(token)) {
                if (!part.isEmpty() && fts.size() < MAX_TERMS) {
                    fts.add(part);
                }
            }
        }
        if (fts.isEmpty() && likes.isEmpty()) {
            likes.add(containsPattern(query.toLowerCase(Locale.ROOT)));
        }
        return new ProductTextQuery(fts, likes);
    }

    /** {@code LIKE} pattern matching {@code text} anywhere, with {@code \} as escape character. */
    static String containsPattern(String text) {
        String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    public List<String> ftsTerms() {
        return ftsTerms;
    }

    public boolean hasFtsTerms() {
        return !ftsTerms.isEmpty();
    }

    /** {@code LIKE} patterns (escape character {@code \}) that must all match. */
    public List<String> likePatterns() {
        return likePatterns;
    }

    /**
     * The {@code to_tsquery} expression of the full-text terms: {@code term1:* & term2 & …},
     * {@code null} when there are none.
     */
    public String tsQuery() {
        if (ftsTerms.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>(ftsTerms.size());
        for (String term : ftsTerms) {
            parts.add(term.length() >= MIN_PREFIX_LENGTH ? term + ":*" : term);
        }
        return String.join(" & ", parts);
    }

    /** Full-text terms eligible for typo correction (see {@link #isCorrectable}). */
    public List<String> correctableTerms() {
        return ftsTerms.stream().filter(ProductTextQuery::isCorrectable).distinct().toList();
    }

    /** A copy with each full-text term found in {@code replacements} swapped; literal terms kept. */
    public ProductTextQuery withReplacedTerms(Map<String, String> replacements) {
        List<String> replaced = ftsTerms.stream().map(term -> replacements.getOrDefault(term, term)).toList();
        return new ProductTextQuery(replaced, likePatterns);
    }

    /**
     * Only words of letters with {@value #MIN_CORRECTABLE_LENGTH}+ characters are corrected: a
     * short word or a number/model code ("ps5", "4060") one edit away from something else is far
     * more likely a different word than a typo.
     */
    public static boolean isCorrectable(String term) {
        return term.length() >= MIN_CORRECTABLE_LENGTH && LETTERS_ONLY.matcher(term).matches();
    }

    /** Edit (Levenshtein) distance tolerated when correcting {@code term}: 1, or 2 from 8 characters. */
    public static int maxEdits(String term) {
        return term.length() >= TWO_EDITS_LENGTH ? 2 : 1;
    }
}
