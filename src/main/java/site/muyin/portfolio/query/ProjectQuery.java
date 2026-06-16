package site.muyin.portfolio.query;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.springdoc.core.fn.builders.operation.Builder;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.router.IListRequest;
import run.halo.app.extension.router.SortableRequest;

import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static run.halo.app.extension.index.query.Queries.contains;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.or;
import static run.halo.app.extension.router.QueryParamBuildUtil.sortParameter;
import static run.halo.app.extension.router.selector.SelectorUtil.labelAndFieldSelectorToListOptions;

/**
 * 项目查询条件。
 *
 * @author Lywq
 * @since 1.0.0
 */
@Getter
public class ProjectQuery extends SortableRequest {

    private final MultiValueMap<String, String> queryParams;

    public ProjectQuery(ServerWebExchange exchange) {
        super(exchange);
        this.queryParams = new LinkedMultiValueMap<>(exchange.getRequest().getQueryParams());
    }

    @Nullable
    @Schema(description = "按关键词筛选项目")
    public String getKeyword() {
        return clean(queryParams.getFirst("keyword"));
    }

    @Nullable
    @Schema(description = "按平台筛选项目")
    public String getPlatform() {
        return clean(queryParams.getFirst("platform"));
    }

    @Nullable
    @Schema(description = "按类型筛选项目")
    public String getType() {
        return clean(queryParams.getFirst("type"));
    }

    @Nullable
    @Schema(description = "按状态筛选项目")
    public String getStatus() {
        return clean(queryParams.getFirst("status"));
    }

    @Nullable
    @Schema(description = "按标签筛选项目")
    public String getTag() {
        return clean(queryParams.getFirst("tag"));
    }

    @Nullable
    @Schema(description = "是否只查询推荐项目")
    public String getFeatured() {
        return clean(queryParams.getFirst("featured"));
    }

    public void setQueryParam(String key, String value) {
        queryParams.set(key, value);
    }

    public ListOptions toListOptions() {
        var base = labelAndFieldSelectorToListOptions(getLabelSelector(), getFieldSelector());
        var query = ListOptions.builder(base);
        if (StringUtils.hasText(getKeyword())) {
            query.andQuery(or(
                contains("title", getKeyword()),
                contains("summary", getKeyword()),
                equal("slug", getKeyword())
            ));
        }
        if (StringUtils.hasText(getPlatform())) {
            query.andQuery(equal("platform", getPlatform()));
        }
        if (StringUtils.hasText(getType())) {
            query.andQuery(equal("type", getType()));
        }
        if (StringUtils.hasText(getStatus())) {
            query.andQuery(equal("status", getStatus()));
        }
        if (StringUtils.hasText(getTag())) {
            query.andQuery(or(
                equal("tags", getTag()),
                equal("techStacks", getTag())
            ));
        }
        if (StringUtils.hasText(getFeatured())) {
            query.andQuery(equal("featured", Boolean.parseBoolean(getFeatured())));
        }
        return query.build();
    }

    public PageRequest toPageRequest() {
        var sort = getSort();
        if (sort.isUnsorted()) {
            sort = defaultSort();
        }
        return PageRequestImpl.of(getPage(), getSize(), sort);
    }

    public static Sort defaultSort() {
        return Sort.by(
            Sort.Order.desc("priority"),
            Sort.Order.desc("metadata.creationTimestamp")
        );
    }

    public static void buildParameters(Builder builder) {
        IListRequest.buildParameters(builder);
        builder.parameter(sortParameter())
            .parameter(parameterBuilder().in(ParameterIn.QUERY).name("keyword")
                .description("按关键词筛选项目").implementation(String.class).required(false))
            .parameter(parameterBuilder().in(ParameterIn.QUERY).name("platform")
                .description("按平台筛选项目").implementation(String.class).required(false))
            .parameter(parameterBuilder().in(ParameterIn.QUERY).name("type")
                .description("按类型筛选项目").implementation(String.class).required(false))
            .parameter(parameterBuilder().in(ParameterIn.QUERY).name("status")
                .description("按状态筛选项目").implementation(String.class).required(false))
            .parameter(parameterBuilder().in(ParameterIn.QUERY).name("tag")
                .description("按标签筛选项目").implementation(String.class).required(false))
            .parameter(parameterBuilder().in(ParameterIn.QUERY).name("featured")
                .description("是否只查询推荐项目").implementation(Boolean.class).required(false));
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
