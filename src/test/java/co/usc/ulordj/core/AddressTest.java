/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.usc.ulordj.core;

import co.usc.ulordj.params.MainNetParams;
import co.usc.ulordj.params.Networks;
import co.usc.ulordj.params.RegTestParams;
import co.usc.ulordj.params.TestNet3Params;
import co.usc.ulordj.script.ScriptBuilder;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.interfaces.ECKey;
import java.util.Arrays;

import static co.usc.ulordj.core.Utils.HEX;
import static co.usc.ulordj.core.Utils.sha256hash160;
import static org.junit.Assert.*;

public class AddressTest {
    //static final NetworkParameters regtestParams = RegTestParams.get();
    static final NetworkParameters testParams = TestNet3Params.get();
    static final NetworkParameters mainParams = MainNetParams.get();

    @Test
    public void testPublicKeyToAddress() throws Exception {
        UldECKey publicKey = UldECKey.fromPublicOnly(Hex.decode("03f0ed482997fd16e2b4aed02fe3a386749052fd44d00a75a221e11eac7348d0b6"));
        byte[] addressHash = sha256hash160(publicKey.getPubKey());
        Address address = new Address(testParams, testParams.getAddressHeader(), addressHash);
        assertEquals("ufGHmxvSDsXMKUm23a76JrrjvQqhpfL5E3", address.toString());
    }

    @Test
    public void testJavaSerialization() throws Exception {
        Address testAddress = Address.fromBase58(testParams, "ubwJhHMSVPVCHr3PNPgieNYpWvuWG5XvcQ");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        new ObjectOutputStream(os).writeObject(testAddress);
        VersionedChecksummedBytes testAddressCopy = (VersionedChecksummedBytes) new ObjectInputStream(
                new ByteArrayInputStream(os.toByteArray())).readObject();
        assertEquals(testAddress, testAddressCopy);

        Address mainAddress = Address.fromBase58(mainParams, "UNAiJ2pgTzRR4xvQYKzanEtscc3fybcJgi");
        os = new ByteArrayOutputStream();
        new ObjectOutputStream(os).writeObject(mainAddress);
        VersionedChecksummedBytes mainAddressCopy = (VersionedChecksummedBytes) new ObjectInputStream(
                new ByteArrayInputStream(os.toByteArray())).readObject();
        assertEquals(mainAddress, mainAddressCopy);
    }

    @Test
    public void stringification() throws Exception {
        // Test a testnet address.
        Address a = new Address(testParams, HEX.decode("ba74df8342800b07e231cc33d956aa91fde30675"));
        assertEquals("ubwJhHMSVPVCHr3PNPgieNYpWvuWG5XvcQ", a.toString());
        assertFalse(a.isP2SHAddress());

        Address b = new Address(mainParams, HEX.decode("0205d52c7ed8131d126cf4335bc39abf631a3762"));
        assertEquals("UNAiJ2pgTzRR4xvQYKzanEtscc3fybcJgi", b.toString());
        assertFalse(b.isP2SHAddress());
    }
    
    @Test
    public void decoding() throws Exception {
        Address a = Address.fromBase58(testParams, "ubwJhHMSVPVCHr3PNPgieNYpWvuWG5XvcQ");
        assertEquals("ba74df8342800b07e231cc33d956aa91fde30675", Utils.HEX.encode(a.getHash160()));

        Address b = Address.fromBase58(mainParams, "UNAiJ2pgTzRR4xvQYKzanEtscc3fybcJgi");
        assertEquals("0205d52c7ed8131d126cf4335bc39abf631a3762", Utils.HEX.encode(b.getHash160()));
    }
    
    @Test
    public void errorPaths() {
        // Check what happens if we try and decode garbage.
        try {
            Address.fromBase58(testParams, "this is not a valid address!");
            fail();
        } catch (WrongNetworkException e) {
            fail();
        } catch (AddressFormatException e) {
            // Success.
        }

        // Check the empty case.
        try {
            Address.fromBase58(testParams, "");
            fail();
        } catch (WrongNetworkException e) {
            fail();
        } catch (AddressFormatException e) {
            // Success.
        }

        // Check the case of a mismatched network.
        try {
            Address.fromBase58(testParams, "UNAiJ2pgTzRR4xvQYKzanEtscc3fybcJgi");
            fail();
        } catch (WrongNetworkException e) {
            // Success.
            assertEquals(e.verCode, MainNetParams.get().getAddressHeader());
            assertTrue(Arrays.equals(e.acceptableVersions, TestNet3Params.get().getAcceptableAddressCodes()));
        } catch (AddressFormatException e) {
            fail();
        }
    }

    @Test
    public void getNetwork() throws Exception {
        NetworkParameters params = Address.getParametersFromAddress("UNAiJ2pgTzRR4xvQYKzanEtscc3fybcJgi");
        assertEquals(MainNetParams.get().getId(), params.getId());
        params = Address.getParametersFromAddress("ubwJhHMSVPVCHr3PNPgieNYpWvuWG5XvcQ");
        assertEquals(TestNet3Params.get().getId(), params.getId());
    }

