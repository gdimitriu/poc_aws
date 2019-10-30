package poc_aws.poc_tests.ec2;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class EC2InfrastructureRequests {
    /** ec2 client for amazon instances */
    private AmazonEC2 ec2Client;

    /** load balancer client*/
    private AmazonElasticLoadBalancing lbClient;

    /** the instance id on which we will have the processing */
    private String instanceId;

    /** true if this program is running inside AWS EC2 machine, false otherwise */
    private boolean isRunningInAWSEC2 = false;

    private String loadBalancerName = null;

    public static void main(String...args) {
        EC2InfrastructureRequests infra = new EC2InfrastructureRequests();
        infra.findAndSetInstance();
        infra.describeInstances();
        infra.findLoadBalancer();
    }

    /**
     * constructor to initialize the ec2 client and elastic load balancer client.
     */
    public EC2InfrastructureRequests() {
        AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
        ec2Client = AmazonEC2ClientBuilder.standard().withRegion(Regions.US_EAST_1)
                .withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
        lbClient = AmazonElasticLoadBalancingClientBuilder.standard().withRegion(Regions.US_EAST_1)
                .withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
    }

    /** find and set the instance id  */
    public void findAndSetInstance() {
        //find if we are into AWS instance
        try {
            URL url = new URL("http://169.254.169.254/latest/meta-data/instance-id");
            //should be only one line
            instanceId =  new BufferedReader(new InputStreamReader(url.openConnection().getInputStream())).lines().collect(Collectors.joining(""));
            isRunningInAWSEC2 = true;
        } catch (IOException ex) {
            System.out.println("We are running outside AWS EC2 machine !");
            instanceId = null;
        }
        if (instanceId == null) {
            System.out.println("Please provide the instanceId of the EC2 machine");
            Scanner scanner = new Scanner(System.in);
            instanceId = scanner.nextLine();
        }
    }

    public void describeInstances() {
        DescribeInstancesRequest request = new DescribeInstancesRequest().withInstanceIds(instanceId);
        DescribeInstancesResult response = ec2Client.describeInstances(request);
        for (Reservation reservation : response.getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                System.out.println("instance id =" + instance.getInstanceId());
                System.out.println("imageId = " + instance.getImageId());
                System.out.println("instance type = " +instance.getInstanceType());
                System.out.println("instance architecture = " + instance.getArchitecture());
                System.out.println("IAM arn = "  + instance.getIamInstanceProfile().getArn());
                System.out.println("Key name = " + instance.getKeyName());
                System.out.println("subnet Id  = " + instance.getSubnetId());
                System.out.println("VPC Id  = " + instance.getVpcId());
                System.out.println("Security Groups = " + instance.getSecurityGroups());
                System.out.println("");
            }
        }
    }

    public void findLoadBalancer() {

        List<LoadBalancerDescription> descriptions  = lbClient.describeLoadBalancers(new DescribeLoadBalancersRequest())
                .getLoadBalancerDescriptions();
        for (LoadBalancerDescription description : descriptions) {
            for (com.amazonaws.services.elasticloadbalancing.model.Instance instance : description.getInstances()) {
                if (instance.getInstanceId().equals(instanceId)) {
                    System.out.println("Assigned load balancer is :" + description.getLoadBalancerName());
                    loadBalancerName = description.getLoadBalancerName();
                    return;
                }
            }
        }
    }
}
