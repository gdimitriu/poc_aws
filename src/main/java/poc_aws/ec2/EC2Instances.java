package poc_aws.ec2;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;

public class EC2Instances {
    /** ec2 client for amazon instances */
    private AmazonEC2 ec2Client;

    public EC2Instances(AmazonEC2 client) {
        this.ec2Client = client;
    }

    /**
     * Run a specific instance
     * @param osType the type of the image (OS)
     * @param type type of the instance (T2.micro for the Siplilearn account)
     * @param min minimum number of instances
     * @param max maximum number of instances
     * @param keyName the keyPair to connect to instance
     * @param securityGroup the security group assigned to this instance
     * @param vpcID the id of the VPC in which we will launch this instance
     * @param  subnetId the id of the subnet in which will run the instance
     * @return string representing the instance id
     */
    public String runInstance(String osType, InstanceType type, int min, int max, String keyName, String securityGroup, String vpcID, String subnetId) {
        RunInstancesRequest request = new RunInstancesRequest();
        request.withImageId(osType).withInstanceType(type).withMinCount(min).withMaxCount(max).withKeyName(keyName).withSecurityGroups(securityGroup);
//        if (vpcID != null && !vpcID.isEmpty()) {
//            request.withNetworkInterfaces()
//        }
        if (subnetId != null && !subnetId.isEmpty()) {
            request.withSubnetId(subnetId);
        }
        RunInstancesResult result = ec2Client.runInstances(request);
        return result.getReservation().getInstances().get(0).getInstanceId();
    }

    /**
     * Stop the instance
     * @param instanceId  the id of the instance to be stop
     * @param force true if the instance will be forcefully stoped.
     */
    public void stopInstance(String instanceId, boolean force) throws AmazonEC2Exception {
        DescribeInstancesRequest describeRequest = new DescribeInstancesRequest().withInstanceIds(instanceId);
        DescribeInstancesResult describeResult = ec2Client.describeInstances(describeRequest);
        if (describeResult.getReservations().get(0).getInstances().get(0).getState().getCode() == 16) {
            StopInstancesRequest stopRequest = new StopInstancesRequest();
            stopRequest.withInstanceIds(instanceId).withForce(force);
            ec2Client.stopInstances(stopRequest);
        } else {
            System.out.printf("Instance with id %s is not running yet !\n", instanceId);
        }
    }


    /**
     * Start a stopped instance.
     * @param instanceId the id of the instance to be started
     */
    public void startInstance(String instanceId) {
        StartInstancesRequest request = new StartInstancesRequest();
        request.withInstanceIds(instanceId);
        ec2Client.startInstances(request);
    }

    /**
     * Terminate an instance;
     * @param instanceId the id of the instance to be terminated
     */
    public void terminateInstance(String instanceId) {
        TerminateInstancesRequest request = new TerminateInstancesRequest();
        request.withInstanceIds(instanceId);
        ec2Client.terminateInstances(request);
    }
}
