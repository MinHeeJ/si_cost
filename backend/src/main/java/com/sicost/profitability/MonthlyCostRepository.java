package com.sicost.profitability;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.developers.DeveloperRepository;
import com.sicost.shared.SharedQueriesMapper;
import com.sicost.shared.SharedQueriesService;
import java.time.*;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.profitability.MonthlyCostDtos.*;

/** One consistent, uncached, company-wide source set for subsequent pure monthly calculations. */
@Repository
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class MonthlyCostRepository {
    private final MonthlyCostMapper mapper;
    private final DeveloperRepository developers;
    private final SharedQueriesService shared;
    private final SharedQueriesMapper sharedMapper;
    private final CurrentUserProvider users;
    private final Clock clock;

    public MonthlyCostRepository(MonthlyCostMapper mapper, DeveloperRepository developers,
        SharedQueriesService shared, SharedQueriesMapper sharedMapper, CurrentUserProvider users, Clock clock) {
        this.mapper = mapper; this.developers = developers; this.shared = shared;
        this.sharedMapper = sharedMapper; this.users = users; this.clock = clock;
    }

    public Sources loadYear(Integer requestedYear) {
        Instant at = clock.instant();
        String base = DatabaseMonthCodec.toDatabase(shared.getPeriodContext().cashBaseMonth());
        ProfitabilityPeriod period = period(requestedYear, base, at);
        // Reading the whole selected year retains pre-base protected evidence, but the calculation
        // period always intersects CASH_BASE_YM. Never use company PL to fabricate personal rows.
        String from = db(YearMonth.of(period.year(), 1));
        YearMonth current = YearMonth.from(at.atZone(TimeConfiguration.SEOUL));
        String to = db(period.year() == current.getYear() ? current : YearMonth.of(period.year(), 12));
        return load(period, new Range(from, to), at);
    }

    public Sources loadRange(String requestedMonth) {
        Instant at = clock.instant();
        String base = DatabaseMonthCodec.toDatabase(shared.getPeriodContext().cashBaseMonth());
        YearMonth current = YearMonth.from(at.atZone(TimeConfiguration.SEOUL));
        YearMonth selected = requestedMonth == null ? current : com.sicost.common.validation.ProjectInputPolicy.month(requestedMonth, "month");
        if (selected.isBefore(parseMonth(base)) || selected.isAfter(current)) throw invalid("month");
        var period = new ProfitabilityPeriod(selected.getYear(), base, base, db(selected),
            at.atZone(TimeConfiguration.SEOUL).toOffsetDateTime(), false);
        return load(period, new Range(base, db(selected)), at);
    }

    public Sources loadRange(YearMonth selected) {
        Instant at = clock.instant();
        String base = DatabaseMonthCodec.toDatabase(shared.getPeriodContext().cashBaseMonth());
        YearMonth start = parseMonth(base);
        YearMonth current = YearMonth.from(at.atZone(TimeConfiguration.SEOUL));
        if (selected.isBefore(start) || selected.isAfter(current))
            throw com.sicost.common.validation.ProjectInputPolicy.invalid("month");
        var period = new ProfitabilityPeriod(selected.getYear(), base, base, db(selected),
            at.atZone(TimeConfiguration.SEOUL).toOffsetDateTime(), false);
        return load(period, new Range(base, db(selected)), at);
    }

    public Sources loadMonth(int year, int month) {
        Instant at = clock.instant();
        String base = DatabaseMonthCodec.toDatabase(shared.getPeriodContext().cashBaseMonth());
        ProfitabilityPeriod annual = period(year, base, at);
        YearMonth selected;
        try { selected = YearMonth.of(year, month); }
        catch (DateTimeException e) { throw invalid("month"); }
        if (selected.isAfter(YearMonth.from(at.atZone(TimeConfiguration.SEOUL)))) throw invalid("month");
        String value = db(selected);
        var costs = mapper.storedCosts(new Range(value, value));
        boolean noData = value.compareTo(base) < 0 && costs.isEmpty();
        var period = new ProfitabilityPeriod(annual.year(), base, noData ? null : value, noData ? null : value,
            annual.calculatedAt(), noData);
        return load(period, new Range(value, value), at, costs);
    }

    private Sources load(ProfitabilityPeriod period, Range range, Instant at) {
        return load(period, range, at, mapper.storedCosts(range));
    }

    private Sources load(ProfitabilityPeriod period, Range range, Instant at, List<StoredCost> costs) {
        String actor = users.userId();
        return new Sources(period, actor, at.atZone(TimeConfiguration.SEOUL).toLocalDate(),
            List.copyOf(developers.historicalPeople()), List.copyOf(developers.historicalPrices(range)),
            List.copyOf(developers.historicalStates(range)), List.copyOf(mapper.payroll(range)),
            List.copyOf(mapper.assignments(range)), List.copyOf(mapper.projectMonths(range)),
            List.copyOf(mapper.projects()), List.copyOf(mapper.projectParts()), List.copyOf(mapper.turnkey()),
            List.copyOf(costs), List.copyOf(mapper.standards(range)), List.copyOf(mapper.parts()),
            List.copyOf(sharedMapper.monthCloses(range.fromMonth(), range.toMonth())),
            List.copyOf(shared.getCodes("ALWNC", false).items()));
    }

    /** Boundary validation shared by annual/monthly readers; no writes, rates or business defaults. */
    public static ProfitabilityPeriod period(Integer requestedYear, String baseMonth, Instant at) {
        YearMonth base = parseMonth(baseMonth);
        YearMonth current = YearMonth.from(at.atZone(TimeConfiguration.SEOUL));
        int year = requestedYear == null ? current.getYear() : requestedYear;
        if (year < base.getYear() || year > current.getYear()) throw invalid("year");
        YearMonth start = YearMonth.of(year, 1), end = year == current.getYear() ? current : YearMonth.of(year, 12);
        if (start.isBefore(base)) start = base;
        boolean empty = start.isAfter(end);
        return new ProfitabilityPeriod(year, baseMonth, empty ? null : db(start), empty ? null : db(end),
            at.atZone(TimeConfiguration.SEOUL).toOffsetDateTime(), empty);
    }

    private static String db(YearMonth month) { return DatabaseMonthCodec.toDatabase(month.toString()); }
    private static ApiException invalid(String field) {
        return new ApiException(400, "INVALID_PERIOD", "운영 시작연도부터 올해 현재월까지 조회할 수 있습니다.", field, null);
    }
}
