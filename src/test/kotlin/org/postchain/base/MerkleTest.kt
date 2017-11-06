// Copyright (c) 2017 ChromaWay Inc. See README for license information.

package org.postchain.base

import org.junit.Test

import org.junit.Assert.*
import org.postchain.base.computeMerkleRootHash
import org.junit.Assert

val cryptoSystem = SECP256K1CryptoSystem()

class MerkleTest {
    fun stringToHash(string: String): ByteArray {
        return cryptoSystem.digest(string.toByteArray())
    }

    fun hashList(stringList: Array<String>): Array<ByteArray> {
        return stringList.map({stringToHash(it)}).toTypedArray()
    }

    fun merkleRoot(stringList: Array<String>): ByteArray {
        return computeMerkleRootHash(cryptoSystem, hashList(stringList))
    }

    fun checkDifferent(list1: Array<String>, list2: Array<String>) {
        val root1 = merkleRoot(list1)
        val root2 = merkleRoot(list2)
        assertByteArrayNotEqual(root1, root2)
    }
    val a = arrayOf("a")
    val aa = arrayOf("a", "a")
    val abcde = arrayOf("a", "b", "c", "d", "e")
    val abcdee= arrayOf("a", "b", "c", "d", "e", "e")
    val abcdef = arrayOf("a", "b", "c", "d", "e", "f")
    val abcdefef = arrayOf("a", "b", "c", "d", "e", "f", "e", "f")

    fun assertByteArrayEqual(expected: ByteArray, actual: ByteArray) {
        assertTrue(expected.contentEquals(actual))
    }

    fun assertByteArrayNotEqual(val1: ByteArray, val2: ByteArray) {
        assertFalse(val1.contentEquals(val2))
    }

    @Test
    fun testMerkleRootOfEmptyListIs32Zeroes() {
        assertByteArrayEqual(kotlin.ByteArray(32), merkleRoot(emptyArray()))
    }

    @Test
    fun testMerkleRootOfSingleElement() {
        assertByteArrayEqual(stringToHash("a"), merkleRoot(a))
    }

    @Test
    fun testMerkleRootNoCollisions() {
        checkDifferent(a, aa)
        checkDifferent(abcde, abcdee)
        checkDifferent(abcdef, abcdefef)
    }

    /* TESTS FROM JS IMPL FOR INSPIRATION

    it('merkle proof throws on empty list', () => {
        assert.throws(() => {util.merklePath([], stringToHash('a'))}, Error);
    })

    it('merkle proof throws if tx not exist in list', () => {
        assert.throws(() => {util.merklePath(abcdee, stringToHash('f'))}, Error);
    })

    it('merkle proof structure', () => {
        var path = util.merklePath(hashList(abcde), stringToHash('e'));
        assert.equal(path.length, 3);
        assert.equal(path[0].side, 1); // right
        assert.equal(path[1].side, 1); // right
        assert.equal(path[2].side, 0); // left
        assert.ok(util.validateMerklePath(path, stringToHash('e'), merkleRoot(abcde)));
        assert.ok(!util.validateMerklePath(path, stringToHash('c'), merkleRoot(abcde)));
    })

    function testPath(stringList, stringToProve) {
        var path = util.merklePath(hashList(stringList), stringToHash(stringToProve));
       // console.log(path);
        assert.ok(util.validateMerklePath(path, stringToHash(stringToProve), merkleRoot(stringList)),
            `validation failed for txs ${stringList} and tx ${stringToProve}`);
    }


    this.timeout(400000);
    it ('merkle path to size 20', () => {
        var txs = []
        for (var i = 1; i < 20; i++) {
            txs.push(''+i);
            for (var j = 1; j <= i; j++) {
                testPath(txs, ''+j);
            }
        }
    })

    it ('dummy', () => {
        testPath(['1', '2', '3'], '1');
    });

    it ('merkle path negative test wrong side', () => {
        var path = util.merklePath(hashList(abcde), stringToHash('d'));
        path[1].side = 1; // Flip side of one path component
        assert.ok(!util.validateMerklePath(path, stringToHash('d'), merkleRoot(abcde)));
    });

    it ('merkle path negative test wrong hash', () => {
        var path = util.merklePath(hashList(abcde), stringToHash('d'));
        path[2].hash = stringToHash('invalid'); // wrong hash
        assert.ok(!util.validateMerklePath(path, stringToHash('d'), merkleRoot(abcde)));
    });
     */
}