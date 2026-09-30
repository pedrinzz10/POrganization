package com.porganization.finance.goals;

import com.porganization.common.NotFoundException;
import com.porganization.finance.accounts.AccountRepository;
import com.porganization.finance.goals.GoalDtos.ContributionRequest;
import com.porganization.finance.goals.GoalDtos.ContributionResponse;
import com.porganization.finance.goals.GoalDtos.GoalRequest;
import com.porganization.finance.goals.GoalDtos.GoalResponse;
import com.porganization.settings.UserSettingsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final SavingsGoalRepository goals;
    private final GoalContributionRepository contributions;
    private final AccountRepository accounts;
    private final UserSettingsService userSettings;
    private final Clock clock;

    public GoalService(SavingsGoalRepository goals, GoalContributionRepository contributions, AccountRepository accounts,
            UserSettingsService userSettings, Clock clock) {
        this.goals = goals;
        this.contributions = contributions;
        this.accounts = accounts;
        this.userSettings = userSettings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> list(UUID userId) {
        Map<UUID, BigDecimal> saved = contributions.totalsByGoal(userId).stream()
                .collect(Collectors.toMap(GoalTotal::goalId, GoalTotal::total));
        LocalDate today = today(userId);
        return goals.findByUserIdOrderByArchivedAscNameAsc(userId).stream()
                .map(g -> toResponse(g, saved.getOrDefault(g.getId(), ZERO), today))
                .toList();
    }

    @Transactional(readOnly = true)
    public GoalResponse get(UUID userId, UUID id) {
        return toResponse(userId, find(userId, id));
    }

    @Transactional
    public GoalResponse create(UUID userId, GoalRequest request) {
        SavingsGoal goal = new SavingsGoal(userId);
        apply(userId, goal, request);
        return toResponse(userId, goals.save(goal));
    }

    @Transactional
    public GoalResponse update(UUID userId, UUID id, GoalRequest request) {
        SavingsGoal goal = find(userId, id);
        apply(userId, goal, request);
        return toResponse(userId, goal);
    }

    /** Exclui a meta com os aportes. */
    @Transactional
    public void delete(UUID userId, UUID id) {
        goals.delete(find(userId, id));
    }

    @Transactional(readOnly = true)
    public List<ContributionResponse> contributions(UUID userId, UUID goalId) {
        find(userId, goalId);
        return contributions.findByUserIdAndGoalIdOrderByDateDescCreatedAtDesc(userId, goalId).stream()
                .map(GoalService::toResponse).toList();
    }

    @Transactional
    public ContributionResponse contribute(UUID userId, UUID goalId, ContributionRequest request) {
        find(userId, goalId);
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        return toResponse(contributions.save(new GoalContribution(userId, goalId, request.amount(), request.date(), note)));
    }

    @Transactional
    public void deleteContribution(UUID userId, UUID goalId, UUID contributionId) {
        contributions.delete(contributions.findByIdAndUserIdAndGoalId(contributionId, userId, goalId)
                .orElseThrow(() -> new NotFoundException("Aporte não encontrado")));
    }

    /** Percentual guardado, truncado em 2 casas. */
    static BigDecimal progress(BigDecimal target, BigDecimal saved) {
        return saved.multiply(HUNDRED).divide(target, 2, RoundingMode.DOWN);
    }

    /**
     * Quanto guardar por mês até o prazo, arredondado para cima no centavo. Os meses contam do mês
     * atual até o do prazo (outubro → janeiro = 3); prazo neste mês = 1 mês; prazo vencido = tudo o que falta.
     */
    static BigDecimal monthlyNeeded(BigDecimal remaining, LocalDate today, LocalDate targetDate) {
        if (targetDate == null) {
            return null;
        }
        if (remaining.signum() <= 0) {
            return ZERO;
        }
        if (targetDate.isBefore(today)) {
            return remaining;
        }
        long months = Math.max(1, ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(targetDate)));
        return remaining.divide(BigDecimal.valueOf(months), 2, RoundingMode.CEILING);
    }

    private void apply(UUID userId, SavingsGoal goal, GoalRequest request) {
        if (request.accountId() != null) {
            accounts.findByIdAndUserId(request.accountId(), userId).orElseThrow(() -> new NotFoundException("Conta não encontrada"));
        }
        goal.update(request.name().trim(), request.targetAmount(), request.targetDate(), request.accountId(),
                Boolean.TRUE.equals(request.archived()));
    }

    private SavingsGoal find(UUID userId, UUID id) {
        return goals.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Meta não encontrada"));
    }

    private GoalResponse toResponse(UUID userId, SavingsGoal goal) {
        BigDecimal saved = contributions.totalsByGoal(userId).stream().filter(t -> t.goalId().equals(goal.getId()))
                .map(GoalTotal::total).findFirst().orElse(ZERO);
        return toResponse(goal, saved, today(userId));
    }

    private static GoalResponse toResponse(SavingsGoal g, BigDecimal saved, LocalDate today) {
        BigDecimal remaining = g.getTargetAmount().subtract(saved).max(ZERO);
        return new GoalResponse(g.getId(), g.getName(), g.getTargetAmount(), g.getTargetDate(), g.getAccountId(), g.isArchived(),
                saved, remaining, progress(g.getTargetAmount(), saved), monthlyNeeded(remaining, today, g.getTargetDate()),
                saved.compareTo(g.getTargetAmount()) >= 0);
    }

    private static ContributionResponse toResponse(GoalContribution c) {
        return new ContributionResponse(c.getId(), c.getGoalId(), c.getAmount(), c.getDate(), c.getNote());
    }

    private LocalDate today(UUID userId) {
        return LocalDate.ofInstant(clock.instant(), userSettings.zoneOf(userId));
    }
}
