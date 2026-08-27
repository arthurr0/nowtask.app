package app.nowtask.identity.api;

import app.nowtask.shared.TaskOpenMode;
import app.nowtask.shared.TaskView;

public interface ViewPreferences {
    TaskView currentDefaultView();

    TaskView replaceDefaultView(TaskView view);

    TaskOpenMode currentTaskOpenMode();

    TaskOpenMode replaceTaskOpenMode(TaskOpenMode mode);
}
