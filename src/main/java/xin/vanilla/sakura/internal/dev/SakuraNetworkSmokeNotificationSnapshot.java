package xin.vanilla.sakura.internal.dev;

import xin.vanilla.banira.common.data.Component;

/** Canonical expected transport input, with explicit inherited language on the detached copy. */
public final class SakuraNetworkSmokeNotificationSnapshot {
    private SakuraNetworkSmokeNotificationSnapshot() { }

    public static String json(Component component, String language) {
        Component copy = component.clone();
        bindLanguage(copy, language);
        return copy.toJson().toString();
    }

    private static void bindLanguage(Component component, String inherited) {
        component.languageCodeIfEmpty(inherited);
        String language = component.languageCode().get();
        component.languageCode(language);
        for (Component child : component.getChildren()) bindLanguage(child, language);
        for (Component argument : component.getArgs()) if (argument != null) bindLanguage(argument, language);
    }
}
