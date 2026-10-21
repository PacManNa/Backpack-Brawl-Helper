package ua.borlock.autotap;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

import java.util.function.Consumer;

/** Shared disclosure behavior for module cards, permissions and first-launch help. */
final class ExpandableSection {
    private final View header;
    private final View body;
    private final String label;
    private final Consumer<Boolean> onChange;
    private boolean expanded;

    private ExpandableSection(View header, View body, String label, boolean expanded,
                              Consumer<Boolean> onChange) {
        this.header = header;
        this.body = body;
        this.label = label;
        this.onChange = onChange;
        header.setClickable(true);
        header.setFocusable(true);
        header.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        header.setOnClickListener(view -> setExpanded(!this.expanded));
        header.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host,
                                                                    AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
                info.addAction(ExpandableSection.this.expanded
                        ? AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE
                        : AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND);
            }

            @Override public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (action == AccessibilityNodeInfo.ACTION_EXPAND
                        || action == AccessibilityNodeInfo.ACTION_COLLAPSE) {
                    setExpanded(action == AccessibilityNodeInfo.ACTION_EXPAND);
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
        setExpanded(expanded);
    }

    static void bind(View header, View body, String label, boolean expanded, Consumer<Boolean> onChange) {
        new ExpandableSection(header, body, label, expanded, onChange);
    }

    private void setExpanded(boolean value) {
        expanded = value;
        body.setVisibility(value ? View.VISIBLE : View.GONE);
        String state = value ? t(R.string.expanded) : t(R.string.collapsed);
        if (Build.VERSION.SDK_INT >= 30) {
            header.setContentDescription(label);
            header.setStateDescription(state);
        } else {
            header.setContentDescription(label + ". " + state);
        }
        onChange.accept(value);
    }

    private String t(int id, Object... args) {
        return AppLanguage.text(header.getContext(), id, args);
    }
}
