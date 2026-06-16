package site.muyin.portfolio.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * 项目发布状态。
 *
 * @author Lywq
 * @since 1.0.0
 */
@Getter
public enum ProjectStatus {

    DRAFT("draft", "草稿"),
    PUBLISHED("published", "已发布"),
    ARCHIVED("archived", "已归档");

    private static final Map<String, ProjectStatus> VALUE_MAP =
        Arrays.stream(values()).collect(Collectors.toMap(ProjectStatus::getValue, Function.identity()));

    private final String value;
    private final String text;

    ProjectStatus(String value, String text) {
        this.value = value;
        this.text = text;
    }

    @JsonCreator
    public static ProjectStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        return VALUE_MAP.get(value);
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
