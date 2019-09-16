package poc_aws.poc_tests.ec2;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Ec2ClientOperations {
    /** ec2 client  */
    private AmazonEC2 ec2Client;
    /** security group for EC2 */
    private CreateSecurityGroupResult sgResult;

    private static final String KEY_PAIR_NAME="javaAutoKeyPair";

    private static final String SECURITY_GROUP_NAME = "MySecurityGroup";

    private KeyPair keyPair;
    public static void main(String...args) {
        Ec2ClientOperations client = new Ec2ClientOperations();
        client.createSecurityGroup(SECURITY_GROUP_NAME, "My secutity group");
        client.addFirewallRule(SECURITY_GROUP_NAME, Arrays.asList(new String[]{"0.0.0.0/0"}),"tcp",22,22);
        client.createKeyPair(KEY_PAIR_NAME, true);
        try {
            client.savePEMToFile("d:\\" + KEY_PAIR_NAME +".pem");
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Reserved instances!");
        client.printAllInstances();
        System.out.println("Try to start new instance!");
        client.runInstance("ami-00eb20669e0990cb4", InstanceType.T2Micro, 1, 1, KEY_PAIR_NAME, SECURITY_GROUP_NAME);
        System.out.println("Launched successfully the instance!");
        System.out.println("Reserved instances!");
        client.printAllInstances();
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
        try {
            DescribeSecurityGroupsResult result = ec2Client.describeSecurityGroups(request);
            return result.getSecurityGroups().stream().map(SecurityGroup::getGroupName).anyMatch(str -> str.equals(sgName));
        } catch (AmazonEC2Exception e) {
            return false;
        }
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

    /**
     * create the key pair for ec2 instances
     * @param keyName the name of the keypair
     * @param  force if true the existing key will be deleted.
     */
    public void createKeyPair(String keyName, boolean force) {
        if (isKeyPairCreated(keyName)) {
            System.out.println("The existing keyPair " + keyName + " will be deleted and recreated!");
            DeleteKeyPairRequest request = new DeleteKeyPairRequest().withKeyName(keyName);
            try {
                ec2Client.deleteKeyPair(request);
            } catch (AmazonEC2Exception e) {
                e.printStackTrace();
                return;
            }
       }
        CreateKeyPairRequest request = new CreateKeyPairRequest();
        request.withKeyName(keyName);
        CreateKeyPairResult result = ec2Client.createKeyPair(request);
        keyPair = result.getKeyPair();
    }

    /**
     * This will check if a KeyPair with a name exists.
     * @param keyName the name of the key pair.
     * @return true if the key already exists.
     */
    public boolean isKeyPairCreated(String keyName) {
        DescribeKeyPairsResult result = ec2Client.describeKeyPairs();
        return result.getKeyPairs().stream().map(key -> key.getKeyName()).anyMatch(str -> str.equals(keyName));
    }

    /**
     * This will save the PEM from KeyPair to a file.
     * @param fileName the full path of the file ot save.
     * @throws IOException in case of error
     */
    public void savePEMToFile(String fileName) throws IOException{
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write(keyPair.getKeyMaterial());
        }
    }

    /**
     * Run a specific instance
     * @param osType the type of the image (OS)
     * @param type type of the instance (T2.micro for the Siplilearn account)
     * @param min minimum number of instances
     * @param max maximum number of instances
     * @param keyName the keyPair to connect to instance
     * @param securityGroup the security group assigned to this instance
     */
    public void runInstance(String osType, InstanceType type, int min, int max, String keyName, String securityGroup) {
        RunInstancesRequest request = new RunInstancesRequest();
        request.withImageId(osType).withInstanceType(type).withMinCount(min).withMaxCount(max).withKeyName(keyName).withSecurityGroups(securityGroup);
        RunInstancesResult result = ec2Client.runInstances(request);
    }

    /**
     * print all running instances, this is used for debug mode.
     */
    private void printAllInstances() {
        String nextToken = null;
        do {
            DescribeInstancesRequest request = new DescribeInstancesRequest().withMaxResults(6).withNextToken(nextToken);
            DescribeInstancesResult response = ec2Client.describeInstances(request);
            for (Reservation reservation : response.getReservations()) {
                for (Instance instance : reservation.getInstances()) {
                    System.out.printf(
                            "Found reservation with id %s, " +
                                    "AMI %s, " +
                                    "type %s, " +
                                    "state %s " +
                                    "and monitoring state %s",
                            instance.getInstanceId(),
                            instance.getImageId(),
                            instance.getInstanceType(),
                            instance.getState().getName(),
                            instance.getMonitoring().getState());
                    System.out.println("");
                }
            }
            nextToken = response.getNextToken();
        } while (nextToken != null);
    }
}
