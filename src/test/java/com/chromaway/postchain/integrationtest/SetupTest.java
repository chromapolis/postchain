package com.chromaway.postchain.integrationtest;

import com.chromaway.postchain.base.BaseBlockchainConfiguration;
import com.chromaway.postchain.core.*;
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

        //BlockBuilder blockBuilder = blockchainConfiguration.makeBlockBuilder();

        TransactionFactory transactionFactory = blockchainConfiguration.getTransactionFactory();

        Transaction transaction = transactionFactory.decodeTransaction(new byte[222]);



    }
}