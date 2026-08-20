package app.nowtask.identity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import app.nowtask.identity.api.TeamView;
import app.nowtask.identity.api.UserDirectory;
import app.nowtask.identity.api.UserView;
import app.nowtask.shared.NotFoundException;

@Service
@Transactional(readOnly = true)
class UserDirectoryService implements UserDirectory {
    private final AppUserRepository users;
    private final JdbcClient jdbc;

    UserDirectoryService(AppUserRepository users, JdbcClient jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    @Override
    public List<UserView> findAll() {
        return users.findAllByOrderByPendingAscNameAsc().stream().map(UserDirectoryService::toView).toList();
    }

    @Override
    public List<UserView> findActive() {
        return findAll().stream().filter(user -> !user.pending()).toList();
    }

    @Override
    public Optional<UserView> findById(UUID id) {
        return users.findById(id).map(UserDirectoryService::toView);
    }

    @Override
    public Map<UUID, UserView> findByIds(Iterable<UUID> ids) {
        List<UUID> list = new java.util.ArrayList<>();
        ids.forEach(list::add);
        if (list.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(list).stream()
                .map(UserDirectoryService::toView)
                .collect(Collectors.toMap(UserView::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    @Override
    public List<TeamView> findTeams() {
        return jdbc.sql("SELECT id, name, headcount FROM team ORDER BY name")
                .query((rs, rowNum) -> new TeamView(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getInt("headcount")))
                .list();
    }

    @Override
    public UserView currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new NotFoundException("No signed-in user");
        }
        return users.findByEmailIgnoreCase(authentication.getName())
                .map(UserDirectoryService::toView)
                .orElseThrow(() -> NotFoundException.of("User", authentication.getName()));
    }

    static UserView toView(AppUser user) {
        return new UserView(
                user.getId(),
                user.getName(),
                user.getShortName(),
                user.getInitials(),
                user.getEmail(),
                user.getRole(),
                user.getCapacity(),
                user.isPending(),
                user.getInvitedOn());
    }
}
