package app.nowtask.identity.api;

import java.util.List;

public record BulkInviteResultView(List<InviteView> sent, List<FailedInvite> failed) {

    public record FailedInvite(String email, String code, String messageKey) {
    }
}
