# Postchain quick user guide

Postchain is a blockchain framework designed primarily for consortium databases. Business-oriented information about it
can be found on [our site](https://chromaway.com/products/postchain/).

This guide contains short overview of Postchain from technical and conceptual perspectives, 
as well as practical instructions on using it.

This is the only documentation available now. More extensive documentation
will be available in beta release ETA December 2017.

## Overview

Postchain is a modular framework for implementing custom blockchains. Particularly,
it's geared towards _consortium_ blockchains (also known as _permissioned_, _enterprise_,
_private_, _federated_ blockchains, and, sometimes, _distributed ledger technology_).

In a consortium blockchain, typically blocks must be approved (signed) by a majority of consortium
members. This model is also known as _proof-of-authority_, to contrast it with _proof-of-work_ and _proof-of-stake_.

Postchain has modular architecture, where different parts (such as consensus, transaction format,
crypto system) can be customized independently of each other.

The main feature which differentiates Postchain from similar system is that it integrates with
SQL databases in a very deep way: all blockchain data is stored in an SQL database, transaction
logic can be defined in terms of SQL code (particularly, stored procedures). However we should note
that Postchain uses SQL as a black box: it is not a database plugin and it works with databases
such as PostgreSQL as is, without any special configuration or modification.

### Tech stack

 * Programming languages: Kotlin and Java, SQL
 * SQL database: currently tested only with PostgreSQL, more databases will be supported 
   in future releases
 * Operating systems: anything which supports Java 8; tested on Linux and Mac OS X
 * Cryptosystem: SECP256k1 and SHA256 by default, but it is customizable

### Programming model

Custom blockchain can be programmed in any JVM language which can define classes (Java, Kotlin, ...)
and SQL.

In future versions Postchain will allow:

 * SQL only modules (i.e. no Java programming necessary)
 * declarative programming
 
Postchain currently comes with no user-defined smart contract capabilities, i.e. all
smart contract must be defined by a programmer. Future versions will include this capability.

### Typical scenario

In this section we will provide an example of a typical implementation and deployment scenario.

Suppose that 4 independent enterprises wish to maintain a common database in a secure way.
They decide to use Postchain. Each of them will run a validator (authority) node, and each block will have to have
at least 3 signatures. Here's a list of steps they need to launch it:

1. Define blockchain code in Java and SQL. Compile it into a `jar` file.
2. Generate keypairs for each node.
3. Share public keys and define common configuration.
4. Implement client software. E.g. it can be web-based, implemented in JavaScript using Postchain JS SDK.
5. Launch their nodes and configure clients to access the nodes.
6. Clients will create transactions using Postchain client library methods. Transactions are signed on the client
   side and submitted to Postchain node.
7. Postchain nodes will run consensus algorithm, create blocks and apply transactions, thus modifying state of 
   the database. Direct modification of Postchain-managed database is prohibited, all changes must be done 
   using signed transactions.
8. Clients can access database state either by using Postchain SDK, or by reading data from 
   SQL database directly. Reading data directly is allowed.
   
Postchain does not impose any hard requirements on SQL schema, it is possible to define it in the same
way as for non-blockchain applications. But application must use Postchain transactions to perform database
changes, it should not try to insert or update data directly.

## Architecture

Postchain consists of following components:

 * Core: Defines common interfaces which allow different modules to interoperate with each other.
 * Base: Defines base classes which are geared towards enterprise blockchains. They can either be used as is, or 
   serve as a base classes for customization.
 * GTX: Defines a generic transaction format. It is a general-purpose format with several useful features, such
   as native multisignature support. It is optional (it is easy to define custom format), but is recommended.
   GTX also offers a way to define modules with blockchain logic which can work together with each other.
 * API: REST API for submitting transactions and retreiving data.
 * EBFT: Consensus protocol, based on PBFT. (Replaceable.)
 * Client library/SDK: Currently only JavaScript SDK is available, in future we will offer SDK for JVM.
   Client library contains functions for composing and parsing transactions, interacting with Postchain node using
   client API.

Postchain GTX makes it possible to compose application from modules. Using pre-made modules can help to reduce
implementation time.

Currently ChromaWay offers only two GTX modules (which are bundled with Postchain code):

 * Standard -- implements time lock and transaction expiration feature.
 * FT -- Flexible Tokens, provides a simple way to implement tokens. (Can be used in applications like payments,
   loyalty points, crowdfunding, securities trade, ...)
   
### GTX

GTX transaction format has following features:

 * Format is based on ASN.1 DER serialization (standardized by ITU-T, ISO, IEC)
 * Has native support for multi-signature
 * Has native support for atomic transactions
 
GTX transaction is consists of one or more operations, and each operation is defined by its name and list of arguments.
E.g. one transaction might encode two operations:

     issue(<Alice account ID>, 1000, USD)
     transfer(<Alice account ID>, <Bob account ID>, 100, USD)

