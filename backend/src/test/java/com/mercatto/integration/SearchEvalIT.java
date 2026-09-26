package com.mercatto.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Search evaluation harness (#219): runs the reference queries of
 * {@code search-eval/queries.json} against {@code GET /api/catalog/products} on the real 500-product
 * dev seed, prints relevance and latency metrics, and writes {@code target/search-eval/report.md}
 * and {@code report.json}. The numbers recorded in {@code docs/search-recommendation-baseline.md}
 * come from this test.
 *
 * <p><b>Opt-in.</b> The {@code IT} suffix keeps it out of a plain {@code mvn test} (Surefire only
 * picks up {@code *Test}/{@code Test*}/{@code *Tests}/{@code *TestCase} by default). Run it alone,
 * because the catalog must hold exactly the seed (other integration tests create products in the
 * same shared container):
 *
 * <pre>mvn test -Dtest=SearchEvalIT [-Dsearch.eval.repetitions=20]</pre>
 *
 * <p>Runs with the {@code dev} profile so {@code DevDataSeeder} loads the seed users and the 500
 * products on startup, then loads the recommendation dataset through the public APIs
 * ({@link RecommendationDatasetLoader}) so the environment is the same one the recommendation cards
 * (#223–#225) will measure against — and so a later ranking that uses ratings/sales (#221) is
 * measured on data that has some.
 *
 * <p>Only sanity assertions (every request 200, every query executed, report written): the metrics
 * are a measurement, not a gate.
 */
@ActiveProfiles("dev")
@Tag("search-eval")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchEvalIT extends PostgresIntegrationTest {

    private static final int SEED_SIZE = 500;
    private static final int CATALOG_PAGE_SIZE = 100;
    private static final Path REPORT_DIR = Path.of("target", "search-eval");
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final int repetitions = Integer.getInteger("search.eval.repetitions", 20);

    private final Map<String, Long> idsByName = new HashMap<>();
    private final Map<Long, String> namesById = new HashMap<>();
    private final Map<String, Set<Long>> idsByCategory = new HashMap<>();
    private RecommendationDatasetLoader.LoadedDataset loaded;

    @BeforeAll
    void loadCatalogAndDataset() {
        long total = -1;
        for (int page = 0; total < 0 || (long) page * CATALOG_PAGE_SIZE < total; page++) {
            Map<String, Object> body = get(URI.create(rest.getRootUri()
                    + "/api/catalog/products?page=" + page + "&size=" + CATALOG_PAGE_SIZE));
            total = ((Number) body.get("totalElements")).longValue();
            assertThat(total)
                    .as("the catalog must hold exactly the %d seed products; run this test in isolation: "
                            + "mvn test -Dtest=SearchEvalIT", SEED_SIZE)
                    .isEqualTo(SEED_SIZE);
            for (Map<String, Object> product : content(body)) {
                Long id = asLong(product.get("id"));
                String name = (String) product.get("name");
                idsByName.put(name, id);
                namesById.put(id, name);
                idsByCategory.computeIfAbsent((String) product.get("category"), c -> new HashSet<>()).add(id);
            }
        }
        assertThat(idsByName).as("seed product names are unique").hasSize(SEED_SIZE);

        SearchEvalFixtures.Dataset dataset = SearchEvalFixtures.dataset();
        loaded = new RecommendationDatasetLoader(this).load(dataset, idsByName);
        assertThat(loaded.buyers()).hasSize(dataset.buyers().size());
        assertThat(loaded.orderIds()).hasSize(dataset.orders().size());
        assertThat(loaded.reviews()).isEqualTo(dataset.reviews().size());
        assertThat(loaded.lists()).isEqualTo(dataset.lists().size());
        assertThat(loaded.listItems()).isEqualTo(dataset.lists().stream().mapToInt(l -> l.products().size()).sum());
    }

    @Test
    void evaluatesReferenceQueries() throws IOException {
        SearchEvalFixtures.QuerySet set = SearchEvalFixtures.queries();
        int k = set.pageSize();

        // Relevance pass (also the latency warm-up, which is discarded).
        List<QueryResult> results = new ArrayList<>();
        for (SearchEvalFixtures.Query query : set.queries()) {
            Map<String, Object> body = get(searchUri(query.query(), k));
            List<Long> ranked = content(body).stream().map(p -> asLong(p.get("id"))).toList();
            long total = ((Number) body.get("totalElements")).longValue();
            results.add(new QueryResult(query, relevantIds(query), ranked, total));
        }

        // Latency pass: `repetitions` rounds over every query, timed around the HTTP call.
        Map<String, List<Long>> micros = new LinkedHashMap<>();
        set.queries().forEach(query -> micros.put(query.id(), new ArrayList<>()));
        for (int round = 0; round < repetitions; round++) {
            for (SearchEvalFixtures.Query query : set.queries()) {
                URI uri = searchUri(query.query(), k);
                long start = System.nanoTime();
                ResponseEntity<String> response = rest.exchange(uri, HttpMethod.GET, HttpEntity.EMPTY, String.class);
                long elapsed = System.nanoTime() - start;
                assertThat(response.getStatusCode()).as(query.id()).isEqualTo(HttpStatus.OK);
                micros.get(query.id()).add(elapsed / 1_000);
            }
        }

        Report report = new Report(k, results, micros);
        System.out.println(report.table());
        Files.createDirectories(REPORT_DIR);
        Files.writeString(REPORT_DIR.resolve("report.md"), report.markdown());
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(REPORT_DIR.resolve("report.json").toFile(), report.json());

        assertThat(results).hasSize(set.queries().size());
        assertThat(micros.values()).allSatisfy(sample -> assertThat(sample).hasSize(repetitions));
        assertThat(REPORT_DIR.resolve("report.md")).exists();
        assertThat(REPORT_DIR.resolve("report.json")).exists();
    }

    private Set<Long> relevantIds(SearchEvalFixtures.Query query) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String name : query.relevant()) {
            Long id = idsByName.get(name);
            assertThat(id).as(query.id() + ": unknown product " + name).isNotNull();
            ids.add(id);
        }
        for (String category : query.relevantCategories()) {
            Set<Long> inCategory = idsByCategory.get(category);
            assertThat(inCategory).as(query.id() + ": unknown category " + category).isNotNull();
            ids.addAll(inCategory);
        }
        return ids;
    }

    /** The same request the storefront's search page sends: first page of {@code size} items. */
    private URI searchUri(String query, int size) {
        return URI.create(rest.getRootUri() + "/api/catalog/products?query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&page=0&size=" + size);
    }

    private Map<String, Object> get(URI uri) {
        ResponseEntity<Map<String, Object>> response = rest.exchange(uri, HttpMethod.GET, HttpEntity.EMPTY, MAP);
        assertThat(response.getStatusCode()).as(uri.toString()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> content(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("content");
    }

    private record QueryResult(SearchEvalFixtures.Query query, Set<Long> relevant, List<Long> ranked, long total) {

        boolean judged() {
            return !relevant.isEmpty();
        }
    }

    /** Aggregates plus the three renderings (console table, Markdown, JSON). */
    private final class Report {

        private final int k;
        private final List<QueryResult> results;
        private final Map<String, List<Long>> micros;
        private final List<Long> allMicros = new ArrayList<>();

        Report(int k, List<QueryResult> results, Map<String, List<Long>> micros) {
            this.k = k;
            this.results = results;
            this.micros = micros;
            micros.values().forEach(allMicros::addAll);
        }

        private double precision(QueryResult r) {
            return SearchMetrics.precisionAtK(r.ranked(), r.relevant(), k);
        }

        private double recall(QueryResult r) {
            return SearchMetrics.recallAtK(r.ranked(), r.relevant(), k);
        }

        private double rr(QueryResult r) {
            return SearchMetrics.reciprocalRank(r.ranked(), r.relevant(), k);
        }

        private long p95(String queryId) {
            return SearchMetrics.percentile(micros.get(queryId), 95);
        }

        /** Aggregate metrics over a subset of the queries (all of them, or one type). */
        private Map<String, Object> aggregate(List<QueryResult> subset) {
            List<QueryResult> judged = subset.stream().filter(QueryResult::judged).toList();
            List<QueryResult> expectEmpty = subset.stream().filter(r -> !r.judged()).toList();
            List<Long> sample = new ArrayList<>();
            subset.forEach(r -> sample.addAll(micros.get(r.query().id())));

            Map<String, Object> agg = new LinkedHashMap<>();
            agg.put("queries", subset.size());
            agg.put("judgedQueries", judged.size());
            agg.put("expectEmptyQueries", expectEmpty.size());
            if (!judged.isEmpty()) {
                agg.put("precisionAt" + k, round(SearchMetrics.mean(judged.stream().map(this::precision).toList())));
                agg.put("recallAt" + k, round(SearchMetrics.mean(judged.stream().map(this::recall).toList())));
                agg.put("mrrAt" + k, round(SearchMetrics.mean(judged.stream().map(this::rr).toList())));
                agg.put("zeroResultRate", round(SearchMetrics.zeroResultRate(
                        judged.stream().map(QueryResult::total).toList())));
            }
            if (!expectEmpty.isEmpty()) {
                long falsePositives = expectEmpty.stream().filter(r -> r.total() > 0).count();
                agg.put("falsePositiveRate", round((double) falsePositives / expectEmpty.size()));
            }
            agg.put("latencyP50Ms", ms(SearchMetrics.percentile(sample, 50)));
            agg.put("latencyP95Ms", ms(SearchMetrics.percentile(sample, 95)));
            agg.put("latencyMaxMs", ms(SearchMetrics.percentile(sample, 100)));
            return agg;
        }

        private Map<String, Map<String, Object>> byType() {
            Map<String, Map<String, Object>> byType = new LinkedHashMap<>();
            for (String type : SearchEvalFixtures.QUERY_TYPES) {
                List<QueryResult> subset = results.stream().filter(r -> r.query().type().equals(type)).toList();
                if (!subset.isEmpty()) {
                    byType.put(type, aggregate(subset));
                }
            }
            return byType;
        }

        String table() {
            StringBuilder out = new StringBuilder();
            out.append("\n=== Search evaluation (#219): ").append(results.size()).append(" queries, k=").append(k)
                    .append(", ").append(repetitions).append(" timed rounds ===\n");
            out.append(String.format(Locale.ROOT, "%-36s %-16s %-28s %5s %6s %5s %6s %6s %6s %8s%n",
                    "id", "type", "query", "|rel|", "total", "hits", "P@" + k, "R@" + k, "RR", "p95 ms"));
            for (QueryResult r : results) {
                out.append(String.format(Locale.ROOT, "%-36s %-16s %-28s %5d %6d %5d %6s %6s %6s %8.2f%n",
                        r.query().id(), r.query().type(), truncate("\"" + r.query().query() + "\"", 28),
                        r.relevant().size(), r.total(), SearchMetrics.hitsAtK(r.ranked(), r.relevant(), k),
                        r.judged() ? fmt(precision(r)) : "-", r.judged() ? fmt(recall(r)) : "-",
                        r.judged() ? fmt(rr(r)) : "-", ms(p95(r.query().id()))));
            }
            out.append("\n--- By type ---\n");
            out.append(String.format(Locale.ROOT, "%-16s %4s %6s %6s %6s %6s %6s %8s%n",
                    "type", "n", "P@" + k, "R@" + k, "MRR", "zero%", "FP%", "p95 ms"));
            byType().forEach((type, agg) -> out.append(typeRow(type, agg)));
            out.append("\n--- Overall ---\n");
            aggregate(results).forEach((key, value) -> out.append(String.format(Locale.ROOT, "%-20s %s%n", key, value)));
            return out.toString();
        }

        private String typeRow(String type, Map<String, Object> agg) {
            return String.format(Locale.ROOT, "%-16s %4s %6s %6s %6s %6s %6s %8s%n", type, agg.get("queries"),
                    orDash(agg.get("precisionAt" + k)), orDash(agg.get("recallAt" + k)), orDash(agg.get("mrrAt" + k)),
                    orDash(agg.get("zeroResultRate")), orDash(agg.get("falsePositiveRate")), agg.get("latencyP95Ms"));
        }

        String markdown() {
            Map<String, Object> overall = aggregate(results);
            StringBuilder md = new StringBuilder();
            md.append("# Search evaluation report\n\n");
            md.append("Generated by `SearchEvalIT` (#219) at ").append(Instant.now()).append(". ")
                    .append(results.size()).append(" queries, k=").append(k).append(", ")
                    .append(repetitions).append(" timed rounds after 1 warm-up round. ")
                    .append(environment()).append("\n\n");
            md.append("## Overall\n\n| metric | value |\n|---|---|\n");
            overall.forEach((key, value) -> md.append("| ").append(key).append(" | ").append(value).append(" |\n"));
            md.append("\n## By type\n\n| type | n | P@").append(k).append(" | R@").append(k).append(" | MRR@").append(k)
                    .append(" | zero-result | false positive | p95 ms |\n|---|---|---|---|---|---|---|---|\n");
            byType().forEach((type, agg) -> md.append("| ").append(type).append(" | ").append(agg.get("queries"))
                    .append(" | ").append(orDash(agg.get("precisionAt" + k)))
                    .append(" | ").append(orDash(agg.get("recallAt" + k)))
                    .append(" | ").append(orDash(agg.get("mrrAt" + k)))
                    .append(" | ").append(orDash(agg.get("zeroResultRate")))
                    .append(" | ").append(orDash(agg.get("falsePositiveRate")))
                    .append(" | ").append(agg.get("latencyP95Ms")).append(" |\n"));
            md.append("\n## Per query\n\n| id | type | query | relevant | total | hits@").append(k).append(" | P@")
                    .append(k).append(" | R@").append(k).append(" | RR | p95 ms |\n|---|---|---|---|---|---|---|---|---|---|\n");
            for (QueryResult r : results) {
                md.append("| ").append(r.query().id()).append(" | ").append(r.query().type())
                        .append(" | `").append(r.query().query()).append("` | ").append(r.relevant().size())
                        .append(" | ").append(r.total())
                        .append(" | ").append(SearchMetrics.hitsAtK(r.ranked(), r.relevant(), k))
                        .append(" | ").append(r.judged() ? fmt(precision(r)) : "-")
                        .append(" | ").append(r.judged() ? fmt(recall(r)) : "-")
                        .append(" | ").append(r.judged() ? fmt(rr(r)) : "-")
                        .append(" | ").append(ms(p95(r.query().id()))).append(" |\n");
            }
            return md.toString();
        }

        Map<String, Object> json() {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("generatedAt", Instant.now().toString());
            root.put("k", k);
            root.put("timedRounds", repetitions);
            root.put("environment", environment());
            root.put("overall", aggregate(results));
            root.put("byType", byType());
            List<Map<String, Object>> perQuery = new ArrayList<>();
            for (QueryResult r : results) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", r.query().id());
                row.put("type", r.query().type());
                row.put("query", r.query().query());
                row.put("relevantCount", r.relevant().size());
                row.put("totalResults", r.total());
                row.put("hitsAtK", SearchMetrics.hitsAtK(r.ranked(), r.relevant(), k));
                if (r.judged()) {
                    row.put("precisionAtK", round(precision(r)));
                    row.put("recallAtK", round(recall(r)));
                    row.put("reciprocalRank", round(rr(r)));
                }
                row.put("latencyP50Ms", ms(SearchMetrics.percentile(micros.get(r.query().id()), 50)));
                row.put("latencyP95Ms", ms(p95(r.query().id())));
                List<Map<String, Object>> firstPage = new ArrayList<>();
                for (Long id : r.ranked()) {
                    firstPage.add(Map.of("name", namesById.getOrDefault(id, "?"), "relevant", r.relevant().contains(id)));
                }
                row.put("firstPage", firstPage);
                perQuery.add(row);
            }
            root.put("queries", perQuery);
            return root;
        }

        private String environment() {
            return "Java " + System.getProperty("java.version") + ", " + System.getProperty("os.name") + " "
                    + System.getProperty("os.arch") + ", " + Runtime.getRuntime().availableProcessors()
                    + " CPUs; PostgreSQL 16 (Testcontainers) and the app in the same machine, client in-process.";
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static double ms(long micros) {
        return Math.round(micros / 10.0) / 100.0;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String orDash(Object value) {
        return value == null ? "-" : value.toString();
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
