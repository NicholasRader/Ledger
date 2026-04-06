package com.ledger.domain.entity;

import com.ledger.domain.entity.RecurringPayment.Frequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RecurringPayment.Frequency Unit Tests")
class RecurringPaymentFrequencyTest {

    private static Stream<Arguments> frequencyAdvanceCases() {
        LocalDate base = LocalDate.of(2026, 3, 15);
        return Stream.of(
            Arguments.of(Frequency.DAILY,   base, base.plusDays(1)),
            Arguments.of(Frequency.WEEKLY,  base, base.plusWeeks(1)),
            Arguments.of(Frequency.MONTHLY, base, base.plusMonths(1)),
            Arguments.of(Frequency.YEARLY,  base, base.plusYears(1))
        );
    }

    @ParameterizedTest(name = "{0} frequency advances date correctly")
    @MethodSource("frequencyAdvanceCases")
    @DisplayName("nextOccurrence() should advance date correctly for each frequency")
    void nextOccurrence_advancesCorrectly(Frequency frequency, LocalDate from, LocalDate expected) {
        assertThat(frequency.nextOccurrence(from)).isEqualTo(expected);
    }

    @Test
    @DisplayName("MONTHLY frequency handles month-end dates correctly")
    void nextOccurrence_monthly_handlesMonthEnd() {
        // Jan 31 + 1 month = Feb 28 (spring handles this)
        LocalDate jan31 = LocalDate.of(2026, 1, 31);
        LocalDate result = Frequency.MONTHLY.nextOccurrence(jan31);
        assertThat(result).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("YEARLY frequency handles leap year correctly")
    void nextOccurrence_yearly_handlesLeapYear() {
        // Feb 29 2024 (leap year) + 1 year = Feb 28 2025
        LocalDate leapDay = LocalDate.of(2024, 2, 29);
        LocalDate result = Frequency.YEARLY.nextOccurrence(leapDay);
        assertThat(result).isEqualTo(LocalDate.of(2025, 2, 28));
    }

    @Test
    @DisplayName("DAILY frequency advances by exactly one day")
    void nextOccurrence_daily_exactlyOneDay() {
        LocalDate today = LocalDate.of(2026, 3, 31); // end of month
        LocalDate result = Frequency.DAILY.nextOccurrence(today);
        assertThat(result).isEqualTo(LocalDate.of(2026, 4, 1));
    }
}
