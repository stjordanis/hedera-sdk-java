package com.hedera.hashgraph.sdk.examples;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.account.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.account.AccountInfo;
import com.hedera.hashgraph.sdk.account.AccountInfoQuery;
import com.hedera.hashgraph.sdk.crypto.KeyList;
import com.hedera.hashgraph.sdk.crypto.PublicKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;

import java.util.HashMap;
import java.util.Objects;

public final class NewExample {

    // see `.env.sample` in the repository root for how to specify these values
    // or set environment variables with the same names
    private static final AccountId OPERATOR_ID = AccountId.fromString("0.0.1035");
    private static final Ed25519PrivateKey OPERATOR_KEY = Ed25519PrivateKey.fromString("302e020100300506032b6570042204207ce25f7ac7a4fa7284efa8453f153922e16ede6004c36778d3870c93d5dfbee5");

    private NewExample() { }

    public static void main(String[] args) throws HederaStatusException {
        // `Client.forMainnet()` is provided for connecting to Hedera mainnet
        Client client = Client.forTestnet();

        final HashMap<AccountId, String> nodes = new HashMap<>();
        nodes.put(new AccountId(7), "5.testnet.hedera.com:50211");
        nodes.put(new AccountId(8), "6.testnet.hedera.com:50211");
        nodes.put(new AccountId(9), "7.testnet.hedera.com:50211");
        nodes.put(new AccountId(10), "8.testnet.hedera.com:50211");

        client.replaceNodes(nodes);

        // Defaults the operator account ID and key such that all generated transactions will be paid for
        // by this account and be signed by this key
        client.setOperator(OPERATOR_ID, OPERATOR_KEY);

        AccountInfo info = new AccountInfoQuery()
            // The only _required_ property here is `key`
            .setAccountId(AccountId.fromString("0.0.68331"))
            .execute(client);

        System.out.println(info.key);
//
//        KeyList keys = new KeyList().addAll(info.key);
//
//        System.out.println(PublicKey.fromProtoKey(keys.toKeyProto().getKeyList().getKeys(0).getKeyList().getKeys(0).getKeyList().getKeys(0)));
    }
}