This looks similar to making function calls, so GTX operations can be understood as a kind of RPC, where client
submits calls (operations) to be performed on server (network of Postchain nodes). GTX transaction is a batch
of such operations signed by clients which wish to perform them. Usually operations update database, but they might 
only perform checks. GTX transaction is atomic: either all operation succeed, or it fails as a whole.

#### GTX modules

Postchain provides a convenient way to define GTX operations and organize them into modules.
Multiple modules can be composed together into a composite module.

Besides operations, modules also define queries which can later be performed using client API.

#### GTX client SDK

GTX client SDK for JavaScript is provided in `postchain-client` npm package. Example of use is provided in README file
[here](https://bitbucket.org/chromawallet/postchain-client).

#### FT

FT comes with its own client library for JS which also includes examples, [see here](https://bitbucket.org/chromawallet/ft-client).

## Implementing blockchain logic

In most typical case, to implement custom blockchain you only need to implement GTX operations and module, which 
can then be plugged into the rest of the system.

This can be done in three steps:

1. Define schema in an SQL file. It should defined tables and stored procedures needed by the application.
2. Define GTX operations by subclassing `net.postchain.gtx.GTXOperation`.
3. Define GTX module which maps names to operations, by subclassing `net.postchain.gtx.GTXModule` interface, or
   subclassing `net.postchain.gtx.SimpleGTXModule`
4. Optionaly, if GTX module needs paramters, one can define GTX module factory (`net.postchain.gtx.GTXModuleFactory`).

[GTXTestOp](https://bitbucket.org/chromawallet/postchain2/src/fcd30c7e6f3820f98235a474ce5fd1f89f564311/src/main/kotlin/net/postchain/configurations/singlegtxnode.kt?at=master&fileviewer=file-view-default#singlegtxnode.kt-40)
is an example of a simple GTX operation which simply runs `INSERT` query with user-provided string.
It is included into [GTXTestModule](https://bitbucket.org/chromawallet/postchain2/src/fcd30c7e6f3820f98235a474ce5fd1f89f564311/src/main/kotlin/net/postchain/configurations/singlegtxnode.kt?at=master&fileviewer=file-view-default#singlegtxnode.kt-55)
which includes schema in an inline string and also includes an example of a query implementation.

More complex example can be found in [the turorial](https://bitbucket.org/chromawallet/postchain2-examples/src/276b363658b88937b621659185aecf58709537cc/tutorial-etk.md?at=master&fileviewer=file-view-default).

If it is necessary to use custom transaction format, it can be done in following way:

1. Implement `net.postchain.core.Transaction` interface to define transaction serialization format and semantics.
2. Implement transaction factory (`net.postchain.core.TransactionFactory`), usually it just
   calls transaction constructor.
3. Implement `BlockchhainConfiguration`. Simplest way is to subclass `net.postchain.BaseBlockchainConfiguration`
   and override `getTransactionFactory` method.
4. Implement `BlockchainConfigurationFactory`.

Steps 2-4 are usually trivial.

## Running Postchain nodes

To set up Postchain network, following steps are needed:

1. Prepare `jar` files which implement blockchain application. 
2. Define common node configuration, which includes the following:
     * blockchain identifier (blockchainrid)
     * blockchain configuration and module classes
     * module parameters (if needed)
     * public keys used by nodes 
     * node network addresses
3. Define per-node configuration which includes
     * database URL (host, username, password), schema
     * client API port
     * private key
4. Configure database (usually creating database and user is enough)
5. Run Postchain nodes with given configuration on each machine

Example of common configuration is provided [here](https://bitbucket.org/chromawallet/postchain2/src/fcd30c7e6f3820f98235a474ce5fd1f89f564311/src/main/resources/config/common.properties?at=master&fileviewer=file-view-default).

Example of node configuration is provided [here](https://bitbucket.org/chromawallet/postchain2/src/fcd30c7e6f3820f98235a474ce5fd1f89f564311/src/main/resources/config/config.0.properties?at=master&fileviewer=file-view-default). 
(Note that it includes common.properties).

At least 4 nodes are needed for fault-tolerant configuration, but it's possible to run network with just 3 nodes.

For development purposes it might be convenient to run Postchain in a single-node mode, in this case 
it can be configured [like this](https://bitbucket.org/chromawallet/postchain2/src/fcd30c7e6f3820f98235a474ce5fd1f89f564311/src/main/resources/config/single.properties?at=master&fileviewer=file-view-default)

Postchain node can be launched like this:

    java -jar postchain.jar -i 2 -c node2.properties
    
Parameter `-i` specifies node index starting from 0, parameter `-c` specifies node's configuration file.

Here's an example of module parameters used for FT module:
    
    blockchain.1.gtx.modules=net.postchain.modules.ft.BaseFTModuleFactory
    blockchain.1.gtx.ft.assets=USD
    blockchain.1.gtx.ft.asset.USD.issuers=03f811d3e806e6d093a4bcce49c145ba78f9a4b2fbd167753ecab2a13530b081f8
    blockchain.1.gtx.ft.openRegistration=true
