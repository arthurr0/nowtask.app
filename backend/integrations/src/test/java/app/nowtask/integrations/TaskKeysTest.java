package app.nowtask.integrations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskKeysTest {

    @Test
    void readsKeysFromBranchesAndMessages() {
        assertThat(TaskKeys.find("feature/NOW-12-add-webhooks")).containsExactly("NOW-12");
        assertThat(TaskKeys.find("fix: repair the importer (NOW-3)")).containsExactly("NOW-3");
        assertThat(TaskKeys.find("NOW-1 and NOW-2 in one branch")).containsExactly("NOW-1", "NOW-2");
    }

    @Test
    void treatsLowercaseKeysAsKeys() {
        assertThat(TaskKeys.find("now-7 done")).containsExactly("NOW-7");
    }

    @Test
    void keepsEveryKeyOnlyOnce() {
        assertThat(TaskKeys.find("NOW-5", "NOW-5 again", "now-5")).containsExactly("NOW-5");
    }

    @Test
    void ignoresTextThatOnlyLooksLikeAKey() {
        assertThat(TaskKeys.find("UTF-8 encoding")).containsExactly("UTF-8");
        assertThat(TaskKeys.find("no keys here")).isEmpty();
        assertThat(TaskKeys.find("")).isEmpty();
        assertThat(TaskKeys.find((String) null)).isEmpty();
    }

    @Test
    void skipsBlankSources() {
        assertThat(TaskKeys.find(null, "NOW-9", "")).containsExactly("NOW-9");
    }
}
