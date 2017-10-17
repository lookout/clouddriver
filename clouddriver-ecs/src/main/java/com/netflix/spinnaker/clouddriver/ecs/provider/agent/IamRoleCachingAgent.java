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
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.amazonaws.services.ecs.model.ListServicesRequest;
import com.amazonaws.services.ecs.model.ListServicesResult;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.GetPolicyRequest;
import com.amazonaws.services.identitymanagement.model.ListRolesRequest;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.amazonaws.services.identitymanagement.model.Role;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.IAM_ROLE;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;

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
    Set<IamRole> cacheableRoles = new HashSet();

    Collection<CacheData> dataPoints = new HashSet<>();
    Map<String, Collection<CacheData>> newDataMap = new HashMap<>();
    Map<String, Collection<String>> evictionsByKey = new HashMap<>();

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

    for (IamRole iamRole: cacheableRoles) {
      String key = Keys.getIamRoleKey(accountName, region, iamRole.getName());
      Map<String, Object> attributes = new HashMap<>();

      // TODO - add attributes

      CacheData data = new DefaultCacheData(key, attributes, Collections.emptyMap());
      dataPoints.add(data);
    }


    newDataMap.put(IAM_ROLE.toString(), dataPoints);

    //TODO - do the evictions here

    DefaultCacheResult cacheResult = new DefaultCacheResult(newDataMap, evictionsByKey);
    return cacheResult;
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
