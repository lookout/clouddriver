/*
 * Copyright 2017 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.cache;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.IamRole;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.IamTrustRelationship;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.IAM_ROLE;

public class IamRoleCacheClient {

  private final Cache cache;

  @Autowired
  public IamRoleCacheClient(Cache cache) {
    this.cache = cache;
  }

  public Collection<IamRole> getAll() {
    Collection<CacheData> allData = cache.getAll(IAM_ROLE.toString());
    return filterResultsForEcsTrustRelationship(allData);
  }

  public Collection<IamRole> getAll(String account, String region) {
    Collection<CacheData> data = fetchFromCache(account, region);

    Collection<IamRole> result = filterResultsForEcsTrustRelationship(data);

    return result;
  }

  private Collection<IamRole> filterResultsForEcsTrustRelationship(Collection<CacheData> allData) {
    Set<IamRole> result = new HashSet<>();

    for (CacheData cacheData: allData) {
      List<Map<String, String>> trustRelationships = (List<Map<String, String>> ) cacheData.getAttributes().get("trustRelationships");
      for (Map<String, String> trustRelationship : trustRelationships) {
        if (trustRelationship.get("type").equals("Service") && trustRelationship.get("value").equals("ecs-tasks.amazonaws.com")) {

          result.add(convertToRole(cacheData));
          continue;
        }
      }

    }

    return result;
  }

  private IamRole convertToRole(CacheData cacheData) {

    List<Map<String, String>> trustRelationships = (List<Map<String, String>> ) cacheData.getAttributes().get("trustRelationships");
    Set<IamTrustRelationship> iamTrustRelationships = new HashSet<>();

    IamRole iamRole = new IamRole();
    iamRole.setId(cacheData.getAttributes().get("arn").toString());
    iamRole.setName(cacheData.getAttributes().get("name").toString());
    iamRole.setAccountName(cacheData.getAttributes().get("accountName").toString());

    for (Map<String, String> trustRelationship : trustRelationships) {
      IamTrustRelationship iamTrustRelationship = new IamTrustRelationship();
      iamTrustRelationship.setType(trustRelationship.get("type"));
      iamTrustRelationship.setValue(trustRelationship.get("value"));
      iamTrustRelationships.add(iamTrustRelationship);
    }

    iamRole.setTrustRelationships(iamTrustRelationships);


    return iamRole;
  }

  /**
   *
   * @param account name of the AWS account, as defined in clouddriver.yml
   * @param region is not used in AWS as IAM is region-agnostic
   * @return
   */
  private Collection<CacheData> fetchFromCache(String account, String region) {
    Set<String> keys = new HashSet<>();
    Collection<String> nameMatches = cache.filterIdentifiers(IAM_ROLE.ns, "*:" + account + ":*");

    keys.addAll(nameMatches);

    Collection<CacheData> allData = cache.getAll(IAM_ROLE.ns, keys);

    return allData;
  }
}
