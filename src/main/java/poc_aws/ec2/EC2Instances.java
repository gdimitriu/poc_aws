package poc_aws.ec2;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

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
     * @param securityGroupId the security group id assigned to this instance
     * @param  subnetId the id of the subnet in which will run the instance
     * @param installingScript  the script to run at install
     * @return string representing the instance id
     */
    public String runInstance(String osType, InstanceType type, int min, int max, String keyName, String securityGroupId, String subnetId, String installingScript) {
        RunInstancesRequest request = new RunInstancesRequest();
        request.withImageId(osType).withInstanceType(type).withMinCount(min).withMaxCount(max).withKeyName(keyName);//.withSecurityGroupIds(securityGroupId);
        List<InstanceNetworkInterfaceSpecification> interfaces = new ArrayList<>();
        InstanceNetworkInterfaceSpecification interfaceDNS = new InstanceNetworkInterfaceSpecification();
        interfaceDNS.withSubnetId(subnetId).withAssociatePublicIpAddress(true).setDeviceIndex(0);
        interfaceDNS.setGroups(Arrays.asList(securityGroupId));
        interfaces.add(interfaceDNS);
        request.withNetworkInterfaces(interfaces).withAdditionalInfo("--associate-public-ip-address");
//        if (subnetId != null && !subnetId.isEmpty()) {
//            request.withSubnetId(subnetId);
//        }
        if (installingScript != null) {
            request.withUserData(Base64.getEncoder().encodeToString(installingScript.getBytes()));
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
