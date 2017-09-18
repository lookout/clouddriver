package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.amazonaws.services.ecs.model.StopTaskRequest;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.TerminateAndDecrementServerGroupDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class TerminateAndDecrementServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "TERM_AND_DEC_ECS_SERVER_GROUP";

  private static final String CLUSTER_NAME = "poc";

  private final TerminateAndDecrementServerGroupDescription description;

  @Autowired
  AmazonClientProvider amazonClientProvider;
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider;


  public TerminateAndDecrementServerGroupAtomicOperation(TerminateAndDecrementServerGroupDescription description) {
    this.description = description;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask().updateStatus(BASE_PHASE, "Initializing Terminate and Decrement Amazon ECS Server Group Operation...");
    AmazonCredentials credentials = (AmazonCredentials) accountCredentialsProvider.getCredentials(description.getCredentialAccount());
    AmazonECS ecs = amazonClientProvider.getAmazonEcs(description.getCredentialAccount(), credentials.getCredentialsProvider(), description.getRegion());

    getTask().updateStatus(BASE_PHASE, "Terminating a " + description.getInstance() + " for " + description.getServerGroupName() + " service.");
    ecs.stopTask(new StopTaskRequest().withTask(description.getInstance()).withCluster(CLUSTER_NAME));

    getTask().updateStatus(BASE_PHASE, "Describing " + description.getServerGroupName() + " service.");
    DescribeServicesResult describeServiceResult = ecs.describeServices(new DescribeServicesRequest().withCluster(CLUSTER_NAME).withServices(description.getServerGroupName()));

    int desiredCount = describeServiceResult.getServices().get(0).getDesiredCount();
    if (desiredCount > 0) {
      getTask().updateStatus(BASE_PHASE, "Decreasing desired count to " + (desiredCount - 1) + " for " + description.getServerGroupName() + " service.");
      ecs.updateService(new UpdateServiceRequest().withService(description.getServerGroupName())
        .withCluster(CLUSTER_NAME).withDesiredCount(desiredCount - 1));
    }

    return null;
  }
}