    @Test
    public void getAltNetwork() throws Exception {
        // An alternative network
        class AltNetwork extends MainNetParams {
            AltNetwork() {
                super();
                id = "alt.network";
                addressHeader = 48;
                p2shHeader = 5;
                acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
            }
        }
        AltNetwork altNetwork = new AltNetwork();
        // Add new network params
        Networks.register(altNetwork);
        // Check if can parse address
        NetworkParameters params = Address.getParametersFromAddress("LLxSnHLN2CYyzB5eWTR9K9rS9uWtbTQFb6");
        assertEquals(altNetwork.getId(), params.getId());
        // Check if main network works as before
        params = Address.getParametersFromAddress("UNAiJ2pgTzRR4xvQYKzanEtscc3fybcJgi");
        assertEquals(MainNetParams.get().getId(), params.getId());
        // Unregister network
        Networks.unregister(altNetwork);
        try {
            Address.getParametersFromAddress("LLxSnHLN2CYyzB5eWTR9K9rS9uWtbTQFb6");
            fail();
        } catch (AddressFormatException e) { }
    }
    
    @Test
    public void p2shAddress() throws Exception {
        // Test that we can construct P2SH addresses
//        Address mainNetP2SHAddress = Address.fromBase58(MainNetParams.get(), "35b9vsyH1KoFT5a5KtrKusaCcPLkiSo1tU");
//        assertEquals(mainNetP2SHAddress.version, MainNetParams.get().p2shHeader);
//        assertTrue(mainNetP2SHAddress.isP2SHAddress());
        Address testNetP2SHAddress = Address.fromBase58(TestNet3Params.get(), "saxvYV4oy1eu9ZDotsh94F9xc6BqsqgcvT");
        assertEquals(testNetP2SHAddress.version, TestNet3Params.get().p2shHeader);
        assertTrue(testNetP2SHAddress.isP2SHAddress());

        // Test that we can determine what network a P2SH address belongs to
//        NetworkParameters mainNetParams = Address.getParametersFromAddress("35b9vsyH1KoFT5a5KtrKusaCcPLkiSo1tU");
//        assertEquals(MainNetParams.get().getId(), mainNetParams.getId());
        NetworkParameters testNetParams = Address.getParametersFromAddress("saxvYV4oy1eu9ZDotsh94F9xc6BqsqgcvT"); // NOTE: This test requires to remove MainNetParams from Networks as
        assertEquals(TestNet3Params.get().getId(), testNetParams.getId());                                        // MainNet is not implement yet.

        // Test that we can convert them from hashes
//        byte[] hex = HEX.decode("2ac4b0b501117cc8119c5797b519538d4942e90e");
//        Address a = Address.fromP2SHHash(mainParams, hex);
//        assertEquals("35b9vsyH1KoFT5a5KtrKusaCcPLkiSo1tU", a.toString());
        Address b = Address.fromP2SHHash(testParams, HEX.decode("b75c7f3c962d5124ed54c0d8014cb26976c070cd"));
        assertEquals("saxvYV4oy1eu9ZDotsh94F9xc6BqsqgcvT", b.toString());
//        Address c = Address.fromP2SHScript(mainParams, ScriptBuilder.createP2SHOutputScript(hex));
//        assertEquals("35b9vsyH1KoFT5a5KtrKusaCcPLkiSo1tU", c.toString());
    }

    @Test
    public void cloning() throws Exception {
        Address a = new Address(testParams, HEX.decode("b75c7f3c962d5124ed54c0d8014cb26976c070cd"));
        Address b = a.clone();

        assertEquals(a, b);
        assertNotSame(a, b);
    }

    @Test
    public void roundtripBase58() throws Exception {
        String base58 = "uVvfixZ1rAXx7ktFCNuRgHrU5yWoDNurEy";
        assertEquals(base58, Address.fromBase58(null, base58).toBase58());
    }

    @Test
    public void comparisonCloneEqualTo() throws Exception {
//        Address a = Address.fromBase58(mainParams, "1Dorian4RoXcnBv9hnQ4Y2C1an6NJ4UrjX");
//        Address b = a.clone();

//        int result = a.compareTo(b);
//        assertEquals(0, result);
    }

    @Test
    public void comparisonEqualTo() throws Exception {
//        Address a = Address.fromBase58(mainParams, "1Dorian4RoXcnBv9hnQ4Y2C1an6NJ4UrjX");
//        Address b = a.clone();
//
//        int result = a.compareTo(b);
//        assertEquals(0, result);
    }

    @Test
    public void comparisonLessThan() throws Exception {
//        Address a = Address.fromBase58(mainParams, "1Dorian4RoXcnBv9hnQ4Y2C1an6NJ4UrjX");
//        Address b = Address.fromBase58(mainParams, "1EXoDusjGwvnjZUyKkxZ4UHEf77z6A5S4P");
//
//        int result = a.compareTo(b);
//        assertTrue(result < 0);
    }

    @Test
    public void comparisonGreaterThan() throws Exception {
//        Address a = Address.fromBase58(mainParams, "1EXoDusjGwvnjZUyKkxZ4UHEf77z6A5S4P");
//        Address b = Address.fromBase58(mainParams, "1Dorian4RoXcnBv9hnQ4Y2C1an6NJ4UrjX");
//
//        int result = a.compareTo(b);
//        assertTrue(result > 0);
    }

    @Test
    public void comparisonBytesVsString() throws Exception {
        // TODO: To properly test this we need a much larger data set
//        Address a = Address.fromBase58(mainParams, "1Dorian4RoXcnBv9hnQ4Y2C1an6NJ4UrjX");
//        Address b = Address.fromBase58(mainParams, "1EXoDusjGwvnjZUyKkxZ4UHEf77z6A5S4P");
//
//        int resultBytes = a.compareTo(b);
//        int resultsString = a.toString().compareTo(b.toString());
//        assertTrue( resultBytes < 0 );
//        assertTrue( resultsString < 0 );
    }
}
