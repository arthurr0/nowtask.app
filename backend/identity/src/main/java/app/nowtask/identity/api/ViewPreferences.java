package app.nowtask.identity.api;

import app.nowtask.shared.TaskView;

public interface ViewPreferences {
    TaskView currentDefaultView();

    TaskView replaceDefaultView(TaskView view);
}
