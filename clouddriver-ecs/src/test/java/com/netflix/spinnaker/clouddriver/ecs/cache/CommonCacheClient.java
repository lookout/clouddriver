package com.netflix.spinnaker.clouddriver.ecs.cache;

import com.netflix.spinnaker.cats.cache.Cache;

import static org.mockito.Mockito.mock;

class CommonCacheClient {
  static final String REGION = "us-west-2";
  static final String ACCOUNT = "test-account";

  final Cache cacheView = mock(Cache.class);
}
