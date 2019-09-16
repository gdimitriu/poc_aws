package poc_aws.poc_tests.ec2;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Ec2ClientOperations {
    /** ec2 client  */
    private AmazonEC2 ec2Client;
    /** security group for EC2 */
    private CreateSecurityGroupResult sgResult;

    public static void main(String...args) {
        Ec2ClientOperations client = new Ec2ClientOperations();
        client.createSecurityGroup("MySecurityGroup", "My secutity group");
        client.addFirewallRule("MySecurityGroup", Arrays.asList(new String[]{"0.0.0.0/0"}),"tcp",22,22);
    }

    public Ec2ClientOperations() {
        AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
        ec2Client = AmazonEC2ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials)).withRegion(Regions.US_EAST_1).build();
    }

    /**
     * Check if the security group with the specified name exists.
     * @param sgName
     * @return true if the security group exists
     */
    public boolean isSecurityGroupCreated(String sgName) {
        DescribeSecurityGroupsRequest request = new
                DescribeSecurityGroupsRequest().withGroupNames(sgName);

        DescribeSecurityGroupsResult result = ec2Client.describeSecurityGroups(request);
        return result.getSecurityGroups().stream().map(SecurityGroup::getGroupName).anyMatch(str -> str.equals(sgName));
    }

    /**
     * Create a security group
     * @param name
     * @param description
     * @return true if it created the sg false otherwise
     */
    public boolean createSecurityGroup(String name, String description) {
        if(isSecurityGroupCreated(name)) {
            System.out.println("SG with name=" + name + " is already created we will just add securities");
            return false;
        }
        CreateSecurityGroupRequest sgRequest = new CreateSecurityGroupRequest();
        sgRequest.withGroupName(name).withDescription(description);
        sgResult = ec2Client.createSecurityGroup(sgRequest);
        return true;
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
    public boolean addFirewallRule(String securityGroupName, List<String> ipRangesStr, String protocol, int fromPort, int toPort) {
        IpPermission ipPermission = new IpPermission();
        List<IpRange> ipRanges = new ArrayList<>();
        ipRangesStr.forEach(a -> ipRanges.add(new IpRange().withCidrIp(a)));
        ipPermission.withIpv4Ranges(ipRanges).withIpProtocol(protocol).withFromPort(fromPort).withToPort(toPort);
        AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest = new AuthorizeSecurityGroupIngressRequest();
        authorizeSecurityGroupIngressRequest.withGroupName(securityGroupName).withIpPermissions(ipPermission);
        try {
            ec2Client.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest);
            return true;
        } catch (AmazonEC2Exception e) {
            if (e.getErrorCode().equals("InvalidPermission.Duplicate")) {
                System.out.println("Rule already exist for ipRange " + ipRangesStr + " on protocol " + protocol);
                return true;
            }
            System.out.println(e.getLocalizedMessage());
        }
        return false;
    }
}
