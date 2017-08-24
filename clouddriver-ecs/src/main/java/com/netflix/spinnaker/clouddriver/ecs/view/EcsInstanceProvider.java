package com.netflix.spinnaker.clouddriver.ecs.view;

import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsInstance;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EcsInstanceProvider implements InstanceProvider<EcsInstance> {

  private final String cloudProvider = EcsCloudProvider.ID;

  @Autowired
  private AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  private AmazonClientProvider amazonClientProvider;


  @Override
  public String getCloudProvider() {
    return cloudProvider;
  }

  @Override
  public EcsInstance getInstance(String account, String region, String id) {
    AccountCredentials credentials = accountCredentialsProvider.getCredentials(account);
    if (!(credentials instanceof NetflixAmazonCredentials)) {
      throw new IllegalArgumentException("Invalid credentials: " + account + ":" + region);
    }
    amazonClientProvider.getAmazonECS(account, credentials, region)
    amazonClientProvider.getAmazonEC2((NetflixAmazonCredentials) credentials, region, true)
  }

  @Override
  public String getConsoleOutput(String account, String region, String id) {
    return null;
  }
}
