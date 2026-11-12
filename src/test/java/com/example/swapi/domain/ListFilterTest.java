package com.example.swapi.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ListFilterTest {

    @ParameterizedTest(name = "[{0}] counts as absent")
    @NullSource
    @ValueSource(strings = {"", " ", "\t", "   "})
    void blankValuesAreNormalisedToAbsent(String blank) {
        ListFilter filter = new ListFilter(blank, blank);

        assertThat(filter.hasId()).isFalse();
        assertThat(filter.hasName()).isFalse();
        assertThat(filter.id()).isNull();
        assertThat(filter.name()).isNull();
    }

    @ParameterizedTest(name = "[{0}] is trimmed and kept")
    @ValueSource(strings = {"luke", " luke", "luke ", "  luke  "})
    void surroundingWhitespaceIsTrimmed(String raw) {
        ListFilter filter = new ListFilter(raw, raw);

        assertThat(filter.id()).isEqualTo("luke");
        assertThat(filter.name()).isEqualTo("luke");
        assertThat(filter.hasId()).isTrue();
        assertThat(filter.hasName()).isTrue();
    }

    @ParameterizedTest(name = "id and name are independent")
    @ValueSource(strings = {"1"})
    void idAndNameAreIndependent(String id) {
        ListFilter filter = new ListFilter(id, "  ");

        assertThat(filter.hasId()).isTrue();
        assertThat(filter.hasName()).isFalse();
    }
}
