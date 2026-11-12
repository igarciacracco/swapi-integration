package com.example.swapi.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pagination arithmetic is the one piece of real logic the domain owns, so it gets the
 * table-driven treatment: first page, middle page, last partial page, past the end, and the
 * degenerate cases around an empty list.
 */
class PageResultTest {

    private static final List<String> TEN = IntStream.rangeClosed(1, 10)
            .mapToObj(i -> "item" + i)
            .toList();

    @ParameterizedTest(name = "page {0} size {1} -> {2} items starting at {3}")
    @CsvSource({
            // page, size, expectedCount, expectedFirst
            "1,  3, 3, item1",   // first page
            "2,  3, 3, item4",   // middle page
            "4,  3, 1, item10",  // last page, partially filled
            "1, 10, 10, item1",  // exactly one full page
            "1, 25, 10, item1",  // size larger than the list
    })
    void slicesTheRequestedWindow(int page, int size, int expectedCount, String expectedFirst) {
        PageResult<String> result = PageResult.slice(TEN, new PageQuery(page, size));

        assertThat(result.items()).hasSize(expectedCount).first().isEqualTo(expectedFirst);
        assertThat(result.page()).isEqualTo(page);
        assertThat(result.size()).isEqualTo(size);
        assertThat(result.totalItems()).isEqualTo(10);
    }

    @ParameterizedTest(name = "{0} items at size {1} -> {2} pages")
    @CsvSource({"10, 3, 4", "10, 10, 1", "10, 25, 1", "0, 10, 0", "1, 1, 1"})
    void reportsTotalPagesRoundedUp(int itemCount, int size, int expectedPages) {
        List<String> items = IntStream.range(0, itemCount).mapToObj(i -> "x" + i).toList();

        PageResult<String> result = PageResult.slice(items, new PageQuery(1, size));

        assertThat(result.totalPages()).isEqualTo(expectedPages);
    }

    @Test
    @DisplayName("a page past the end is empty, not an error, and still reports the real totals")
    void pagePastTheEndIsEmpty() {
        PageResult<String> result = PageResult.slice(TEN, new PageQuery(9, 10));

        assertThat(result.items()).isEmpty();
        assertThat(result.totalItems()).isEqualTo(10);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void emptyInputGivesAnEmptyPage() {
        PageResult<String> result = PageResult.slice(List.of(), new PageQuery(1, 10));

        assertThat(result.items()).isEmpty();
        assertThat(result.totalItems()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    @DisplayName("of() keeps the upstream total, which is larger than the page we were handed")
    void ofUsesUpstreamTotals() {
        PageResult<String> result = PageResult.of(List.of("a", "b"), new PageQuery(1, 2), 82);

        assertThat(result.items()).containsExactly("a", "b");
        assertThat(result.totalItems()).isEqualTo(82);
        assertThat(result.totalPages()).isEqualTo(41);
    }

    @Test
    void itemsAreDefensivelyCopied() {
        List<String> mutable = new java.util.ArrayList<>(List.of("a"));
        PageResult<String> result = new PageResult<>(mutable, 1, 10, 1, 1);

        mutable.add("b");

        assertThat(result.items()).containsExactly("a");
    }
}
