package com.chromaway.postchain.integrationtest;

import com.chromaway.postchain.base.BaseBlockchainConfiguration;
import com.chromaway.postchain.base.BaseManagedBlockBuilder;
import com.chromaway.postchain.base.BasePeerCommConfiguration;
import com.chromaway.postchain.base.BaseStorage;
import com.chromaway.postchain.base.ManagedBlockBuilder;
import com.chromaway.postchain.base.PeerCommConfiguration;
import com.chromaway.postchain.base.PeerInfo;
import com.chromaway.postchain.base.SECP256K1CryptoSystem;
import com.chromaway.postchain.base.Storage;
import com.chromaway.postchain.base.TransactionQueue;
import com.chromaway.postchain.core.*;
import com.chromaway.postchain.ebft.BaseBlockDatabase;
import com.chromaway.postchain.ebft.BaseBlockchainEngine;
import com.chromaway.postchain.ebft.BlockDatabase;
import com.chromaway.postchain.ebft.BlockchainEngine;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.sql.Connection;
import java.util.Properties;

import static com.chromaway.postchain.base.UtilKt.computeMerkleRootHash;

public class SetupTest {

    private static class TestBlockchainConfigurationFactory implements BlockchainConfigurationFactory {

        @NotNull
        public BlockchainConfiguration makeBlockchainConfiguration(long chainID, @NotNull Properties properties) {

            return new BaseBlockchainConfiguration(chainID, properties);
        }
    }

    @Test
    public void setupSystem() {
        /*
1. Manager reads JSON and finds BlockchainConfigurationFactory class name.
2. Manager instantiates a class which implements BlockchainConfigurationFactory interface, and feeds it JSON data.
3. BlockchainConfigurationFactory creates BlockchainConfiguration object.
4. BlockchainConfiguration acts as a block factory and creates a transaction factory, presumably passing it configuration data in some form.
5. TransactionFactory will create Transaction objects according to configuration, possibly also passing it the configuration data.
6. Transaction object can perform its duties according to the configuration it received, perhaps creating sub-objects called transactors and passing them the configuration.
 */
        BlockchainConfigurationFactory factory = new TestBlockchainConfigurationFactory();
//
        BlockchainConfiguration blockchainConfiguration = factory.makeBlockchainConfiguration(1, new Properties());


        TransactionFactory transactionFactory = blockchainConfiguration.getTransactionFactory();

        Transaction transaction = transactionFactory.decodeTransaction(new byte[222]);

        PeerCommConfiguration peerCommConf = new BasePeerCommConfiguration(new PeerInfo[1], 0);

        Storage storage = new BaseStorage();

        TransactionQueue txQueue = new TransactionQueue() {
            @NotNull
            @Override
            public Transaction[] getTransactions() {
                return new Transaction[0];
            }
        };

        BlockchainEngine engine = new BaseBlockchainEngine(blockchainConfiguration, peerCommConf,
                storage, 1, new SECP256K1CryptoSystem(), txQueue);

        BlockBuilder blockBuilder = engine.buildBlock();
//        blockBuilder.getBlockWitnessBuilder().

    }
}