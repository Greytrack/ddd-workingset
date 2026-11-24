package com.airedgesoft.bridge.v3.metadata.cache;

import com.airedgesoft.sharedkernel.businessframework.application.query.PageViewProjectionPort;
import com.airedgesoft.sharedkernel.businessframework.application.query.QPageView;
import com.airedgesoft.sharedkernel.businessframework.application.query.ResolvePageViewQuery;
import org.springframework.stereotype.Component;

@Component
public class PageViewProjectionPortImpl implements PageViewProjectionPort {
    @Override
    public QPageView resolve(ResolvePageViewQuery query) {
        return null;
    }
}
