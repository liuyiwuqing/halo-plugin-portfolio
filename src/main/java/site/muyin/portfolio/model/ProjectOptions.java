package site.muyin.portfolio.model;

import java.util.List;
import site.muyin.portfolio.setting.PortfolioSetting.OptionItem;

/**
 * 项目管理选项。
 *
 * @author Lywq
 * @since 1.0.0
 */
public record ProjectOptions(List<OptionItem> platformOptions, List<OptionItem> typeOptions) {
}
