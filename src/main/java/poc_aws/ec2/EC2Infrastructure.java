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

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingAsyncClientBuilder;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerListenersRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.Listener;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.Instance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EC2Infrastructure {
    /** ec2 client for amazon instances */
    private AmazonEC2 ec2Client;

    /** load balancer client*/
    private AmazonElasticLoadBalancingClient lbClient;

    /** maps of maps to keep correspondence of vpdid and sgid */
    Map<String, Map<String, String>> securityGroupsByVpc;
    /** map of subnets by address */
    Map<String, Subnet> subnetsByNetwork;

    public EC2Infrastructure() {
        AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
        ec2Client = AmazonEC2ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(Regions.US_EAST_1).build();
        securityGroupsByVpc = new HashMap<>();
        subnetsByNetwork = new HashMap<>();
        AmazonElasticLoadBalancingAsyncClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials)).withRegion(Regions.US_EAST_1).build();
    }

    /**
     * Constructor using the client for amazon EC2.
     * @param client of the amazon EC2
     */
    public EC2Infrastructure(AmazonEC2 client)
    {
        this.ec2Client = client;
        securityGroupsByVpc = new HashMap<>();
        subnetsByNetwork = new HashMap<>();
        AmazonElasticLoadBalancingAsyncClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(new ProfileCredentialsProvider().getCredentials())).withRegion(Regions.US_EAST_1).build();
    }

    /**
     * Check if security group exists
     * @param sgName name of the security group
     * @param vpcId the vpc Id or null for the default VPC
     * @return true if security group exists false otherwise
     */
    private boolean isSecurityGroupCreated(String sgName, String vpcId) {
        if (vpcId == null) {
            DescribeSecurityGroupsRequest request = new DescribeSecurityGroupsRequest().withGroupNames(sgName);
            try {
                DescribeSecurityGroupsResult result = ec2Client.describeSecurityGroups(request);
                return result.getSecurityGroups().stream().map(SecurityGroup::getGroupName).anyMatch(str -> str.equals(sgName));
            } catch (AmazonEC2Exception e) {
                return false;
            }
        } else {
            if (!securityGroupsByVpc.containsKey(vpcId)) {
                return false;
            }
            Map<String, String> securityGroups = securityGroupsByVpc.get(vpcId);
            if (securityGroups.containsKey(sgName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create the security group.
     * @param sgName security group name
     * @param sgDescription security group description
     * @param vpcId VPC id if is null or empty then it will be the default VPC
     * @return this class to have chain of configuration.
     */
    public EC2Infrastructure createSecurityGroup(String sgName, String sgDescription, String vpcId) {
        String realVpc = vpcId;
        if(!isSecurityGroupCreated(sgName, vpcId)) {
            CreateSecurityGroupRequest request = new CreateSecurityGroupRequest().withGroupName(sgName)
                    .withDescription(sgDescription);
            if (realVpc != null && !realVpc.isEmpty()) {
                request.withVpcId(realVpc);
            } else {
                realVpc = "default";
            }
            CreateSecurityGroupResult result = ec2Client.createSecurityGroup(request);
            if (securityGroupsByVpc.containsKey(realVpc)) {
                securityGroupsByVpc.get(realVpc).put(sgName,result.getGroupId());
            } else {
                Map<String, String> sgs = new HashMap<>();
                sgs.put(sgName, result.getGroupId());
                securityGroupsByVpc.put(realVpc, sgs);
            }
        }
        return this;
    }

    /**
     * add a firewall rule to the security group
     * @param securityGroupName the name of the security group
     * @param ipRangesStr ip ranges for the required protocol
     * @param protocol the protocol
     * @param fromPort inbound port
     * @param toPort outbound port
     * @return  true if it could add the firewall rule
     */
    public EC2Infrastructure addFirewallRule(String securityGroupName, List<String> ipRangesStr, String protocol, int fromPort, int toPort) {
        IpPermission ipPermission = new IpPermission();
        List<IpRange> ipRanges = new ArrayList<>();
        ipRangesStr.forEach(a -> ipRanges.add(new IpRange().withCidrIp(a)));
        ipPermission.withIpv4Ranges(ipRanges).withIpProtocol(protocol).withFromPort(fromPort).withToPort(toPort);
        AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest = new AuthorizeSecurityGroupIngressRequest().withIpPermissions(ipPermission);
        String securityGroupId = getSecurityGroupId(securityGroupName);
        if (securityGroupId != null) {
            authorizeSecurityGroupIngressRequest.withGroupId(securityGroupId);
        } else {
            System.out.println("Security group does not exist! You must create one before add firewalls rules.");
            return this;
        }
        try {
            ec2Client.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest);
        } catch (AmazonEC2Exception e) {
            if (e.getErrorCode().equals("InvalidPermission.Duplicate")) {
                System.out.println("Rule already exist for ipRange " + ipRangesStr + " on protocol " + protocol);
            }
            System.out.println(e.getLocalizedMessage());
        }
        return this;
    }

    /**
     * get the first create security group id
     * @param sgName security group name
     * @return security group id
     * TODO: now returns the first sg with the required name this should return all.
     */
    public String getSecurityGroupId(String sgName) {
        for ( Map.Entry<String, Map<String, String>> entry : securityGroupsByVpc.entrySet()) {
            if(!entry.getValue().containsKey(sgName)) {
                continue;
            }
            return entry.getValue().get(sgName);
        }
        return null;
    }

    /**
     * Create a VPC.
     * @param ipRange
     * @return the new created VPC or the existing one if exists.
     */
    public Vpc createVpc(String ipRange) {
        Vpc vpc = getVpcIfExists(ipRange);
        if (vpc != null) {
            return vpc;
        }
        CreateVpcRequest request = new CreateVpcRequest(ipRange);
        CreateVpcResult result = ec2Client.createVpc(request);
        return result.getVpc();
    }

    /**
     * get the VPC with the ip range
     * @param ipRange the range of the VPC
     * @return null if does not exist
     */
    private Vpc getVpcIfExists(String ipRange) {
        DescribeVpcsResult result = ec2Client.describeVpcs(new DescribeVpcsRequest());
        for (Vpc vpc : result.getVpcs()) {
            if (ipRange.equals(vpc.getCidrBlock())) {
                return vpc;
            }
        }
        return null;
    }

    /**
     * Add a subnet to the vpcId it will put into the hash map to retrieve later.
     * @param vpcId the vpcId in which the subnet will reside
     * @param network the ip range address of the network
     * @param availabilityZone the availability zone for the subnet
     * @return this to be chained.
     */
    public EC2Infrastructure addSubnet(String vpcId, String network, String availabilityZone) {
        CreateSubnetRequest request = new CreateSubnetRequest();
        request.withAvailabilityZone(availabilityZone).withCidrBlock(network).withVpcId(vpcId);
        CreateSubnetResult result = ec2Client.createSubnet(request);
        subnetsByNetwork.put(network, result.getSubnet());
        return this;
    }

    /**
     * get the subnetId
     * @param network  the network ip range
     * @return subnetId
     */
    public String getSubnetId(String network) {
        return subnetsByNetwork.get(network).getSubnetId();
    }

    /**
     * create a clasic load balancer.
     * @param name name of the LB
     * @param availabilityZones  the availability zones of the LB
     * @param subnets the subnets associated with LB
     * @param securityGroupId the security group
     */
    public void createLoadBalancer(String name, List<String> availabilityZones, List<String> subnets, String...securityGroupId) {
        CreateLoadBalancerRequest request = new CreateLoadBalancerRequest().withLoadBalancerName(name).withAvailabilityZones(availabilityZones);
        request.withSubnets(subnets).withSecurityGroups(securityGroupId);
        lbClient.createLoadBalancer(request);
    }

    /**
     * Add a listener to a load balancer
     * @param lbName name of the load balancer
     * @param lbProtocol the load balancer protocol
     * @param lbPort the load balancer port
     * @param instanceProtocol the instance protocol
     * @param instancePort the instance port
     * @return this to be chained.
     */
    public EC2Infrastructure addListnerToLoadBalancer(String lbName, String lbProtocol,int lbPort, String instanceProtocol, int instancePort) {
        Listener listener = new Listener().withInstancePort(instancePort).withInstanceProtocol(instanceProtocol).withLoadBalancerPort(lbPort).withProtocol(lbProtocol);
        CreateLoadBalancerListenersRequest request = new CreateLoadBalancerListenersRequest().withListeners(listener).withLoadBalancerName(lbName);
        lbClient.createLoadBalancerListeners(request);
        return this;
    }

    /**
     * add/register the instances to a load balancer
     * @param lbName load balancer name
     * @param instanceIds list of instances id
     * @return this to be chained
     */
    public EC2Infrastructure addInstacesToLB(String lbName, List<String> instanceIds) {
        RegisterInstancesWithLoadBalancerRequest request = new RegisterInstancesWithLoadBalancerRequest();
        List<Instance> instances = new ArrayList<>();
        for (String id : instanceIds) {
            instances.add(new Instance(id));
        }
        request.withLoadBalancerName(lbName).withInstances(instances);
        lbClient.registerInstancesWithLoadBalancer(request);
        return this;
    }
}
