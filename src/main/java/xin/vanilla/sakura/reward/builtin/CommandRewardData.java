package xin.vanilla.sakura.reward.builtin;

/**
 * Optional presentation is stored alongside the original command field.
 */
public final class CommandRewardData {
    private final String command;
    private final String displayName;
    private final String displayIcon;

    public CommandRewardData(String command, String displayName, String displayIcon) {
        this.command = command == null ? "" : command.trim();
        this.displayName = displayName == null ? "" : displayName.trim();
        this.displayIcon = displayIcon == null ? "" : displayIcon.trim();
    }

    public String command() {
        return command;
    }

    public String displayName() {
        return displayName;
    }

    public String displayIcon() {
        return displayIcon;
    }
}
