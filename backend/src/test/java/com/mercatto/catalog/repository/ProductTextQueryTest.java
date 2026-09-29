package com.mercatto.catalog.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTextQueryTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n"})
    void blankQueryIsNoQuery(String raw) {
        assertThat(ProductTextQuery.parse(raw)).isNull();
    }

    @Test
    void splitsOnWhitespaceAndLowercases() {
        ProductTextQuery query = ProductTextQuery.parse("  Wireless   EARBUDS\tPro ");

        assertThat(query.ftsTerms()).containsExactly("wireless", "earbuds", "pro");
        assertThat(query.likePatterns()).isEmpty();
        assertThat(query.tsQuery()).isEqualTo("wireless:* & earbuds:* & pro:*");
    }

    @Test
    void splitsTokensOnPunctuation() {
        ProductTextQuery query = ProductTextQuery.parse("usb-c cable,2m");

        assertThat(query.ftsTerms()).containsExactly("usb", "c", "cable", "2m");
        assertThat(query.tsQuery()).isEqualTo("usb:* & c & cable:* & 2m");
    }

    @Test
    void keepsAccentsForTheDatabaseToRemove() {
        assertThat(ProductTextQuery.parse("Crème brûlée").ftsTerms()).containsExactly("crème", "brûlée");
    }

    @Test
    void onlyTermsOfThreeOrMoreCharactersArePrefixes() {
        assertThat(ProductTextQuery.parse("tv 4k hdr oled").tsQuery()).isEqualTo("tv & 4k & hdr:* & oled:*");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "100%      | %100\\%%",
            "%         | %\\%%",
            "_         | %\\_%",
            "a_b       | %a\\_b%",
            "50%_off   | %50\\%\\_off%",
            "c:\\x_1   | %c:\\\\x\\_1%",
            "'1%'      | %'1\\%'%",
    })
    void tokensWithPercentOrUnderscoreBecomeEscapedLiteralPatterns(String raw, String pattern) {
        ProductTextQuery query = ProductTextQuery.parse(raw);

        assertThat(query.likePatterns()).containsExactly(pattern);
        assertThat(query.hasFtsTerms()).isFalse();
        assertThat(query.tsQuery()).isNull();
    }

    @Test
    void mixesLiteralAndFullTextTerms() {
        ProductTextQuery query = ProductTextQuery.parse("Cotton 100% shirt");

        assertThat(query.ftsTerms()).containsExactly("cotton", "shirt");
        assertThat(query.likePatterns()).containsExactly("%100\\%%");
    }

    @Test
    void punctuationOnlyQueryIsOneLiteralTerm() {
        ProductTextQuery query = ProductTextQuery.parse(" !!! ?? ");

        assertThat(query.hasFtsTerms()).isFalse();
        assertThat(query.likePatterns()).containsExactly("%!!! ??%");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a & b | c", "!foo", "(bar)", "baz:*", "qux:A", "a <-> b", "'quoted'", "x\\y", "*", "&|!():*",
    })
    void tsQueryNeverContainsUserSuppliedOperators(String raw) {
        String tsQuery = ProductTextQuery.parse(raw).tsQuery();
        if (tsQuery == null) {
            return;
        }
        // Only the generated " & " separators and ":*" suffixes; every term is letters/digits.
        for (String part : tsQuery.split(" & ")) {
            assertThat(part).matches("[\\p{L}\\p{N}]+(:\\*)?");
        }
    }

    @Test
    void capsTheNumberOfTermsAndTheQueryLength() {
        ProductTextQuery manyTerms = ProductTextQuery.parse("a b c d e f g h i j k l m");
        assertThat(manyTerms.ftsTerms()).hasSize(ProductTextQuery.MAX_TERMS);

        ProductTextQuery hyphenated = ProductTextQuery.parse("a-b-c-d-e-f-g-h-i-j-k-l");
        assertThat(hyphenated.ftsTerms()).hasSize(ProductTextQuery.MAX_TERMS);

        ProductTextQuery longQuery = ProductTextQuery.parse("x".repeat(500));
        assertThat(longQuery.ftsTerms()).containsExactly("x".repeat(ProductTextQuery.MAX_LENGTH));
    }

    @ParameterizedTest
    @CsvSource({"cat,1", "boot,1", "lipstik,1", "headphne,2", "playstaton,2"})
    void maxEditsGrowsWithTheWordLength(String term, int expected) {
        assertThat(ProductTextQuery.maxEdits(term)).isEqualTo(expected);
    }

    @Test
    void onlyAlphabeticTermsOfFourOrMoreLettersAreCorrectable() {
        ProductTextQuery query = ProductTextQuery.parse("tv ps5 4060 boot lipstik lipstik café 100%");

        assertThat(query.correctableTerms()).containsExactly("boot", "lipstik", "café");
    }

    @Test
    void withReplacedTermsSwapsOnlyFullTextTerms() {
        ProductTextQuery query = ProductTextQuery.parse("lipstik red 100%");

        ProductTextQuery corrected = query.withReplacedTerms(Map.of("lipstik", "lipstick"));

        assertThat(corrected.ftsTerms()).containsExactly("lipstick", "red");
        assertThat(corrected.likePatterns()).containsExactly("%100\\%%");
        assertThat(query.ftsTerms()).containsExactly("lipstik", "red");
    }

    @Test
    void oneTermGivesTheSameExpressionForEveryOperator() {
        ProductTextQuery query = ProductTextQuery.parse("Laptops");

        assertThat(query.tsQuery()).isEqualTo("laptops:*");
        assertThat(query.anyTermTsQuery()).isEqualTo("laptops:*");
        assertThat(query.phraseTsQuery()).isEqualTo("laptops:*");
    }

    @Test
    void severalTermsAreJoinedWithOrAndAsAPhrase() {
        ProductTextQuery query = ProductTextQuery.parse("noise cancelling headphones");

        assertThat(query.anyTermTsQuery()).isEqualTo("noise:* | cancelling:* | headphones:*");
        assertThat(query.phraseTsQuery()).isEqualTo("noise:* <-> cancelling:* <-> headphones:*");
    }

    @Test
    void shortTermsAreNotPrefixesInAnyOperator() {
        ProductTextQuery query = ProductTextQuery.parse("usb-c tv 4k");

        assertThat(query.anyTermTsQuery()).isEqualTo("usb:* | c | tv | 4k");
        assertThat(query.phraseTsQuery()).isEqualTo("usb:* <-> c <-> tv <-> 4k");
    }

    @Test
    void literalOnlyQueryHasNoAnyTermOrPhraseExpression() {
        ProductTextQuery query = ProductTextQuery.parse("100% _");

        assertThat(query.anyTermTsQuery()).isNull();
        assertThat(query.phraseTsQuery()).isNull();
    }

    @Test
    void literalTermsStayOutOfTheAnyTermAndPhraseExpressions() {
        ProductTextQuery query = ProductTextQuery.parse("cotton 100% shirt");

        assertThat(query.anyTermTsQuery()).isEqualTo("cotton:* | shirt:*");
        assertThat(query.phraseTsQuery()).isEqualTo("cotton:* <-> shirt:*");
    }

    @Test
    void withReplacedTermsPropagatesToTheAnyTermAndPhraseExpressions() {
        ProductTextQuery corrected = ProductTextQuery.parse("matte lipstik")
                .withReplacedTerms(Map.of("lipstik", "lipstick"));

        assertThat(corrected.tsQuery()).isEqualTo("matte:* & lipstick:*");
        assertThat(corrected.anyTermTsQuery()).isEqualTo("matte:* | lipstick:*");
        assertThat(corrected.phraseTsQuery()).isEqualTo("matte:* <-> lipstick:*");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a | b", "a <-> b", "a <2> b", "!x | (y)", "<->", "|"})
    void anyTermAndPhraseExpressionsNeverContainUserSuppliedOperators(String raw) {
        ProductTextQuery query = ProductTextQuery.parse(raw);
        if (!query.hasFtsTerms()) {
            assertThat(query.anyTermTsQuery()).isNull();
            assertThat(query.phraseTsQuery()).isNull();
            return;
        }
        for (String part : query.anyTermTsQuery().split(" \\| ")) {
            assertThat(part).matches("[\\p{L}\\p{N}]+(:\\*)?");
        }
        for (String part : query.phraseTsQuery().split(" <-> ")) {
            assertThat(part).matches("[\\p{L}\\p{N}]+(:\\*)?");
        }
    }
}
