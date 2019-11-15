/*
 Copyright (c) 2019 Gabriel Dimitriu All rights reserved.
 DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.

 This file is part of poc_aws project.

 poc_aws is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 poc_aws is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with poc_aws.  If not, see <http://www.gnu.org/licenses/>.
 */
package poc_aws.ec2;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class EC2Instances {
    /** ec2 client for amazon instances */
    private Ec2Client ec2Client;

    public EC2Instances(Ec2Client client) {
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
     * @paran name the name of the instance
     * @param instanceProfile the name of the instance profile (which is the same as the role name)
     * @return string representing the instance id
     */
    public String runInstance(String osType, InstanceType type, int min, int max, String keyName, String securityGroupId, String subnetId, String installingScript, String name, String instanceProfile) {
        RunInstancesRequest.Builder builder = RunInstancesRequest.builder().imageId(osType).instanceType(type).minCount(min).maxCount(max).keyName(keyName);
        //do not work with security group and subnets.
        //.withSecurityGroupIds(securityGroupId);
        List<InstanceNetworkInterfaceSpecification> interfaces = new ArrayList<>();
        InstanceNetworkInterfaceSpecification interfaceDNS = InstanceNetworkInterfaceSpecification.builder().subnetId(subnetId)
                .associatePublicIpAddress(true).deviceIndex(0).groups(Arrays.asList(securityGroupId)).build();
        interfaces.add(interfaceDNS);
        builder.networkInterfaces(interfaces).additionalInfo("--associate-public-ip-address");
        if (installingScript != null) {
            builder.userData(Base64.getEncoder().encodeToString(installingScript.getBytes()));
        }
        IamInstanceProfileSpecification profile = IamInstanceProfileSpecification.builder().name(instanceProfile).build();
        RunInstancesRequest request = builder.iamInstanceProfile(profile).build();
        RunInstancesResponse response = ec2Client.runInstances(request);
        CreateTagsRequest tagNameRequest = CreateTagsRequest.builder()
                .resources(response.instances().get(0).instanceId())
                .tags(Tag.builder().key("Name").value(name).build()).build();
        ec2Client.createTags(tagNameRequest);
        return response.instances().get(0).instanceId();
    }

    /**
     * Stop the instance
     * @param instanceId  the id of the instance to be stop
     * @param force true if the instance will be forcefully stoped.
     */
    public void stopInstance(String instanceId, boolean force) {
        DescribeInstancesRequest describeRequest = DescribeInstancesRequest.builder().instanceIds(instanceId).build();
        DescribeInstancesResponse describeResult = ec2Client.describeInstances(describeRequest);
        if (describeResult.reservations().get(0).instances().get(0).state().code() == 16) {
            StopInstancesRequest stopRequest = StopInstancesRequest.builder().instanceIds(instanceId).force(force).build();
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
        StartInstancesRequest request = StartInstancesRequest.builder().instanceIds(instanceId).build();
        ec2Client.startInstances(request);
    }

    /**
     * Terminate an instance;
     * @param instanceId the id of the instance to be terminated
     */
    public void terminateInstance(String instanceId) {
        TerminateInstancesRequest request = TerminateInstancesRequest.builder().instanceIds(instanceId).build();
        ec2Client.terminateInstances(request);
    }
}
