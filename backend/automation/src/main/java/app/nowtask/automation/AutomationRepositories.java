package app.nowtask.automation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {
    List<AutomationRule> findAllByOrderByPositionAsc();
}

interface AutomationRunRepository extends JpaRepository<AutomationRun, UUID> {
    List<AutomationRun> findTop20ByRuleIdOrderByCreatedAtDesc(UUID ruleId);

    List<AutomationRun> findTop20ByOrderByCreatedAtDesc();

    List<AutomationRun> findTop50ByTaskKeyOrderByCreatedAtDesc(String taskKey);
}
