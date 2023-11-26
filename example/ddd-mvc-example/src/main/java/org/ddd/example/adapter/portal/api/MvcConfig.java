package org.ddd.example.adapter.portal.api;

import lombok.RequiredArgsConstructor;
import org.ddd.domain.aggregate.UnitOfWork;
import org.ddd.domain.event.DomainEventSupervisor;
import org.ddd.domain.web.ClearUnitOfWorkContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author <template/>
 * @date
 */
@Configuration
@RequiredArgsConstructor
public class MvcConfig implements WebMvcConfigurer {
    private final UnitOfWork unitOfWork;
    private final DomainEventSupervisor domainEventSupervisor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ClearUnitOfWorkContextInterceptor(unitOfWork, domainEventSupervisor)).addPathPatterns("/**");
    }
}
