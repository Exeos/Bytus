package com.bytus.test;

import com.sun.xml.internal.ws.api.ResourceLoader;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public class CLoader extends ClassLoader {

//    public void test(byte[] encData) {
//        try {
//            byte[] decKey = new byte[32];
//            byte[] signature = new byte[256];
//            byte[] integrityKey = new byte[294];
//
//            // decrypt key
//            for (int i = 0; i < decKey.length; i++) {
//                decKey[i] = encData[i];
//            }
//            // signature
//            for (int i = 0; i < signature.length; i++) {
//                signature[i] = encData[decKey.length + i];
//            }
//            // integrity key
//            for (int i = 0; i < integrityKey.length; i++) {
//                integrityKey[i] = encData[decKey.length + signature.length + i];
//            }
//
//            int prefixLength = decKey.length + signature.length + integrityKey.length;
//
//            byte[] trimmedEnc = new byte[encData.length - prefixLength];
//            System.arraycopy(encData, prefixLength, trimmedEnc, 0, encData.length - prefixLength);
//
//            byte[] decData = decryptData(trimmedEnc, decKey);
//            if (verifySignature(decData, signature, integrityKey)) {
//                System.out.println("SUCCESS");
//            } else {
//                System.out.println(decKey.length);
//                System.out.println(signature.length);
//                System.out.println(integrityKey.length);
//                System.out.println(trimmedEnc.length);
//
//                System.out.println();
//
//                System.out.println(Arrays.hashCode(decKey));
//                System.out.println(Arrays.hashCode(signature));
//                System.out.println(Arrays.hashCode(integrityKey));
//                System.out.println(Arrays.hashCode(trimmedEnc));
//                System.out.println("not valid");
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] encData = loadResourceAsByteArray(name);
        if (encData != null) {
            try {
                byte[] decKey = new byte[32];
                byte[] signature = new byte[256];
                byte[] integrityKey = new byte[294];

                // decrypt key
                for (int i = 0; i < decKey.length; i++) {
                    decKey[i] = encData[i];
                }
                // signature
                for (int i = 0; i < signature.length; i++) {
                    signature[i] = encData[decKey.length + i];
                }
                // integrity key
                for (int i = 0; i < integrityKey.length; i++) {
                    integrityKey[i] = encData[decKey.length + signature.length + i];
                }

                int prefixLength = decKey.length + signature.length + integrityKey.length;

                byte[] trimmedEnc = new byte[encData.length - prefixLength];
                System.arraycopy(encData, prefixLength, trimmedEnc, 0, encData.length - prefixLength);

                byte[] decData = decryptData(trimmedEnc, decKey);
                if (verifySignature(decData, signature, integrityKey)) {
                    return defineClass(name, decData, 0, decData.length);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("data is null");
        }

        return null;
    }

    private byte[] loadResourceAsByteArray(String resourcePath) {
        String resourceName = "/" + resourcePath.replace(".", "/") + ".bytus";
        try (InputStream in = getClass().getResourceAsStream(resourceName)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int data;
            while ((data = in.read()) != -1) {
                buffer.write(data);
            }
            System.out.println("Encrypted resource loaded successfully.");
            return buffer.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] decryptData(byte[] encryptedData, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));

        return cipher.doFinal(encryptedData);
    }

    private boolean verifySignature(byte[] data, byte[] signature, byte[] publicKeyBytes) throws Exception {
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(keySpec);

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }
}
