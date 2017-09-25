package com.netflix.spinnaker.clouddriver.ecs.provider.config;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.ServiceAgent;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Scope;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableConfigurationProperties(ReservationReportConfigurationProperties.class)
public class EcsProviderConfig {
  @Bean
  @DependsOn("netflixECSCredentials")
  public EcsProvider ecsProvider(AccountCredentialsRepository accountCredentialsRepository){
    EcsProvider provider = new EcsProvider(accountCredentialsRepository, Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()));
    synchronizeEcsProvider(provider);
    return provider;
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  public EcsProviderSynchronizer synchronizeEcsProvider(EcsProvider ecsProvider){
    //TODO: Implement the real functionality, not examples.
    List<Agent> newAgents = new LinkedList<>();
    newAgents.add(new ServiceAgent());
    ecsProvider.getAgents().addAll(newAgents);
    return new EcsProviderSynchronizer();
  }

  class EcsProviderSynchronizer {}
}
