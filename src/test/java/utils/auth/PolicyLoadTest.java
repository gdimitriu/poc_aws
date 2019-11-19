package utils.auth;

import org.junit.Test;
import poc_aws.utils.auth.Policy;

public class PolicyLoadTest {
    @Test
    public void loadAndTestJsonPolicy() {
        String jsonPolicy;
        Policy policy = new Policy().withSid("topic-subscription-arn:aws:sns:us-west-2:599109622955:myTopic")
                .withEffect("Allow").withPrincipal("AWS","*").withAction("SQS:SendMessage")
                .withResource("arn:aws:sqs:us-west-2:599109622955:myQueue")
                .withCondition("ArnLike", "aws:SourceArn", "arn:aws:sns:us-west-2:599109622955:myTopic");
        jsonPolicy = policy.toJson();
        System.out.println(jsonPolicy);
    }

    @Test
    public void EC2_With_full_S3() {
        String jsonPolicy;
        Policy policy = new Policy("2012-10-17").withEffect("Allow").withAction("sts:AssumeRole").withPrincipal("Service", "ec2.amazonaws.com");
        jsonPolicy = policy.toJson();
        System.out.println(jsonPolicy);
    }

    public static void main(String...args) {
        new PolicyLoadTest().loadAndTestJsonPolicy();
        new PolicyLoadTest().EC2_With_full_S3();
    }
}
