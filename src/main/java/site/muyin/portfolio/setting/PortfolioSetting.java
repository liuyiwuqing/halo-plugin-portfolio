package site.muyin.portfolio.setting;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 作品集插件通用设置。
 *
 * @author Lywq
 * @since 1.0.0
 */
@Data
public class PortfolioSetting {

    public static final String GENERAL_GROUP = "general";

    private Boolean defaultPageEnabled = true;

    private Integer pageSize = 12;

    private String seoTitle = "项目作品集";

    private String seoDescription = "集中展示开源项目、产品、插件、工具和其他开发作品。";

    private List<OptionItem> platformOptions = defaultPlatformOptions();

    private List<OptionItem> typeOptions = defaultTypeOptions();

    public static List<OptionItem> defaultPlatformOptions() {
        var options = new ArrayList<OptionItem>();
        options.add(new OptionItem("GitHub", "github"));
        options.add(new OptionItem("Gitee", "gitee"));
        options.add(new OptionItem("独立站点", "website"));
        options.add(new OptionItem("私有项目", "private"));
        options.add(new OptionItem("其他", "other"));
        return options;
    }

    public static List<OptionItem> defaultTypeOptions() {
        var options = new ArrayList<OptionItem>();
        options.add(new OptionItem("开源项目", "open_source"));
        options.add(new OptionItem("产品", "product"));
        options.add(new OptionItem("插件", "plugin"));
        options.add(new OptionItem("网站", "website"));
        options.add(new OptionItem("工具", "tool"));
        options.add(new OptionItem("类库", "library"));
        options.add(new OptionItem("其他", "other"));
        return options;
    }

    @Data
    public static class OptionItem {

        private String label;

        private String value;

        public OptionItem() {
        }

        public OptionItem(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }
}
