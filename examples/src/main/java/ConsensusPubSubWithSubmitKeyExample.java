import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeoutException;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.HederaPreCheckStatusException;
import com.hedera.hashgraph.sdk.HederaReceiptStatusException;
import com.hedera.hashgraph.sdk.MessageSubmitTransaction;
import com.hedera.hashgraph.sdk.MirrorClient;
import com.hedera.hashgraph.sdk.MirrorTopicQuery;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TopicCreateTransaction;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TransactionId;

import org.threeten.bp.Instant;

/**
 * An example of an HCS topic that utilizes a submitKey to limit who can submit messages on the topic.
 * <p>
 * Creates a new HCS topic with a single ED25519 submitKey.
 * Subscribes to the topic (no key required).
 * Publishes a number of messages to the topic signed by the submitKey.
 */
public class ConsensusPubSubWithSubmitKeyExample {
    private Client hapiClient;
    private MirrorClient mirrorNodeClient;

    private final int messagesToPublish;
    private final int millisBetweenMessages;

    private TopicId topicId;
    private PrivateKey submitKey;

    public ConsensusPubSubWithSubmitKeyExample(int messagesToPublish, int millisBetweenMessages) {
        this.messagesToPublish = messagesToPublish;
        this.millisBetweenMessages = millisBetweenMessages;
        setupHapiClient();
        setupMirrorNodeClient();
    }

    public static void main(String[] args) throws TimeoutException, InterruptedException, HederaPreCheckStatusException, HederaReceiptStatusException {
        new ConsensusPubSubWithSubmitKeyExample(5, 2000).execute();
    }

    public void execute() throws TimeoutException, InterruptedException, HederaPreCheckStatusException, HederaReceiptStatusException {
        createTopicWithSubmitKey();

        subscribeToTopic();

        publishMessagesToTopic();
    }

    private void setupHapiClient() {
        // Transaction payer's account ID and ED25519 private key.
        AccountId payerId = AccountId.fromString(Objects.requireNonNull(System.getenv("OPERATOR_ID")));
        PrivateKey payerPrivateKey =
            PrivateKey.fromString(Objects.requireNonNull(System.getenv("OPERATOR_KEY")));

        // Interface used to publish messages on the HCS topic.
        hapiClient = Client.forTestnet();

        // Defaults the operator account ID and key such that all generated transactions will be paid for by this
        // account and be signed by this key
        hapiClient.setOperator(payerId, payerPrivateKey);
    }

    private void setupMirrorNodeClient() {
        // Interface used to subscribe to messages on the HCS topic.
        mirrorNodeClient = new MirrorClient(Objects.requireNonNull(System.getenv("MIRROR_NODE_ADDRESS")));
    }

    /**
     * Generate a brand new ED25519 key pair.
     * <p>
     * Create a new topic with that key as the topic's submitKey; required to sign all future
     * ConsensusMessageSubmitTransactions for that topic.
     */
    private void createTopicWithSubmitKey() throws TimeoutException, HederaPreCheckStatusException, HederaReceiptStatusException {
        // Generate a Ed25519 private, public key pair
        submitKey = PrivateKey.generate();
        PublicKey submitPublicKey = submitKey.getPublicKey();

        TransactionId transactionId = new TopicCreateTransaction()
            .setTopicMemo("HCS topic with submit key")
            .setSubmitKey(submitPublicKey)
            .execute(hapiClient);


        topicId = Objects.requireNonNull(transactionId.getReceipt(hapiClient).topicId);
        System.out.println("Created new topic " + topicId + " with ED25519 submitKey of " + submitKey);
    }

    /**
     * Subscribe to messages on the topic, printing out the received message and metadata as it is published by the
     * Hedera mirror node.
     */
    private void subscribeToTopic() {
        new MirrorTopicQuery()
            .setTopicId(topicId)
            .setStartTime(Instant.ofEpochSecond(0))
            .subscribe(mirrorNodeClient, System.out::println,
                // On gRPC error, print the stack trace
                Throwable::printStackTrace);
    }

    /**
     * Publish a list of messages to a topic, signing each transaction with the topic's submitKey.
     */
    private void publishMessagesToTopic() throws TimeoutException, InterruptedException, HederaPreCheckStatusException, HederaReceiptStatusException {
        Random r = new Random();
        for (int i = 0; i < messagesToPublish; i++) {
            String message = "random message " + r.nextLong();

            System.out.println("Publishing message: " + message);

            new MessageSubmitTransaction()
                .setTopicId(topicId)
                .setMessage(message)
                .build(hapiClient)

                // The transaction is automatically signed by the payer.
                // Due to the topic having a submitKey requirement, additionally sign the transaction with that key.
                .sign(submitKey)

                .execute(hapiClient)
                .getReceipt(hapiClient);

            Thread.sleep(millisBetweenMessages);
        }

        Thread.sleep(10000);
    }
}
