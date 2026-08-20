package app.nowtask.identity.api;

import java.util.List;

public interface NavPreferences {
    List<NavItemView> currentNavigation();

    List<NavItemView> replaceNavigation(List<NavItemView> items);
}
