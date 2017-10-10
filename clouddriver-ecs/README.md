## AWS ECS Clouddriver 

The clouddriver-ecs module allows for ECS deployments of dockerized applications.  **You need to enable the AWS cloud provider in order for the ECS cloud provider to work**.

It is a work in progress




## Spinnaker role
Make sure that you allow the `application-autoscaling.amazonaws.com` to assume the SpinnakerManaged role by adding it as a principal.  See example code below.  Failure to do so will prevent you from deploying ECS server groups, as they rely on auto-scaling:
```
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "application-autoscaling.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
```
