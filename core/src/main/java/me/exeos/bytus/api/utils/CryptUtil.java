package me.exeos.bytus.api.utils;

import me.exeos.asmplus.utils.RandomUtil;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;

public class CryptUtil {

    public static KeyPair generateKeyPair() throws Exception {
        // Generate key pair using RSA algorithm
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048); // You can adjust the key size
        return keyPairGenerator.generateKeyPair();
    }


    public static byte[] encryptData(byte[] data, byte[] key) throws Exception {
        // Generate a secret key from the password
        SecretKey secretKey = new SecretKeySpec(key, "AES");

        // Create Cipher object
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        // Encrypt the data
        return cipher.doFinal(data);
    }

    public static byte[] decryptData(byte[] encryptedData, byte[] key) throws Exception {
        // Generate a secret key from the password
        SecretKey secretKey = new SecretKeySpec(key, "AES");

        // Create Cipher object
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);

        // Decrypt the data
        return cipher.doFinal(encryptedData);
    }

    public static byte[] signData(byte[] data, PrivateKey privateKey) throws Exception {
        // Signature object
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign(); // Returns the signature
    }

    public static boolean verifySignature(byte[] data, byte[] signature, byte[] publicKeyBytes) throws Exception {
        // Convert public key bytes to PublicKey object
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(keySpec);

        // Signature object for verification
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature); // Returns true if the signature is verified
    }

    public static byte[] genKey(int length) {
        return RandomUtil.getBytes(length);
    }


    public static String toBase26Hash(String toHash) throws NumberFormatException {
        int number = toHash.hashCode();
        StringBuilder converted = new StringBuilder();
        if (number < 0) {
            number = -number;
            converted.append("-");
        }
        number = Math.abs(number);

        do {
            int remainder = number % 26;
            converted.insert(0, (char) (remainder + 'a'));
            number = (number - remainder) / 26;
        } while (number > 0);
        return converted.toString();
    }
}
