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

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.ListRolesRequest;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.amazonaws.services.identitymanagement.model.Role;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.IamRole;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.IAM_ROLE;

public class IamRoleCachingAgent implements CachingAgent {

  static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(IAM_ROLE.toString())
  ));

  private final Logger log = LoggerFactory.getLogger(getClass());
  private AmazonClientProvider amazonClientProvider;
  private AWSCredentialsProvider awsCredentialsProvider;
  private String region;
  private String accountName;
  private IamPolicyReader iamPolicyReader;


  public IamRoleCachingAgent(String accountName, String region,
                             AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider,
                             IamPolicyReader iamPolicyReader) {
    this.accountName = accountName;
    this.region = region;
    this.amazonClientProvider = amazonClientProvider;
    this.awsCredentialsProvider = awsCredentialsProvider;
    this.iamPolicyReader = iamPolicyReader;
  }


  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    AmazonIdentityManagement iam = amazonClientProvider.getIam(accountName, awsCredentialsProvider, region);

    Set<IamRole> cacheableRoles = fetchIamRoles(iam);
    Map<String, Collection<CacheData>> newDataMap = generateFreshData(cacheableRoles);
    Collection<CacheData> newData = newDataMap.get(IAM_ROLE.toString());

    Set<String> oldKeys = providerCache.getAll(IAM_ROLE.toString()).stream()
      .map(cache -> cache.getId()).collect(Collectors.toSet());
    Map<String, Collection<String>> evictionsByKey = computeEvictableData(newData, oldKeys);

    DefaultCacheResult cacheResult = new DefaultCacheResult(newDataMap, evictionsByKey);
    return cacheResult;
  }

  private Map<String, Collection<String>> computeEvictableData(Collection<CacheData> newData, Collection<String> oldKeys) {

    Set<String> newKeys = newData.stream().map(newKey -> newKey.getId()).collect(Collectors.toSet());

    Set<String> evictedKeys = new HashSet<>();
    for (String oldKey: oldKeys) {
      if (!newKeys.contains(oldKey)) {
        evictedKeys.add(oldKey);
      }
    }
    Map<String, Collection<String>> evictionsByKey = new HashMap<>();
    evictionsByKey.put(IAM_ROLE.toString(), evictedKeys);
    return evictionsByKey;
  }

  private Map<String, Collection<CacheData>> generateFreshData(Set<IamRole> cacheableRoles) {
    Collection<CacheData> dataPoints = new HashSet<>();
    Map<String, Collection<CacheData>> newDataMap = new HashMap<>();
    for (IamRole iamRole: cacheableRoles) {
      String key = Keys.getIamRoleKey(accountName, region, iamRole.getName());
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("name", iamRole.getName());
      attributes.put("arn", iamRole.getRoleArn());
      attributes.put("trustRelationships", iamRole.getTrustRelationships());

      CacheData data = new DefaultCacheData(key, attributes, Collections.emptyMap());
      dataPoints.add(data);
    }

    newDataMap.put(IAM_ROLE.toString(), dataPoints);
    return newDataMap;
  }

  private Set<IamRole> fetchIamRoles(AmazonIdentityManagement iam) {
    Set<IamRole> cacheableRoles = new HashSet();
    String marker = null;
    do {
      ListRolesRequest request = new ListRolesRequest();
      if (marker != null) {
        request.setMarker(marker);
      }

      ListRolesResult listRolesResult = iam.listRoles(request);
      List<Role> roles = listRolesResult.getRoles();

      for (Role role: roles) {
        cacheableRoles.add(
          new IamRole(role.getRoleName(),
            role.getArn(),
            iamPolicyReader.getTrustedEntities(role.getAssumeRolePolicyDocument()))
        );
      }

      if (listRolesResult.isTruncated()) {
        marker = listRolesResult.getMarker();
      } else {
        marker = null;
      }

    } while (marker != null && marker.length() != 0);

    return cacheableRoles;
  }

  @Override
  public String getAgentType() {
    return IamRoleCachingAgent.class.getSimpleName();
  }

  @Override
  public String getProviderName() {
    return EcsProvider.NAME;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }
}
