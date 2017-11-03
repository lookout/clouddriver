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
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.ListRolesRequest;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.amazonaws.services.identitymanagement.model.Role;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.IamRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.IAM_ROLE;

public class IamRoleCachingAgent extends AbstractEcsCachingAgent<IamRole> {

  public static final String DEFAULT_IAM_REGION = "us-east-1";
  static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(IAM_ROLE.toString())
  ));
  private final Logger log = LoggerFactory.getLogger(getClass());
  private AmazonClientProvider amazonClientProvider;
  private AWSCredentialsProvider awsCredentialsProvider;
  private String accountName;
  private IamPolicyReader iamPolicyReader;


  public IamRoleCachingAgent(String accountName,
                             AmazonClientProvider amazonClientProvider,
                             AWSCredentialsProvider awsCredentialsProvider,
                             IamPolicyReader iamPolicyReader) {
    super(accountName, null, amazonClientProvider, awsCredentialsProvider);
    this.accountName = accountName;
    this.amazonClientProvider = amazonClientProvider;
    this.awsCredentialsProvider = awsCredentialsProvider;
    this.iamPolicyReader = iamPolicyReader;
  }

  public static Map<String, Object> convertIamRoleToAttributes(IamRole iamRole) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("name", iamRole.getName());
    attributes.put("accountName", iamRole.getAccoutName());
    attributes.put("arn", iamRole.getId());
    attributes.put("trustRelationships", iamRole.getTrustRelationships());
    return attributes;
  }

  @Override
  protected Map<String, Collection<CacheData>> generateFreshData(Collection<IamRole> cacheableRoles) {
    Collection<CacheData> dataPoints = new HashSet<>();
    Map<String, Collection<CacheData>> newDataMap = new HashMap<>();

    for (IamRole iamRole : cacheableRoles) {
      String key = Keys.getIamRoleKey(accountName, iamRole.getName());
      Map<String, Object> attributes = convertIamRoleToAttributes(iamRole);

      CacheData data = new DefaultCacheData(key, attributes, Collections.emptyMap());
      dataPoints.add(data);
    }
    log.info(String.format("Caching %s IAM roles in %s for account %s",
      dataPoints.size(),
      getAgentType(),
      accountName)
    );
    newDataMap.put(IAM_ROLE.toString(), dataPoints);
    return newDataMap;
  }

  @Override
  protected List<IamRole> getItems(AmazonECS ecs, ProviderCache providerCache) {
    AmazonIdentityManagement iam = amazonClientProvider.getIam(accountName, awsCredentialsProvider, DEFAULT_IAM_REGION);
    return new ArrayList(fetchIamRoles(iam, accountName));
  }

  private Collection<IamRole> fetchIamRoles(AmazonIdentityManagement iam, String accountName) {
    Set<IamRole> cacheableRoles = new HashSet<>();
    String marker = null;
    do {
      ListRolesRequest request = new ListRolesRequest();
      if (marker != null) {
        request.setMarker(marker);
      }

      ListRolesResult listRolesResult = iam.listRoles(request);
      List<Role> roles = listRolesResult.getRoles();
      for (Role role : roles) {
        cacheableRoles.add(
          new IamRole(role.getArn(),
            role.getRoleName(),
            accountName,
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
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }
}
