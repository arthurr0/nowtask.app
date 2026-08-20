package app.nowtask.identity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.NavItemCode;
import app.nowtask.identity.api.NavItemView;
import app.nowtask.identity.api.NavPreferences;
import app.nowtask.identity.api.UserDirectory;

@Service
@Transactional
class NavPreferenceService implements NavPreferences {

    private final JdbcClient jdbc;
    private final UserDirectory directory;

    NavPreferenceService(JdbcClient jdbc, UserDirectory directory) {
        this.jdbc = jdbc;
        this.directory = directory;
    }

    private record StoredItem(String code, boolean hidden) {
    }

    @Override
    @Transactional(readOnly = true)
    public List<NavItemView> currentNavigation() {
        return navigationOf(directory.currentUser().id());
    }

    @Override
    public List<NavItemView> replaceNavigation(List<NavItemView> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("A navigation item list is required");
        }

        UUID userId = directory.currentUser().id();
        List<NavItemView> normalized = normalize(items);

        jdbc.sql("DELETE FROM user_nav_item WHERE user_id = ?").param(userId).update();

        for (int position = 0; position < normalized.size(); position++) {
            NavItemView item = normalized.get(position);
            jdbc.sql("INSERT INTO user_nav_item (user_id, code, position, hidden) VALUES (?, ?, ?, ?)")
                    .params(userId, item.code().code(), position, item.hidden())
                    .update();
        }

        return normalized;
    }

    private List<NavItemView> navigationOf(UUID userId) {
        List<StoredItem> stored = jdbc
                .sql("SELECT code, hidden FROM user_nav_item WHERE user_id = ? ORDER BY position")
                .param(userId)
                .query((rs, rowNum) -> new StoredItem(rs.getString("code"), rs.getBoolean("hidden")))
                .list();

        Map<NavItemCode, Boolean> byCode = new LinkedHashMap<>();
        for (StoredItem item : stored) {
            known(item.code()).ifPresent(code -> byCode.putIfAbsent(code, item.hidden()));
        }

        return fillDefaults(byCode);
    }

    private List<NavItemView> normalize(List<NavItemView> items) {
        Map<NavItemCode, Boolean> byCode = new LinkedHashMap<>();

        for (NavItemView item : items) {
            if (item == null || item.code() == null) {
                throw new IllegalArgumentException("A navigation item requires a code");
            }
            byCode.putIfAbsent(item.code(), item.hidden());
        }

        return fillDefaults(byCode);
    }

    private List<NavItemView> fillDefaults(Map<NavItemCode, Boolean> byCode) {
        for (NavItemCode code : NavItemCode.defaultOrder()) {
            byCode.putIfAbsent(code, false);
        }

        List<NavItemView> items = new ArrayList<>(byCode.size());
        byCode.forEach((code, hidden) -> items.add(new NavItemView(code, hidden)));
        return List.copyOf(items);
    }

    private Optional<NavItemCode> known(String code) {
        try {
            return Optional.of(NavItemCode.of(code));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
