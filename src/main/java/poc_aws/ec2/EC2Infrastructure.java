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
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.elasticloadbalancing.model.Instance;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingAsyncClientBuilder;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancing.model.*;

import java.util.*;

public class EC2Infrastructure {
    /** ec2 client for amazon instances */
    private AmazonEC2 ec2Client;

    /** load balancer client*/
    private AmazonElasticLoadBalancing lbClient;

    /** maps of maps to keep correspondence of vpdid and sgid */
    Map<String, Map<String, String>> securityGroupsByVpc;
    /** map of subnets by address */
    Map<String, Subnet> subnetsByNetwork;

    /** map of listeners of the load balancer name */
    Map<String,List<Listener>> listenersOfLb;

    public EC2Infrastructure() {
        AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
        ec2Client = AmazonEC2ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(Regions.US_EAST_1).build();
        securityGroupsByVpc = new HashMap<>();
        subnetsByNetwork = new HashMap<>();
        listenersOfLb = new HashMap<>();
        lbClient = AmazonElasticLoadBalancingClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials)).withRegion(Regions.US_EAST_1).build();
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
        listenersOfLb = new HashMap<>();
        lbClient = AmazonElasticLoadBalancingAsyncClientBuilder.standard()
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
     * get the first create security group id
     * @param  vpcId the VPC id where the SG is assigned
     * @param sgName security group name
     * @return security group id
     * TODO: now returns the first sg with the required name this should return all.
     */
    public String getSecurityGroupId(String vpcId, String sgName) {
        Map<String, String> sgInVpc = securityGroupsByVpc.get(vpcId);
        if (sgInVpc != null) {
            return sgInVpc.get(sgName);
        } else {
            return null;
        }
    }

    /**
     * add a firewall rule to the security group
     * @param vpcId the Id the VPC where the SG is.
     * @param securityGroupName the name of the security group
     * @param ipRangesStr ip ranges for the required protocol
     * @param protocol the protocol
     * @param fromPort inbound port
     * @param toPort outbound port
     * @return  true if it could add the firewall rule
     */
    public EC2Infrastructure addFirewallRule(String vpcId, String securityGroupName, List<String> ipRangesStr, String protocol, int fromPort, int toPort) {
        IpPermission ipPermission = new IpPermission();
        List<IpRange> ipRanges = new ArrayList<>();
        ipRangesStr.forEach(a -> ipRanges.add(new IpRange().withCidrIp(a)));
        ipPermission.withIpv4Ranges(ipRanges).withIpProtocol(protocol).withFromPort(fromPort).withToPort(toPort);
        AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest = new AuthorizeSecurityGroupIngressRequest().withIpPermissions(ipPermission);
        String securityGroupId = getSecurityGroupId(vpcId, securityGroupName);
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
     * Create a VPC.
     * @param name the name of the vpc
     * @param ipRange the ip range
     * @return the new created VPC or the existing one if exists.
     */
    public Vpc createVpc(String name, String ipRange) {
        Vpc vpc = getVpcIfExists(ipRange);
        if (vpc != null) {
            return vpc;
        }
        CreateVpcRequest request = new CreateVpcRequest(ipRange);
        CreateVpcResult result = ec2Client.createVpc(request);
        CreateTagsRequest tagNameRequest = new CreateTagsRequest().withResources(result.getVpc().getVpcId());
        tagNameRequest.withTags(new Tag().withKey("Name").withValue(name));
        ec2Client.createTags(tagNameRequest);
        ModifyVpcAttributeRequest requestDNS = new ModifyVpcAttributeRequest()
                .withEnableDnsHostnames(true).withVpcId(result.getVpc().getVpcId());
        ec2Client.modifyVpcAttribute(requestDNS);
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
     * @param name the name of the subnet
     * @return this to be chained.
     */
    public EC2Infrastructure addSubnet(String vpcId, String network, String availabilityZone, String name) {
        CreateSubnetRequest request = new CreateSubnetRequest();
        request.withAvailabilityZone(availabilityZone).withCidrBlock(network).withVpcId(vpcId);
        CreateSubnetResult result = ec2Client.createSubnet(request);
        subnetsByNetwork.put(network, result.getSubnet());
        CreateTagsRequest tagNameRequest = new CreateTagsRequest().withResources(result.getSubnet().getSubnetId());
        tagNameRequest.withTags(new Tag().withKey("Name").withValue(name));
        ec2Client.createTags(tagNameRequest);
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
     * @param subnets the subnets associated with LB
     * @param securityGroupId the security group
     * @param crossBlancingAZ true if will load balancing in all AX
     */
    public void createLoadBalancer(String name, boolean crossBlancingAZ, List<String> subnets, String...securityGroupId) {
        CreateLoadBalancerRequest request = new CreateLoadBalancerRequest().withLoadBalancerName(name);
        request.withSubnets(subnets).withSecurityGroups(securityGroupId)
                .withTags(Arrays.asList(new com.amazonaws.services.elasticloadbalancing.model.Tag().withKey("Name").withValue(name)));
        request.withListeners(listenersOfLb.get(name));
        CreateLoadBalancerResult result = lbClient.createLoadBalancer(request);
        if(crossBlancingAZ) {
           ModifyLoadBalancerAttributesRequest enableLBAZ = new ModifyLoadBalancerAttributesRequest();
           enableLBAZ.withLoadBalancerName(name).withLoadBalancerAttributes(new LoadBalancerAttributes().withCrossZoneLoadBalancing(new CrossZoneLoadBalancing().withEnabled(true)));
           lbClient.modifyLoadBalancerAttributes(enableLBAZ);
        }
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
    public EC2Infrastructure addListnerToExistingLoadBalancer(String lbName, String lbProtocol,int lbPort, String instanceProtocol, int instancePort) {
        Listener listener = new Listener().withInstancePort(instancePort).withInstanceProtocol(instanceProtocol).withLoadBalancerPort(lbPort).withProtocol(lbProtocol);
        CreateLoadBalancerListenersRequest request = new CreateLoadBalancerListenersRequest().withListeners(listener).withLoadBalancerName(lbName);
        lbClient.createLoadBalancerListeners(request);
        return this;
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
        if (listenersOfLb.containsKey(lbName)) {
            listenersOfLb.get(lbName).add(listener);
        } else {
            List<Listener> listeners = new ArrayList<>();
            listeners.add(listener);
            listenersOfLb.put(lbName,listeners );
        }
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

    /**
     * Create internet gateway
     * @param name the name the of the internet gateway
     * @param vpcId the VPC which wants to connect to internet
     * @return internet gateway id
     */
    public String  createInternetGateway(String name, String vpcId) {
        CreateInternetGatewayResult result = ec2Client.createInternetGateway(new CreateInternetGatewayRequest());
        AttachInternetGatewayRequest request = new AttachInternetGatewayRequest().withInternetGatewayId(result.getInternetGateway().getInternetGatewayId()).withVpcId(vpcId);
        ec2Client.attachInternetGateway(request);
        String igwId = result.getInternetGateway().getInternetGatewayId();
        CreateTagsRequest tagNameRequest = new CreateTagsRequest().withResources(igwId);
        tagNameRequest.withTags(new Tag().withKey("Name").withValue(name));
        return igwId;
    }

    /**
     * add a tag Name with the name value to the new route created in vpcId and witout name.
     * @param name name of the route
     * @param vpcId the vpcid in which the route table reside
     * @return this to be chained
     */
    public EC2Infrastructure addTagNameToNewCreatedRouteTable(String name, String vpcId) {
        DescribeRouteTablesRequest request1 = new DescribeRouteTablesRequest().withFilters(new Filter().withName("vpc-id").withValues(vpcId));
        List<RouteTable> routes = ec2Client.describeRouteTables(request1).getRouteTables();
        int index = 0;
        for (int i=0 ; i < routes.size(); i++) {
            for (Tag tag :routes.get(i).getTags()) {
                if ("Name".equals(tag.getKey())) {
                    index = -1;
                    break;
                }
            }
            if( index == 0) {
                index = i;
                break;
            }
        }
        String id = routes.get(index).getRouteTableId();
        CreateTagsRequest tagNameRequest = new CreateTagsRequest().withResources(id);
        tagNameRequest.withTags(new Tag().withKey("Name").withValue(name));
        ec2Client.createTags(tagNameRequest);
        return this;
    }

    /**
     * add an external route to the route table to the internet gateway.
     * @param address address
     * @param internetGatewayId internet gateway id
     * @param name of the route table
     * @return this to be chained.
     */
    public EC2Infrastructure addRouteToRouteTableToInternetGatewayId(String address, String internetGatewayId, String name) {
        DescribeRouteTablesRequest request1 = new DescribeRouteTablesRequest().withFilters(new Filter().withName("tag:Name").withValues(name));
        String id = ec2Client.describeRouteTables(request1).getRouteTables().get(0).getRouteTableId();
        CreateRouteRequest requestRoute = new CreateRouteRequest();
        requestRoute.withRouteTableId(id).withDestinationCidrBlock(address).withGatewayId(internetGatewayId);
        ec2Client.createRoute(requestRoute);
        return this;
    }

    /**
     * add an external route to the route table to the internet gateway.
     * @param address address
     * @param natGatewayId internet gateway id
     * @param name of the route table
     * @return this to be chained.
     */
    public EC2Infrastructure addRouteToRouteTableToNatGatewayId(String address, String natGatewayId, String name) {
        DescribeNatGatewaysRequest natGatewaysRequest = new DescribeNatGatewaysRequest().withNatGatewayIds(natGatewayId);
        DescribeNatGatewaysResult result;
        do {
            result = ec2Client.describeNatGateways(natGatewaysRequest);
            if (result.getNatGateways().size() == 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {

                }
            }
        } while (!(result.getNatGateways().size() == 1 && result.getNatGateways().get(0).getState().equals(NatGatewayState.Available.toString())));
        DescribeRouteTablesRequest request1 = new DescribeRouteTablesRequest().withFilters(new Filter().withName("tag:Name").withValues(name));
        String id = ec2Client.describeRouteTables(request1).getRouteTables().get(0).getRouteTableId();
        CreateRouteRequest requestRoute = new CreateRouteRequest();
        requestRoute.withRouteTableId(id).withDestinationCidrBlock(address).withNatGatewayId(natGatewayId);
        ec2Client.createRoute(requestRoute);
        return this;
    }

    /**
     * Assign a subnet to the Route Table
     * @param subnetId the subnet id to be assigned
     * @param name of the route table
     * @return this to be chained
     */
    public EC2Infrastructure assignSubnetToRouteTable(String subnetId, String name) {
        DescribeRouteTablesRequest request1 = new DescribeRouteTablesRequest().withFilters(new Filter().withName("tag:Name").withValues(name));
        String id = ec2Client.describeRouteTables(request1).getRouteTables().get(0).getRouteTableId();
        AssociateRouteTableRequest request = new AssociateRouteTableRequest().withRouteTableId(id).withSubnetId(subnetId);
        ec2Client.associateRouteTable(request);
        return this;
    }

    /**
     * Create a new route table into a vpc
     * @param name name of the route table
     * @param vpcId the vpcid in which the route table is reside
     * @return this to be chained
     */
    public EC2Infrastructure createRouteTable(String name, String vpcId) {
        CreateRouteTableRequest request = new CreateRouteTableRequest().withVpcId(vpcId);
        CreateRouteTableResult result = ec2Client.createRouteTable(request);
        CreateTagsRequest tagNameRequest = new CreateTagsRequest().withResources(result.getRouteTable().getRouteTableId());
        tagNameRequest.withTags(new Tag().withKey("Name").withValue(name));
        ec2Client.createTags(tagNameRequest);
        return this;
    }

    /**
     * Create ElasticIpAddress.
     * @return the id of the allocated elastic Ip address
     */
    public String createElasticIpAddressOnVpc() {
        AllocateAddressRequest request = new AllocateAddressRequest().withDomain(DomainType.Vpc);
        return ec2Client.allocateAddress(request).getAllocationId();
    }

    /**
     * Careate a network gateway in a subnet using an elastic Ip address.
     * @param name the name of the nat gateway
     * @param subnetId the subnet association
     * @param elasticIpId the elastic Ip address
     * @return the nat gateway id
     */
    public String createNatGateway(String name, String subnetId, String elasticIpId) {
        CreateNatGatewayRequest natRequest = new CreateNatGatewayRequest().withSubnetId(subnetId).withAllocationId(elasticIpId);
        String natGatewayId =  ec2Client.createNatGateway(natRequest).getNatGateway().getNatGatewayId();
        CreateTagsRequest tagNameRequest = new CreateTagsRequest().withResources(natGatewayId);
        tagNameRequest.withTags(new Tag().withKey("Name").withValue(name));
        ec2Client.createTags(tagNameRequest);
        return natGatewayId;
    }
}
