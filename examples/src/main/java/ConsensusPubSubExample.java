import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.HederaPreCheckStatusException;
import com.hedera.hashgraph.sdk.HederaReceiptStatusException;
import com.hedera.hashgraph.sdk.MessageSubmitTransaction;
import com.hedera.hashgraph.sdk.MirrorClient;
import com.hedera.hashgraph.sdk.MirrorTopicQuery;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.TopicCreateTransaction;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;

class ConsensusPubSubExample {
    private static final AccountId OPERATOR_ID = AccountId.fromString(Objects.requireNonNull(System.getenv("OPERATOR_ID")));
    private static final PrivateKey OPERATOR_KEY = PrivateKey.fromString(Objects.requireNonNull(System.getenv("OPERATOR_KEY")));
    @SuppressWarnings("FieldMissingNullable")
    private static final String MIRROR_NODE_ADDRESS = Objects.requireNonNull(System.getenv("MIRROR_NODE_ADDRESS"));

    private ConsensusPubSubExample() {
    }

    @SuppressWarnings("NullableDereference")
    public static void main(String[] args) throws TimeoutException, InterruptedException, HederaPreCheckStatusException, HederaReceiptStatusException {
        MirrorClient mirrorClient = new MirrorClient(MIRROR_NODE_ADDRESS);

        // `Client.forMainnet()` is provided for connecting to Hedera mainnet
        Client client = Client.forTestnet();

        // Defaults the operator account ID and key such that all generated transactions will be paid for
        // by this account and be signed by this key
        client.setOperator(OPERATOR_ID, OPERATOR_KEY);

        TransactionId transactionId = new TopicCreateTransaction()
            .execute(client);

        TransactionReceipt transactionReceipt = transactionId.getReceipt(client);

        TopicId topicId = Objects.requireNonNull(transactionReceipt.topicId);

        System.out.println("New topic created: " + topicId);

        new MirrorTopicQuery()
            .setTopicId(topicId)
            .subscribe(mirrorClient, resp -> {
                    String messageAsString = new String(resp.message, StandardCharsets.UTF_8);

                    System.out.println(resp.consensusTimestamp + " received topic message: " + messageAsString);
                },
                // On gRPC error, print the stack trace
                Throwable::printStackTrace);

        // keep the main thread from exiting because the listeners run on daemon threads
        // noinspection InfiniteLoopStatement
        for (int i = 0; ; i++) {
            new MessageSubmitTransaction()
                .setTopicId(topicId)
                .setMessage("hello, HCS! " + i)
                .execute(client)
                .getReceipt(client);

            Thread.sleep(2500);
        }
    }
}
