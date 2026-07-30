package xin.vanilla.sakura.screen;

import net.minecraft.client.gui.screen.Screen;
import xin.vanilla.banira.client.gui.InputFormScreen;
import xin.vanilla.banira.client.gui.component.Text;
import xin.vanilla.banira.client.gui.component.TextList;
import xin.vanilla.sakura.config.StringList;
import xin.vanilla.sakura.text.SakuraComponent;
import xin.vanilla.sakura.util.StringUtils;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Sakura 奖励配置的多字段输入适配层，布局、输入和错误展示均由 Banira 表单负责。
 */
public final class StringInputScreen extends InputFormScreen {

    private interface SubmissionHandler {
        StringList apply(StringList values);
    }

    public StringInputScreen(Screen parent, Text title, Text message, String validator,
                             Consumer<StringList> callback) {
        this(parent, new TextList(title), new TextList(message), new StringList(validator),
                new StringList(""), (SubmissionHandler) values -> {
                    callback.accept(values);
                    return null;
                }, null);
    }

    public StringInputScreen(Screen parent, TextList titles, TextList messages, StringList validators,
                             Consumer<StringList> callback) {
        this(parent, titles, messages, validators, new StringList(""), (SubmissionHandler) values -> {
            callback.accept(values);
            return null;
        }, null);
    }

    public StringInputScreen(Screen parent, Text title, Text message, String validator, String defaultValue,
                             Consumer<StringList> callback) {
        this(parent, new TextList(title), new TextList(message), new StringList(validator),
                new StringList(defaultValue), (SubmissionHandler) values -> {
                    callback.accept(values);
                    return null;
                }, null);
    }

    public StringInputScreen(Screen parent, TextList titles, TextList messages, StringList validators,
                             StringList defaultValues, Consumer<StringList> callback) {
        this(parent, titles, messages, validators, defaultValues, (SubmissionHandler) values -> {
            callback.accept(values);
            return null;
        }, null);
    }

    public StringInputScreen(Screen parent, Text title, Text message, String validator, String defaultValue,
                             Consumer<StringList> callback, Supplier<Boolean> invisible) {
        this(parent, new TextList(title), new TextList(message), new StringList(validator),
                new StringList(defaultValue), (SubmissionHandler) values -> {
                    callback.accept(values);
                    return null;
                }, invisible);
    }

    public StringInputScreen(Screen parent, TextList titles, TextList messages, StringList validators,
                             StringList defaultValues, Consumer<StringList> callback, Supplier<Boolean> invisible) {
        this(parent, titles, messages, validators, defaultValues, (SubmissionHandler) values -> {
            callback.accept(values);
            return null;
        }, invisible);
    }

    public StringInputScreen(Screen parent, Text title, Text message, String validator,
                             Function<StringList, StringList> callback) {
        this(parent, new TextList(title), new TextList(message), new StringList(validator),
                new StringList(""), (SubmissionHandler) callback::apply, null);
    }

    public StringInputScreen(Screen parent, TextList titles, TextList messages, StringList validators,
                             Function<StringList, StringList> callback) {
        this(parent, titles, messages, validators, new StringList(""),
                (SubmissionHandler) callback::apply, null);
    }

    public StringInputScreen(Screen parent, Text title, Text message, String validator, String defaultValue,
                             Function<StringList, StringList> callback) {
        this(parent, new TextList(title), new TextList(message), new StringList(validator),
                new StringList(defaultValue), (SubmissionHandler) callback::apply, null);
    }

    public StringInputScreen(Screen parent, TextList titles, TextList messages, StringList validators,
                             StringList defaultValues, Function<StringList, StringList> callback) {
        this(parent, titles, messages, validators, defaultValues,
                (SubmissionHandler) callback::apply, null);
    }

    public StringInputScreen(Screen parent, Text title, Text message, String validator, String defaultValue,
                             Function<StringList, StringList> callback, Supplier<Boolean> invisible) {
        this(parent, new TextList(title), new TextList(message), new StringList(validator),
                new StringList(defaultValue), (SubmissionHandler) callback::apply, invisible);
    }

    public StringInputScreen(Screen parent, TextList titles, TextList messages, StringList validators,
                             StringList defaultValues, Function<StringList, StringList> callback,
                             Supplier<Boolean> invisible) {
        this(parent, titles, messages, validators, defaultValues,
                (SubmissionHandler) callback::apply, invisible);
    }

    private StringInputScreen(Screen parent, TextList titles, TextList messages, StringList validators,
                              StringList defaultValues, SubmissionHandler callback, Supplier<Boolean> invisible) {
        super(createArgs(parent, titles, messages, validators, defaultValues, callback, invisible));
    }

    private static Args createArgs(Screen parent, TextList titles, TextList messages, StringList validators,
                                   StringList defaultValues, SubmissionHandler callback,
                                   Supplier<Boolean> invisible) {
        if (titles == null || titles.isEmpty()) {
            throw new IllegalArgumentException("Input form requires at least one title");
        }

        Args args = new Args()
                .setParentScreen(parent)
                .setTitle(titles.get(0))
                .setCallback(results -> {
                    StringList values = new StringList();
                    for (int i = 0; i < titles.size(); i++) {
                        values.add(results.value(i));
                    }
                    StringList errors = callback.apply(values);
                    if (errors != null) {
                        results.runningResult(errors.stream()
                                .filter(StringUtils::isNotNullOrEmpty)
                                .collect(Collectors.joining("\n")));
                    }
                });
        if (invisible != null) {
            args.setInvisible(invisible);
        }

        for (int i = 0; i < titles.size(); i++) {
            final int index = i;
            final String regex = cyclic(validators, index, "");
            InputFormScreen.Widget widget = new InputFormScreen.Widget()
                    .name("field_" + index)
                    .title(titles.get(index))
                    .hint(cyclic(messages, index, Text.empty()))
                    .defaultValue(cyclic(defaultValues, index, ""))
                    .validator(results -> {
                        String value = results.value(index);
                        if (StringUtils.isNotNullOrEmpty(regex)
                                && value != null
                                && !value.matches(regex)) {
                            return SakuraComponent.get().translateClient("tips", "input_format_invalid");
                        }
                        return "";
                    });
            args.addWidget(widget);
        }
        return args;
    }

    private static String cyclic(StringList values, int index, String fallback) {
        return values == null || values.isEmpty() ? fallback : values.get(index);
    }

    private static Text cyclic(TextList values, int index, Text fallback) {
        return values == null || values.isEmpty() ? fallback : values.get(index);
    }
}
