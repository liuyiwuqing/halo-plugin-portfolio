package site.muyin.portfolio.service.impl;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import site.muyin.portfolio.setting.PortfolioSetting;
import site.muyin.portfolio.setting.PortfolioSetting.OptionItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioSettingServiceImplTest {

    @Mock
    ReactiveSettingFetcher settingFetcher;

    @Test
    void projectOptionsFallbackToDefaultsWhenSettingIsEmpty() {
        when(settingFetcher.fetch(PortfolioSetting.GENERAL_GROUP, PortfolioSetting.class))
            .thenReturn(Mono.empty());
        var service = new PortfolioSettingServiceImpl(settingFetcher);

        var options = service.getProjectOptions().block();

        assertThat(options).isNotNull();
        assertThat(options.platformOptions()).extracting(OptionItem::getValue)
            .contains("github", "gitee", "other");
        assertThat(options.typeOptions()).extracting(OptionItem::getValue)
            .contains("open_source", "plugin", "other");
    }

    @Test
    void projectOptionsIgnoreInvalidConfiguredItems() {
        var setting = new PortfolioSetting();
        setting.setPlatformOptions(List.of(new OptionItem("自托管 Git", "self_git"), new OptionItem("", "")));
        setting.setTypeOptions(List.of(new OptionItem("小程序", "mini_app")));
        when(settingFetcher.fetch(PortfolioSetting.GENERAL_GROUP, PortfolioSetting.class))
            .thenReturn(Mono.just(setting));
        var service = new PortfolioSettingServiceImpl(settingFetcher);

        var options = service.getProjectOptions().block();

        assertThat(options).isNotNull();
        assertThat(options.platformOptions()).extracting(OptionItem::getValue)
            .containsExactly("self_git");
        assertThat(options.typeOptions()).extracting(OptionItem::getValue)
            .containsExactly("mini_app");
    }
}
