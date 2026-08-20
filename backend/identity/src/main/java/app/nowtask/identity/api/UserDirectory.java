package app.nowtask.identity.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface UserDirectory {
    List<UserView> findAll();

    List<UserView> findActive();

    Optional<UserView> findById(UUID id);

    Map<UUID, UserView> findByIds(Iterable<UUID> ids);

    List<TeamView> findTeams();

    UserView currentUser();
}
