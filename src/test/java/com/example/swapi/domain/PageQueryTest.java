package com.example.swapi.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PageQueryTest {

    @ParameterizedTest(name = "page={0} size={1} is rejected")
    @CsvSource({"0, 10", "-1, 10", "1, 0", "1, -5", "1, 101"})
    void rejectsOutOfRangeValues(int page, int size) {
        assertThatIllegalArgumentException().isThrownBy(() -> new PageQuery(page, size));
    }

    @ParameterizedTest(name = "page={0} size={1} is accepted")
    @CsvSource({"1, 1", "1, 100", "9999, 10"})
    void acceptsValuesOnAndInsideTheBoundary(int page, int size) {
        assertThat(new PageQuery(page, size)).isNotNull();
    }

    @Test
    void offsetIsZeroBasedEvenThoughPagesAreOneBased() {
        assertThat(new PageQuery(1, 10).offset()).isZero();
        assertThat(new PageQuery(3, 10).offset()).isEqualTo(20);
    }
}
