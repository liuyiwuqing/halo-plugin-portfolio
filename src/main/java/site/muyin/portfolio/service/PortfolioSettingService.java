package site.muyin.portfolio.service;

import reactor.core.publisher.Mono;
import site.muyin.portfolio.model.ProjectOptions;
import site.muyin.portfolio.setting.PortfolioSetting;

/**
 * 作品集设置服务。
 *
 * @author Lywq
 * @since 1.0.0
 */
public interface PortfolioSettingService {

    Mono<PortfolioSetting> getGeneralSetting();

    Mono<ProjectOptions> getProjectOptions();
}
