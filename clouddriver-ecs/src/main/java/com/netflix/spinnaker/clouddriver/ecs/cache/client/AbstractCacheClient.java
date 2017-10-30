package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractCacheClient<T> {

  private final String keyNamespace;
  private final Cache cacheView;

  AbstractCacheClient(Cache cacheView, String keyNamespace) {
    this.cacheView = cacheView;
    this.keyNamespace = keyNamespace;
  }

  protected abstract T convert(CacheData cacheData);

  public Collection<T> getAll() {
    Collection<CacheData> allData = cacheView.getAll(keyNamespace);
    return convertAll(allData);
  }

  public Collection<T> getAll(String account, String region) {
    Collection<CacheData> data = fetchFromCache(account, region);
    return convertAll(data);
  }

  public T get(String key) {
    return convert(cacheView.get(keyNamespace, key));
  }

  private Collection<T> convertAll(Collection<CacheData> cacheData) {
    Set<T> itemSet = new HashSet<>();
    for (CacheData cacheDatum : cacheData) {
      itemSet.add(convert(cacheDatum));
    }
    return itemSet;
  }

  /**
   * @param account name of the AWS account, as defined in clouddriver.yml
   * @param region  is not used in AWS as IAM is region-agnostic
   * @return
   */
  private Collection<CacheData> fetchFromCache(String account, String region) {
    String accountFilter = account != null ? account + Keys.SEPARATOR : "*" + Keys.SEPARATOR;
    String regionFilter = region != null ? region + Keys.SEPARATOR : "*" + Keys.SEPARATOR;
    Set<String> keys = new HashSet<>();
    String pattern = "ecs" + Keys.SEPARATOR + keyNamespace + Keys.SEPARATOR + accountFilter + regionFilter + "*";
    Collection<String> nameMatches = cacheView.filterIdentifiers(keyNamespace, pattern);

    keys.addAll(nameMatches);

    Collection<CacheData> allData = cacheView.getAll(keyNamespace, keys);

    return allData;
  }
}
