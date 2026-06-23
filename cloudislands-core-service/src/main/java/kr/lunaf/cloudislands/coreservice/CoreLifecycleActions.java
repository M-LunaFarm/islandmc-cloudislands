package kr.lunaf.cloudislands.coreservice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CoreLifecycleActions {
    private CoreLifecycleActions() {
    }

    static void start(List<CoreLifecycleAction> actions) {
        for (CoreLifecycleAction action : actions) {
            action.start();
        }
    }

    static void stop(List<CoreLifecycleAction> actions) {
        List<CoreLifecycleAction> reverse = new ArrayList<>(actions);
        Collections.reverse(reverse);
        for (CoreLifecycleAction action : reverse) {
            action.stop();
        }
    }

    static List<String> startOrder(List<CoreLifecycleAction> actions) {
        return actions.stream().map(CoreLifecycleAction::name).toList();
    }

    static List<String> stopOrder(List<CoreLifecycleAction> actions) {
        List<String> names = new ArrayList<>(startOrder(actions));
        Collections.reverse(names);
        return List.copyOf(names);
    }
}
