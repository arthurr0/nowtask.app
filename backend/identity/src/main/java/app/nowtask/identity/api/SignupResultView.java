package app.nowtask.identity.api;

import java.util.UUID;

public record SignupResultView(UserView user, SuggestedOrgView suggestOrg) {

    public record SuggestedOrgView(UUID id, String name, String slug) {
    }
}
