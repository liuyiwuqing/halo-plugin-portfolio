package site.muyin.portfolio.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.portfolio.model.ProjectOptions;
import site.muyin.portfolio.service.PortfolioSettingService;
import site.muyin.portfolio.setting.PortfolioSetting;
import site.muyin.portfolio.setting.PortfolioSetting.OptionItem;

/**
 * 作品集设置服务实现。
 *
 * @author Lywq
 * @since 1.0.0
 */
@Service
public class PortfolioSettingServiceImpl implements PortfolioSettingService {

    private final ReactiveSettingFetcher settingFetcher;

    public PortfolioSettingServiceImpl(ReactiveSettingFetcher settingFetcher) {
        this.settingFetcher = settingFetcher;
    }

    @Override
    public Mono<PortfolioSetting> getGeneralSetting() {
        return settingFetcher.fetch(PortfolioSetting.GENERAL_GROUP, PortfolioSetting.class)
            .switchIfEmpty(Mono.just(new PortfolioSetting()));
    }

    @Override
    public Mono<ProjectOptions> getProjectOptions() {
        return getGeneralSetting()
            .map(setting -> new ProjectOptions(
                normalizeOptions(setting.getPlatformOptions(), PortfolioSetting.defaultPlatformOptions()),
                normalizeOptions(setting.getTypeOptions(), PortfolioSetting.defaultTypeOptions())
            ));
    }

    private List<OptionItem> normalizeOptions(List<OptionItem> configured, List<OptionItem> defaults) {
        if (configured == null || configured.isEmpty()) {
            return defaults;
        }
        var normalized = configured.stream()
            .filter(item -> item != null && StringUtils.hasText(item.getLabel()) && StringUtils.hasText(item.getValue()))
            .map(item -> new OptionItem(item.getLabel().trim(), item.getValue().trim()))
            .toList();
        return normalized.isEmpty() ? defaults : normalized;
    }
}
