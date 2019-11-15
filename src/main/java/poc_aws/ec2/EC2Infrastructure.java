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

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.elasticloadbalancing.ElasticLoadBalancingClient;
import software.amazon.awssdk.services.elasticloadbalancing.model.*;
import software.amazon.awssdk.services.elasticloadbalancing.model.Instance;

import java.util.*;

public class EC2Infrastructure {
    /** ec2 client for amazon instances */
    private Ec2Client ec2Client;

    /** load balancer client*/
    private ElasticLoadBalancingClient lbClient;

    /** maps of maps to keep correspondence of vpdid and sgid */
    Map<String, Map<String, String>> securityGroupsByVpc;
    /** map of subnets by address */
    Map<String, Subnet> subnetsByNetwork;

    /** map of listeners of the load balancer name */
    Map<String,List<Listener>> listenersOfLb;

    public EC2Infrastructure() {
        AwsCredentials credentials = ProfileCredentialsProvider.builder().build().resolveCredentials();
        ec2Client = Ec2Client.builder().credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.US_EAST_1).httpClientBuilder(UrlConnectionHttpClient.builder()).build();
        securityGroupsByVpc = new HashMap<>();
        subnetsByNetwork = new HashMap<>();
        listenersOfLb = new HashMap<>();
        lbClient = ElasticLoadBalancingClient.builder().credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.US_EAST_1).httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }

    /**
     * Constructor using the client for amazon EC2.
     * @param client of the amazon EC2
     */
    public EC2Infrastructure(Ec2Client client)
    {
        this.ec2Client = client;
        securityGroupsByVpc = new HashMap<>();
        subnetsByNetwork = new HashMap<>();
        listenersOfLb = new HashMap<>();
        lbClient = ElasticLoadBalancingClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(ProfileCredentialsProvider.builder().build().resolveCredentials()))
                .region(Region.US_EAST_1).httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }

    /**
     * Check if security group exists
     * @param sgName name of the security group
     * @param vpcId the vpc Id or null for the default VPC
     * @return true if security group exists false otherwise
     */
    private boolean isSecurityGroupCreated(String sgName, String vpcId) {
        if (vpcId == null) {
            DescribeSecurityGroupsRequest request = DescribeSecurityGroupsRequest.builder().groupNames(sgName).build();
            try {
                DescribeSecurityGroupsResponse result = ec2Client.describeSecurityGroups(request);
                return result.securityGroups().stream().map(SecurityGroup::groupName).anyMatch(str -> str.equals(sgName));
            } catch (Ec2Exception e) {
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
            CreateSecurityGroupRequest.Builder request = CreateSecurityGroupRequest.builder().groupName(sgName)
                    .description(sgDescription);
            if (realVpc != null && !realVpc.isEmpty()) {
                request.vpcId(realVpc);
            } else {
                realVpc = "default";
            }
            CreateSecurityGroupResponse result = ec2Client.createSecurityGroup(request.build());
            if (securityGroupsByVpc.containsKey(realVpc)) {
                securityGroupsByVpc.get(realVpc).put(sgName,result.groupId());
            } else {
                Map<String, String> sgs = new HashMap<>();
                sgs.put(sgName, result.groupId());
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
        IpPermission.Builder ipPermission = IpPermission.builder();
        List<IpRange> ipRanges = new ArrayList<>();
        ipRangesStr.forEach(a -> ipRanges.add(IpRange.builder().cidrIp(a).build()));
        ipPermission.ipRanges(ipRanges).ipProtocol(protocol).fromPort(fromPort).toPort(toPort);
        AuthorizeSecurityGroupIngressRequest.Builder authorizeSecurityGroupIngressRequest = AuthorizeSecurityGroupIngressRequest.builder().ipPermissions(ipPermission.build());
        String securityGroupId = getSecurityGroupId(vpcId, securityGroupName);
        if (securityGroupId != null) {
            authorizeSecurityGroupIngressRequest.groupId(securityGroupId);
        } else {
            System.out.println("Security group does not exist! You must create one before add firewalls rules.");
            return this;
        }
        try {
            ec2Client.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest.build());
        } catch (Ec2Exception e) {
//            if (e.statusCode().equals("InvalidPermission.Duplicate")) {
//                System.out.println("Rule already exist for ipRange " + ipRangesStr + " on protocol " + protocol);
//            }
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
        CreateVpcResponse result = ec2Client.createVpc(CreateVpcRequest.builder().cidrBlock(ipRange).build());
        CreateTagsRequest.Builder tagNameRequest = CreateTagsRequest.builder().resources(result.vpc().vpcId());
        tagNameRequest.tags(Tag.builder().key("Name").value(name).build());
        ec2Client.createTags(tagNameRequest.build());
        ModifyVpcAttributeRequest requestDNS = ModifyVpcAttributeRequest.builder()
                .enableDnsHostnames(AttributeBooleanValue.builder().value(true).build()).vpcId(result.vpc().vpcId()).build();
        ec2Client.modifyVpcAttribute(requestDNS);
        return result.vpc();
    }

    /**
     * get the VPC with the ip range
     * @param ipRange the range of the VPC
     * @return null if does not exist
     */
    private Vpc getVpcIfExists(String ipRange) {
        DescribeVpcsResponse result = ec2Client.describeVpcs(DescribeVpcsRequest.builder().build());
        for (Vpc vpc : result.vpcs()) {
            if (ipRange.equals(vpc.cidrBlock())) {
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
        CreateSubnetRequest.Builder request = CreateSubnetRequest.builder();
        request.availabilityZone(availabilityZone).cidrBlock(network).vpcId(vpcId);
        CreateSubnetResponse result = ec2Client.createSubnet(request.build());
        subnetsByNetwork.put(network, result.subnet());
        CreateTagsRequest tagNameRequest = CreateTagsRequest.builder().resources(result.subnet().subnetId())
            .tags(Tag.builder().key("Name").value(name).build()).build();
        ec2Client.createTags(tagNameRequest);
        return this;
    }

    /**
     * get the subnetId
     * @param network  the network ip range
     * @return subnetId
     */
    public String getSubnetId(String network) {
        return subnetsByNetwork.get(network).subnetId();
    }

    /**
     * create a clasic load balancer.
     * @param name name of the LB
     * @param subnets the subnets associated with LB
     * @param securityGroupId the security group
     * @param crossBlancingAZ true if will load balancing in all AX
     */
    public void createLoadBalancer(String name, boolean crossBlancingAZ, List<String> subnets, String...securityGroupId) {
        CreateLoadBalancerRequest.Builder request = CreateLoadBalancerRequest.builder().loadBalancerName(name);
        request.subnets(subnets).securityGroups(securityGroupId)
                .tags(Arrays.asList(software.amazon.awssdk.services.elasticloadbalancing.model.Tag.builder().key("Name").value(name).build()));
        request.listeners(listenersOfLb.get(name));
        CreateLoadBalancerResponse result = lbClient.createLoadBalancer(request.build());
        if(crossBlancingAZ) {
           ModifyLoadBalancerAttributesRequest.Builder enableLBAZ = ModifyLoadBalancerAttributesRequest.builder();
           enableLBAZ.loadBalancerName(name).loadBalancerAttributes(LoadBalancerAttributes.builder().crossZoneLoadBalancing(CrossZoneLoadBalancing.builder().enabled(true).build()).build());
           lbClient.modifyLoadBalancerAttributes(enableLBAZ.build());
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
        Listener listener = Listener.builder().instancePort(instancePort).instanceProtocol(instanceProtocol).loadBalancerPort(lbPort).protocol(lbProtocol).build();
        CreateLoadBalancerListenersRequest request = CreateLoadBalancerListenersRequest.builder().listeners(listener).loadBalancerName(lbName).build();
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
        Listener listener = Listener.builder().instancePort(instancePort).instanceProtocol(instanceProtocol).loadBalancerPort(lbPort).protocol(lbProtocol).build();
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
        List<Instance> instances = new ArrayList<>();
        for (String id : instanceIds) {
            instances.add(Instance.builder().instanceId(id).build());
        }
        RegisterInstancesWithLoadBalancerRequest request = RegisterInstancesWithLoadBalancerRequest.builder().loadBalancerName(lbName).instances(instances).build();
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
        CreateInternetGatewayResponse result = ec2Client.createInternetGateway(CreateInternetGatewayRequest.builder().build());
        AttachInternetGatewayRequest request = AttachInternetGatewayRequest.builder().internetGatewayId(result.internetGateway().internetGatewayId()).vpcId(vpcId).build();
        ec2Client.attachInternetGateway(request);
        String igwId = result.internetGateway().internetGatewayId();
        CreateTagsRequest tagNameRequest = CreateTagsRequest.builder().resources(igwId)
                .tags(Tag.builder().key("Name").value(name).build()).build();
        ec2Client.createTags(tagNameRequest);
        return igwId;
    }

    /**
     * add a tag Name with the name value to the new route created in vpcId and witout name.
     * @param name name of the route
     * @param vpcId the vpcid in which the route table reside
     * @return this to be chained
     */
    public EC2Infrastructure addTagNameToNewCreatedRouteTable(String name, String vpcId) {
        DescribeRouteTablesRequest request1 = DescribeRouteTablesRequest.builder().filters(Filter.builder().name("vpc-id").values(vpcId).build()).build();
        List<RouteTable> routes = ec2Client.describeRouteTables(request1).routeTables();
        int index = 0;
        for (int i=0 ; i < routes.size(); i++) {
            for (Tag tag :routes.get(i).tags()) {
                if ("Name".equals(tag.key())) {
                    index = -1;
                    break;
                }
            }
            if( index == 0) {
                index = i;
                break;
            }
        }
        String id = routes.get(index).routeTableId();
        CreateTagsRequest tagNameRequest = CreateTagsRequest.builder().resources(id)
                .tags(Tag.builder().key("Name").value(name).build()).build();
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
        DescribeRouteTablesRequest request1 = DescribeRouteTablesRequest.builder().filters(Filter.builder().name("tag:Name").values(name).build()).build();
        String id = ec2Client.describeRouteTables(request1).routeTables().get(0).routeTableId();
        CreateRouteRequest requestRoute = CreateRouteRequest.builder()
                .routeTableId(id).destinationCidrBlock(address).gatewayId(internetGatewayId).build();
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
        DescribeNatGatewaysRequest natGatewaysRequest = DescribeNatGatewaysRequest.builder().natGatewayIds(natGatewayId).build();
        DescribeNatGatewaysResponse result;
        do {
            result = ec2Client.describeNatGateways(natGatewaysRequest);
            if (result.natGateways().size() == 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {

                }
            }
        } while (!(result.natGateways().size() == 1 && result.natGateways().get(0).state().name().equals(NatGatewayState.AVAILABLE.name())));
        DescribeRouteTablesRequest request1 = DescribeRouteTablesRequest.builder().filters(Filter.builder().name("tag:Name").values(name).build()).build();
        String id = ec2Client.describeRouteTables(request1).routeTables().get(0).routeTableId();
        CreateRouteRequest requestRoute = CreateRouteRequest.builder().routeTableId(id).destinationCidrBlock(address).natGatewayId(natGatewayId).build();
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
        DescribeRouteTablesRequest request1 = DescribeRouteTablesRequest.builder().filters(Filter.builder().name("tag:Name").values(name).build()).build();
        String id = ec2Client.describeRouteTables(request1).routeTables().get(0).routeTableId();
        AssociateRouteTableRequest request = AssociateRouteTableRequest.builder().routeTableId(id).subnetId(subnetId).build();
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
        CreateRouteTableRequest request = CreateRouteTableRequest.builder().vpcId(vpcId).build();
        CreateRouteTableResponse result = ec2Client.createRouteTable(request);
        CreateTagsRequest tagNameRequest = CreateTagsRequest.builder().resources(result.routeTable().routeTableId())
            .tags(Tag.builder().key("Name").value(name).build()).build();
        ec2Client.createTags(tagNameRequest);
        return this;
    }

    /**
     * Create ElasticIpAddress.
     * @param name  the name of the elastic Ip Address.
     * @return the id of the allocated elastic Ip address
     */
    public String createElasticIpAddressOnVpc(String name) {
        AllocateAddressRequest request = AllocateAddressRequest.builder().domain(DomainType.VPC).build();
        String ipId = ec2Client.allocateAddress(request).allocationId();
        CreateTagsRequest tagNameRequest = CreateTagsRequest.builder().resources(ipId)
                .tags(Tag.builder().key("Name").value(name).build()).build();
        ec2Client.createTags(tagNameRequest);
        return ipId;
    }

    /**
     * Careate a network gateway in a subnet using an elastic Ip address.
     * @param name the name of the nat gateway
     * @param subnetId the subnet association
     * @param elasticIpId the elastic Ip address
     * @return the nat gateway id
     */
    public String createNatGateway(String name, String subnetId, String elasticIpId) {
        CreateNatGatewayRequest natRequest = CreateNatGatewayRequest.builder().subnetId(subnetId).allocationId(elasticIpId).build();
        String natGatewayId =  ec2Client.createNatGateway(natRequest).natGateway().natGatewayId();
        CreateTagsRequest tagNameRequest = CreateTagsRequest.builder().resources(natGatewayId)
                .tags(Tag.builder().key("Name").value(name).build()).build();
        ec2Client.createTags(tagNameRequest);
        return natGatewayId;
    }
}
